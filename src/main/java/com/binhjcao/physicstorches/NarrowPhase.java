package com.binhjcao.physicstorches;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class NarrowPhase {
  private record SatResult(Vec3 normal, double depth) {}

  public static Optional<NarrowPhaseContact> findContactCandidate(RigidBody a, RigidBody b) {
    Vec3 centerA = a.position();
    Vec3 centerB = b.position();
    Quaternionf orientA = a.orientation();
    Quaternionf orientB = b.orientation();
    Collider colliderA = a.collider();
    Collider colliderB = b.collider();

    SatResult sat =
        satObbVsObb(centerA, orientA, colliderA, centerB, orientB, colliderB);
    if (sat == null) {
      return Optional.empty();
    }

    Vec3 point =
        findContactPoint(
            centerA, orientA, colliderA, centerB, orientB, colliderB, sat.normal());
    return Optional.of(
        new NarrowPhaseContact(a, b, point, sat.normal(), sat.depth()));
  }

  private static SatResult satObbVsObb(
      Vec3 centerA,
      Quaternionf orientA,
      Collider colliderA,
      Vec3 centerB,
      Quaternionf orientB,
      Collider colliderB) {
    Vec3 halfA = colliderA.halfExtents();
    Vec3 halfB = colliderB.halfExtents();
    Vec3 u0a = worldAxis(orientA, 0);
    Vec3 u1a = worldAxis(orientA, 1);
    Vec3 u2a = worldAxis(orientA, 2);
    Vec3 u0b = worldAxis(orientB, 0);
    Vec3 u1b = worldAxis(orientB, 1);
    Vec3 u2b = worldAxis(orientB, 2);
    Vec3[] axes = {
      u0a, u1a, u2a,
      u0b, u1b, u2b,
      u0a.cross(u0b), u0a.cross(u1b), u0a.cross(u2b),
      u1a.cross(u0b), u1a.cross(u1b), u1a.cross(u2b),
      u2a.cross(u0b), u2a.cross(u1b), u2a.cross(u2b)
    };

    double minDepth = Double.MAX_VALUE;
    Vec3 bestNormal = null;
    for (Vec3 axis : axes) {
      double lenSq = axis.lengthSqr();
      if (lenSq < 1.0E-8D) {
        continue;
      }
      Vec3 normalized = axis.scale(1.0D / Math.sqrt(lenSq));
      double radiusA =
          Math.abs(normalized.dot(u0a)) * halfA.x
              + Math.abs(normalized.dot(u1a)) * halfA.y
              + Math.abs(normalized.dot(u2a)) * halfA.z;
      double radiusB =
          Math.abs(normalized.dot(u0b)) * halfB.x
              + Math.abs(normalized.dot(u1b)) * halfB.y
              + Math.abs(normalized.dot(u2b)) * halfB.z;
      double projA = normalized.dot(centerA);
      double projB = normalized.dot(centerB);
      double depth = radiusA + radiusB - Math.abs(projA - projB);
      if (depth <= 0.0D) {
        return null;
      }
      if (depth < minDepth) {
        minDepth = depth;
        bestNormal = normalized.scale(projA >= projB ? -1.0D : 1.0D);
      }
    }
    return bestNormal == null ? null : new SatResult(bestNormal, minDepth);
  }

  private static Vec3 findContactPoint(
      Vec3 centerA,
      Quaternionf orientA,
      Collider colliderA,
      Vec3 centerB,
      Quaternionf orientB,
      Collider colliderB,
      Vec3 normal) {
    List<Vec3> penetrating = new ArrayList<>();
    for (Vec3 corner : colliderA.worldCorners(centerA, orientA)) {
      if (isPointInsideObb(corner, centerB, orientB, colliderB)) {
        penetrating.add(corner);
      }
    }
    for (Vec3 corner : colliderB.worldCorners(centerB, orientB)) {
      if (isPointInsideObb(corner, centerA, orientA, colliderA)) {
        penetrating.add(corner);
      }
    }

    if (!penetrating.isEmpty()) {
      Vec3 best = penetrating.getFirst();
      double bestVn = best.subtract(centerA).dot(normal);
      for (Vec3 contact : penetrating) {
        double vn = contact.subtract(centerA).dot(normal);
        if (vn > bestVn) {
          bestVn = vn;
          best = contact;
        }
      }
      return best;
    }

    Vec3[] cornersA = colliderA.worldCorners(centerA, orientA);
    Vec3 closest = cornersA[0];
    for (Vec3 corner : cornersA) {
      if (corner.distanceToSqr(centerB) < closest.distanceToSqr(centerB)) {
        closest = corner;
      }
    }
    return closest;
  }

  private static boolean isPointInsideObb(
      Vec3 point, Vec3 center, Quaternionf orientation, Collider collider) {
    Vec3 local = OrientedTransform.toLocalPoint(point, center, orientation);
    Vec3 half = collider.halfExtents();
    return Math.abs(local.x) <= half.x + 1.0E-4D
        && Math.abs(local.y) <= half.y + 1.0E-4D
        && Math.abs(local.z) <= half.z + 1.0E-4D;
  }

  private static Vec3 worldAxis(Quaternionf orientation, int axis) {
    return switch (axis) {
      case 0 -> OrientedTransform.toWorldDirection(new Vec3(1, 0, 0), orientation);
      case 1 -> OrientedTransform.toWorldDirection(new Vec3(0, 1, 0), orientation);
      default -> OrientedTransform.toWorldDirection(new Vec3(0, 0, 1), orientation);
    };
  }

  private NarrowPhase() {}
}
