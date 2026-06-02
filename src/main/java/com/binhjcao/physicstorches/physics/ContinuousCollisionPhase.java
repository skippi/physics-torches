package com.binhjcao.physicstorches.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import com.binhjcao.physicstorches.BlockCollider;
import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.OrientedTransform;

public class ContinuousCollisionPhase {
  private static final double SWEEP_EPSILON = 1.0E-5D;
  private static final double CONTACT_EPSILON = 1.0E-4D;
  private static final double BLOCK_SWEEP_INFLATE = CONTACT_EPSILON;

  public static void solve(
      List<RigidBody> bodies, List<ContactConstraint> constraints, double dt) {
    if (bodies.isEmpty() || dt <= 1.0E-8D) {
      return;
    }

    var ccdConstraints = new ArrayList<ContactConstraint>();
    for (RigidBody body : bodies) {
      if (body.freeze() || body.isSleeping() || body.linearLock()) {
        continue;
      }

      Optional<SweepHit> hit = findEarliestFrozenHit(body, bodies, constraints);
      if (hit.isEmpty()) {
        continue;
      }

      SweepHit sweepHit = hit.get();
      RigidBody block = sweepHit.block();
      applySweepHit(body, sweepHit);
      removeLinearVelocityIntoHit(body, sweepHit.normal());

      NarrowPhase.findContactCandidate(body, block)
          .or(() -> findContactAfterSurfaceNudge(body, block, sweepHit.normal()))
          .map(ContactManifoldPhase::generateContactManifold)
          .map(manifold -> ContactConstraintPhase.setupContactConstraint(manifold, dt))
          .ifPresent(ccdConstraints::add);
    }

    if (ccdConstraints.isEmpty()) {
      return;
    }

    VelocitySolverPhase.solve(ccdConstraints);
    constraints.addAll(ccdConstraints);
  }

  private static Optional<NarrowPhaseContact> findContactAfterSurfaceNudge(
      RigidBody body, RigidBody block, Vec3 normal) {
    body.position(body.position().subtract(normal.scale(Physics.PENETRATION_SLOP)));
    return NarrowPhase.findContactCandidate(body, block);
  }

  private static void applySweepHit(RigidBody body, SweepHit hit) {
    Vec3 previousPosition = body.prevPosition();
    Vec3 currentPosition = body.position();
    Quaternionf previousOrientation = body.prevOrientation();
    Quaternionf currentOrientation = body.orientation();
    Quaternionf orientationAtHit =
        new Quaternionf(previousOrientation).slerp(currentOrientation, (float) hit.time());

    Vec3 sampleStart =
        OrientedTransform.toWorldPoint(hit.localSample(), previousPosition, previousOrientation);
    Vec3 sampleEnd =
        OrientedTransform.toWorldPoint(hit.localSample(), currentPosition, currentOrientation);
    Vec3 hitPoint = sampleStart.add(sampleEnd.subtract(sampleStart).scale(hit.time()));
    if (hit.startedInside()) {
      hitPoint = sampleStart.add(hit.normal().scale(hit.insidePushDistance() + CONTACT_EPSILON));
    } else {
      hitPoint = hitPoint.add(hit.normal().scale(CONTACT_EPSILON));
    }

    Vec3 sampleOffset =
        OrientedTransform.toWorldPoint(hit.localSample(), Vec3.ZERO, orientationAtHit);
    body.position(hitPoint.subtract(sampleOffset));
  }

  private static Optional<SweepHit> findEarliestFrozenHit(
      RigidBody body, List<RigidBody> bodies, List<ContactConstraint> constraints) {
    Vec3 previousPosition = body.prevPosition();
    Vec3 currentPosition = body.position();
    if (previousPosition.distanceToSqr(currentPosition) <= 1.0E-12D) {
      return Optional.empty();
    }

    Vec3[] localSamples = localSweepSamples(body.collider());
    SweepHit best = null;
    for (RigidBody candidate : bodies) {
      if (!candidate.freeze()
          || candidate == body
          || !(candidate.collider() instanceof BlockCollider blockCollider)
          || hasConstraint(constraints, body, candidate)) {
        continue;
      }

      AABB bounds = blockCollider.bounds().inflate(BLOCK_SWEEP_INFLATE);
      for (Vec3 localSample : localSamples) {
        Vec3 sampleStart =
            OrientedTransform.toWorldPoint(
                localSample, previousPosition, body.prevOrientation());
        Vec3 sampleEnd =
            OrientedTransform.toWorldPoint(localSample, currentPosition, body.orientation());
        Optional<SweepHit> sampleHit =
            sweepSampleAgainstAabb(
                candidate, localSample, sampleStart, sampleEnd, bounds);
        if (sampleHit.isEmpty()) {
          continue;
        }

        SweepHit hit = sampleHit.get();
        if (best == null || hit.time() < best.time()) {
          best = hit;
        }
      }
    }

    return Optional.ofNullable(best);
  }

  private static Optional<SweepHit> sweepSampleAgainstAabb(
      RigidBody block,
      Vec3 localSample,
      Vec3 sampleStart,
      Vec3 sampleEnd,
      AABB bounds) {
    if (bounds.contains(sampleStart)) {
      Vec3 normal = penetrationNormal(sampleStart, bounds);
      if (!isValidBlockSurfaceHit(sampleStart, bounds, normal)) {
        return Optional.empty();
      }
      double pushDistance = exitDistance(sampleStart, bounds, normal);
      return Optional.of(
          new SweepHit(block, 0.0D, normal, localSample, true, pushDistance));
    }

    Optional<SegmentHit> segmentHit = earliestBlockHit(sampleStart, sampleEnd, bounds);
    if (segmentHit.isEmpty()) {
      return Optional.empty();
    }

    Vec3 normal = segmentHit.get().outwardNormal().scale(-1.0D);
    if (!isValidBlockSurfaceHit(sampleStart, bounds, normal)) {
      return Optional.empty();
    }

    return Optional.of(
        new SweepHit(
            block,
            segmentHit.get().time(),
            normal,
            localSample,
            false,
            0.0D));
  }

  private static Optional<SegmentHit> earliestBlockHit(Vec3 sampleStart, Vec3 sampleEnd, AABB bounds) {
    SegmentHit best = null;
    Optional<SegmentHit> volumeHit = sweepPointAgainstAabb(sampleStart, sampleEnd, bounds);
    if (volumeHit.isPresent()) {
      best = volumeHit.get();
    }

    Optional<SegmentHit> topHit = sweepSampleAgainstBlockTop(sampleStart, sampleEnd, bounds);
    if (topHit.isPresent() && (best == null || topHit.get().time() < best.time())) {
      best = topHit.get();
    }

    return Optional.ofNullable(best);
  }

  private static Optional<SegmentHit> sweepSampleAgainstBlockTop(
      Vec3 start, Vec3 end, AABB bounds) {
    double deltaY = end.y - start.y;
    if (Math.abs(deltaY) <= SWEEP_EPSILON) {
      return Optional.empty();
    }

    double planeY = bounds.maxY;
    double time = (planeY - start.y) / deltaY;
    if (time < 0.0D || time > 1.0D) {
      return Optional.empty();
    }

    Vec3 hit = start.add(end.subtract(start).scale(time));
    if (hit.x < bounds.minX - SWEEP_EPSILON
        || hit.x > bounds.maxX + SWEEP_EPSILON
        || hit.z < bounds.minZ - SWEEP_EPSILON
        || hit.z > bounds.maxZ + SWEEP_EPSILON) {
      return Optional.empty();
    }

    Vec3 outwardNormal = deltaY < 0.0D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, -1.0D, 0.0D);
    return Optional.of(new SegmentHit(time, outwardNormal));
  }

  private static boolean isValidBlockSurfaceHit(Vec3 sampleStart, AABB bounds, Vec3 normal) {
    if (sampleStart.y >= bounds.maxY - Physics.SURFACE_TOLERANCE) {
      return Math.abs(normal.y) > 0.9D;
    }
    return true;
  }

  private static Vec3[] localSweepSamples(Collider collider) {
    Vec3 half = collider.halfExtents();
    double hx = half.x;
    double hy = half.y;
    double hz = half.z;
    return new Vec3[] {
      new Vec3(0.0D, 0.0D, 0.0D),
      new Vec3(hx, hy, hz),
      new Vec3(hx, hy, -hz),
      new Vec3(hx, -hy, hz),
      new Vec3(hx, -hy, -hz),
      new Vec3(-hx, hy, hz),
      new Vec3(-hx, hy, -hz),
      new Vec3(-hx, -hy, hz),
      new Vec3(-hx, -hy, -hz),
      new Vec3(0.0D, -hy, 0.0D),
      new Vec3(hx, -hy, 0.0D),
      new Vec3(-hx, -hy, 0.0D),
      new Vec3(0.0D, -hy, hz),
      new Vec3(0.0D, -hy, -hz)
    };
  }

  private static void removeLinearVelocityIntoHit(RigidBody body, Vec3 normal) {
    double inwardSpeed = body.linearVelocity().dot(normal);
    if (inwardSpeed <= 0.0D) {
      return;
    }
    body.linearVelocity(body.linearVelocity().subtract(normal.scale(inwardSpeed)));
  }

  private static Optional<SegmentHit> sweepPointAgainstAabb(Vec3 start, Vec3 end, AABB bounds) {
    Vec3 delta = end.subtract(start);
    double entryTime = 0.0D;
    double exitTime = 1.0D;
    Vec3 entryNormal = Vec3.ZERO;

    AxisHit x = axisHit(start.x, delta.x, bounds.minX, bounds.maxX, new Vec3(-1.0D, 0.0D, 0.0D));
    if (x == null) {
      return Optional.empty();
    }
    if (x.entryTime() > entryTime) {
      entryTime = x.entryTime();
      entryNormal = x.entryNormal();
    }
    exitTime = Math.min(exitTime, x.exitTime());

    AxisHit y = axisHit(start.y, delta.y, bounds.minY, bounds.maxY, new Vec3(0.0D, -1.0D, 0.0D));
    if (y == null) {
      return Optional.empty();
    }
    if (y.entryTime() > entryTime) {
      entryTime = y.entryTime();
      entryNormal = y.entryNormal();
    }
    exitTime = Math.min(exitTime, y.exitTime());

    AxisHit z = axisHit(start.z, delta.z, bounds.minZ, bounds.maxZ, new Vec3(0.0D, 0.0D, -1.0D));
    if (z == null) {
      return Optional.empty();
    }
    if (z.entryTime() > entryTime) {
      entryTime = z.entryTime();
      entryNormal = z.entryNormal();
    }
    exitTime = Math.min(exitTime, z.exitTime());

    if (entryTime > exitTime || entryTime < 0.0D || entryTime > 1.0D) {
      return Optional.empty();
    }
    return Optional.of(new SegmentHit(Math.clamp(entryTime, 0.0D, 1.0D), entryNormal));
  }

  private static Vec3 penetrationNormal(Vec3 point, AABB bounds) {
    double toMinX = point.x - bounds.minX;
    double toMaxX = bounds.maxX - point.x;
    double toMinY = point.y - bounds.minY;
    double toMaxY = bounds.maxY - point.y;
    double toMinZ = point.z - bounds.minZ;
    double toMaxZ = bounds.maxZ - point.z;

    double minDepth = toMinX;
    Vec3 normal = new Vec3(-1.0D, 0.0D, 0.0D);
    if (toMaxX < minDepth) {
      minDepth = toMaxX;
      normal = new Vec3(1.0D, 0.0D, 0.0D);
    }
    if (toMinY < minDepth) {
      minDepth = toMinY;
      normal = new Vec3(0.0D, -1.0D, 0.0D);
    }
    if (toMaxY < minDepth) {
      minDepth = toMaxY;
      normal = new Vec3(0.0D, 1.0D, 0.0D);
    }
    if (toMinZ < minDepth) {
      minDepth = toMinZ;
      normal = new Vec3(0.0D, 0.0D, -1.0D);
    }
    if (toMaxZ < minDepth) {
      normal = new Vec3(0.0D, 0.0D, 1.0D);
    }
    return normal;
  }

  private static double exitDistance(Vec3 point, AABB bounds, Vec3 normal) {
    if (normal.y > 0.5D) {
      return bounds.maxY - point.y;
    }
    if (normal.y < -0.5D) {
      return point.y - bounds.minY;
    }
    if (normal.x > 0.5D) {
      return bounds.maxX - point.x;
    }
    if (normal.x < -0.5D) {
      return point.x - bounds.minX;
    }
    if (normal.z > 0.5D) {
      return bounds.maxZ - point.z;
    }
    if (normal.z < -0.5D) {
      return point.z - bounds.minZ;
    }
    return 0.0D;
  }

  private static AxisHit axisHit(
      double start, double delta, double min, double max, Vec3 minNormal) {
    if (Math.abs(delta) <= SWEEP_EPSILON) {
      return start >= min && start <= max ? new AxisHit(0.0D, 1.0D, Vec3.ZERO) : null;
    }

    double entry = (min - start) / delta;
    double exit = (max - start) / delta;
    Vec3 entryNormal = minNormal;
    if (entry > exit) {
      double tmp = entry;
      entry = exit;
      exit = tmp;
      entryNormal = minNormal.scale(-1.0D);
    }

    return new AxisHit(entry, exit, entryNormal);
  }

  private static boolean hasConstraint(
      List<ContactConstraint> constraints, RigidBody a, RigidBody b) {
    for (ContactConstraint constraint : constraints) {
      if ((constraint.a() == a && constraint.b() == b)
          || (constraint.a() == b && constraint.b() == a)) {
        return true;
      }
    }
    return false;
  }

  private record SweepHit(
      RigidBody block,
      double time,
      Vec3 normal,
      Vec3 localSample,
      boolean startedInside,
      double insidePushDistance) {}

  private record SegmentHit(double time, Vec3 outwardNormal) {}

  private record AxisHit(double entryTime, double exitTime, Vec3 entryNormal) {}

  private ContinuousCollisionPhase() {}
}
