package com.binhjcao.physicstorches;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class BoxRayIntersection {
  private static final double FACE_EPSILON = 1.0E-4D;
  private static final double RAY_EPSILON = 1.0E-8D;

  record LocalHit(Vec3 point, Vec3 outwardNormal) {}

  private BoxRayIntersection() {}

  static Optional<LocalHit> intersect(
      Vec3 origin,
      Vec3 direction,
      double halfX,
      double halfY,
      double halfZ,
      double surfaceTolerance) {
    if (direction.lengthSqr() < RAY_EPSILON) {
      return Optional.empty();
    }

    double[] halfExtents = {
      halfX + surfaceTolerance, halfY + surfaceTolerance, halfZ + surfaceTolerance
    };
    double tMin = Double.NEGATIVE_INFINITY;
    double tMax = Double.POSITIVE_INFINITY;
    double[] originComponents = {origin.x, origin.y, origin.z};
    double[] directionComponents = {direction.x, direction.y, direction.z};
    double[] bounds = {halfX, halfY, halfZ};

    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(directionComponents[axis]) < RAY_EPSILON) {
        if (originComponents[axis] < -halfExtents[axis]
            || originComponents[axis] > halfExtents[axis]) {
          return Optional.empty();
        }
        continue;
      }

      double tNear =
          (-halfExtents[axis] - originComponents[axis]) / directionComponents[axis];
      double tFar =
          (halfExtents[axis] - originComponents[axis]) / directionComponents[axis];
      if (tNear > tFar) {
        double swap = tNear;
        tNear = tFar;
        tFar = swap;
      }

      if (tNear > tMin) {
        tMin = tNear;
      }
      tMax = Math.min(tMax, tFar);
      if (tMin > tMax) {
        return Optional.empty();
      }
    }

    double t = tMin >= 0.0D ? tMin : tMax;
    if (t < 0.0D) {
      return Optional.empty();
    }

    Vec3 point = clampToBoxSurface(origin.add(direction.scale(t)), bounds);
    Vec3 outwardNormal = entryFaceNormal(point, direction, bounds);
    return Optional.of(new LocalHit(point, outwardNormal));
  }

  private static Vec3 clampToBoxSurface(Vec3 point, double[] halfExtents) {
    double x = Math.clamp(point.x, -halfExtents[0], halfExtents[0]);
    double y = Math.clamp(point.y, -halfExtents[1], halfExtents[1]);
    double z = Math.clamp(point.z, -halfExtents[2], halfExtents[2]);
    if (Math.abs(x) < halfExtents[0] - FACE_EPSILON
        && Math.abs(y) < halfExtents[1] - FACE_EPSILON
        && Math.abs(z) < halfExtents[2] - FACE_EPSILON) {
      double absX = Math.abs(x);
      double absY = Math.abs(y);
      double absZ = Math.abs(z);
      if (absX >= absY && absX >= absZ) {
        x = Math.copySign(halfExtents[0], x != 0.0D ? x : 1.0D);
      } else if (absY >= absZ) {
        y = Math.copySign(halfExtents[1], y != 0.0D ? y : 1.0D);
      } else {
        z = Math.copySign(halfExtents[2], z != 0.0D ? z : 1.0D);
      }
    }

    return new Vec3(x, y, z);
  }

  private static Vec3 entryFaceNormal(Vec3 hit, Vec3 direction, double[] halfExtents) {
    int bestAxis = -1;
    double bestAlignment = 0.0D;

    for (int axis = 0; axis < 3; axis++) {
      double coordinate = axisComponent(hit, axis);
      if (Math.abs(Math.abs(coordinate) - halfExtents[axis]) > FACE_EPSILON) {
        continue;
      }

      Vec3 outwardNormal = faceNormalForAxis(axis, coordinate > 0.0D);
      double alignment = outwardNormal.dot(direction);
      if (bestAxis < 0 || alignment < bestAlignment) {
        bestAxis = axis;
        bestAlignment = alignment;
      }
    }

    if (bestAxis < 0) {
      return new Vec3(0.0D, 1.0D, 0.0D);
    }

    return faceNormalForAxis(bestAxis, axisComponent(hit, bestAxis) > 0.0D);
  }

  private static Vec3 faceNormalForAxis(int axis, boolean positiveFace) {
    double normalComponent = positiveFace ? 1.0D : -1.0D;
    return switch (axis) {
      case 0 -> new Vec3(normalComponent, 0.0D, 0.0D);
      case 1 -> new Vec3(0.0D, normalComponent, 0.0D);
      default -> new Vec3(0.0D, 0.0D, normalComponent);
    };
  }

  private static double axisComponent(Vec3 vector, int axis) {
    return switch (axis) {
      case 0 -> vector.x;
      case 1 -> vector.y;
      default -> vector.z;
    };
  }
}
