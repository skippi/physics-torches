package com.binhjcao.physicstorches.entity;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class RigidBodyCollisionModel {
  public record SurfaceHit(Vec3 worldPoint, Vec3 worldNormal) {}

  private final double halfSize;

  public RigidBodyCollisionModel(double halfSize) {
    this.halfSize = halfSize;
  }

  public static RigidBodyCollisionModel cube(double halfSize) {
    return new RigidBodyCollisionModel(halfSize);
  }

  public double halfSize() {
    return halfSize;
  }

  public Optional<SurfaceHit> raycast(
      Quaternionf orientation,
      Vec3 center,
      Vec3 rayOrigin,
      Vec3 rayDirection,
      double maxReach) {
    if (rayDirection.lengthSqr() < 1.0E-8D) {
      return Optional.empty();
    }

    Vec3 localOrigin = toLocalPoint(rayOrigin, center, orientation);
    Vec3 localDirection = toLocalDirection(rayDirection, orientation);
    Optional<Vec3> localHit = intersectRayCube(localOrigin, localDirection, halfSize);
    if (localHit.isEmpty()) {
      return Optional.empty();
    }

    Vec3 worldPoint = toWorldPoint(localHit.get(), center, orientation);
    if (worldPoint.subtract(rayOrigin).length() > maxReach) {
      return Optional.empty();
    }

    Vec3 localNormal = localFaceNormal(localHit.get());
    Vec3 worldNormal = toWorldDirection(localNormal, orientation);
    return Optional.of(new SurfaceHit(worldPoint, worldNormal));
  }

  public Vec3 localToWorld(Vec3 localPoint, Vec3 center, Quaternionf orientation) {
    return toWorldPoint(localPoint, center, orientation);
  }

  private static Vec3 localFaceNormal(Vec3 localHit) {
    double absX = Math.abs(localHit.x);
    double absY = Math.abs(localHit.y);
    double absZ = Math.abs(localHit.z);

    if (absX >= absY && absX >= absZ) {
      return new Vec3(Math.signum(localHit.x), 0.0D, 0.0D);
    }
    if (absY >= absX && absY >= absZ) {
      return new Vec3(0.0D, Math.signum(localHit.y), 0.0D);
    }
    return new Vec3(0.0D, 0.0D, Math.signum(localHit.z));
  }

  private static Vec3 toLocalPoint(Vec3 worldPoint, Vec3 center, Quaternionf orientation) {
    Vector3f local =
        new Vector3f(
            (float) (worldPoint.x - center.x),
            (float) (worldPoint.y - center.y),
            (float) (worldPoint.z - center.z));
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  private static Vec3 toLocalDirection(Vec3 worldDirection, Quaternionf orientation) {
    Vector3f local =
        new Vector3f(
            (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  private static Vec3 toWorldPoint(Vec3 localPoint, Vec3 center, Quaternionf orientation) {
    Vector3f world =
        new Vector3f((float) localPoint.x, (float) localPoint.y, (float) localPoint.z);
    orientation.transform(world);
    return new Vec3(world.x + center.x, world.y + center.y, world.z + center.z);
  }

  private static Vec3 toWorldDirection(Vec3 localDirection, Quaternionf orientation) {
    Vector3f world =
        new Vector3f(
            (float) localDirection.x, (float) localDirection.y, (float) localDirection.z);
    orientation.transform(world);
    return new Vec3(world.x, world.y, world.z);
  }

  private static Optional<Vec3> intersectRayCube(Vec3 origin, Vec3 direction, double halfSize) {
    double tMin = Double.NEGATIVE_INFINITY;
    double tMax = Double.POSITIVE_INFINITY;
    double[] originComponents = {origin.x, origin.y, origin.z};
    double[] directionComponents = {direction.x, direction.y, direction.z};

    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(directionComponents[axis]) < 1.0E-8D) {
        if (originComponents[axis] < -halfSize || originComponents[axis] > halfSize) {
          return Optional.empty();
        }
        continue;
      }

      double tNear = (-halfSize - originComponents[axis]) / directionComponents[axis];
      double tFar = (halfSize - originComponents[axis]) / directionComponents[axis];
      if (tNear > tFar) {
        double swap = tNear;
        tNear = tFar;
        tFar = swap;
      }

      tMin = Math.max(tMin, tNear);
      tMax = Math.min(tMax, tFar);
      if (tMin > tMax) {
        return Optional.empty();
      }
    }

    double t = tMin >= 0.0D ? tMin : tMax;
    if (t < 0.0D) {
      return Optional.empty();
    }

    return Optional.of(origin.add(direction.scale(t)));
  }
}
