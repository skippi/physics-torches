package com.binhjcao.physicstorches.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class RigidBodyCollisionModel {
  private static final double FACE_EPSILON = 1.0E-4D;
  private static final double RAY_EPSILON = 1.0E-8D;

  private static final int[][] CORNER_SIGNS = {
    {-1, -1, -1}, {1, -1, -1}, {-1, -1, 1}, {1, -1, 1},
    {-1, 1, -1}, {1, 1, -1}, {-1, 1, 1}, {1, 1, 1}
  };

  public record SurfaceHit(Vec3 worldPoint, Vec3 worldNormal) {
    public Vec3 inwardNormal() {
      return worldNormal.scale(-1.0D);
    }
  }

  private record LocalSurfaceHit(Vec3 point, Vec3 outwardNormal) {}

  private final double halfX;
  private final double halfY;
  private final double halfZ;

  private RigidBodyCollisionModel(double halfX, double halfY, double halfZ) {
    this.halfX = halfX;
    this.halfY = halfY;
    this.halfZ = halfZ;
  }

  public static RigidBodyCollisionModel cube(double halfSize) {
    return box(halfSize, halfSize, halfSize);
  }

  public static RigidBodyCollisionModel box(double halfX, double halfY, double halfZ) {
    return new RigidBodyCollisionModel(halfX, halfY, halfZ);
  }

  public double halfSize() {
    return Math.max(halfX, Math.max(halfY, halfZ));
  }

  public Vec3 halfExtents() {
    return new Vec3(halfX, halfY, halfZ);
  }

  public Optional<SurfaceHit> raycast(
      Quaternionf orientation,
      Vec3 center,
      Vec3 rayOrigin,
      Vec3 rayDirection,
      double maxReach) {
    return raycast(orientation, center, rayOrigin, rayDirection, maxReach, 0.0D);
  }

  public Optional<SurfaceHit> raycast(
      Quaternionf orientation,
      Vec3 center,
      Vec3 rayOrigin,
      Vec3 rayDirection,
      double maxReach,
      double surfaceTolerance) {
    if (rayDirection.lengthSqr() < RAY_EPSILON) {
      return Optional.empty();
    }

    Vec3 localOrigin = toLocalPoint(rayOrigin, center, orientation);
    Vec3 localDirection = toLocalDirection(rayDirection, orientation);
    Optional<LocalSurfaceHit> localHit =
        intersectRayBox(localOrigin, localDirection, halfX, halfY, halfZ, surfaceTolerance);
    if (localHit.isEmpty()) {
      return Optional.empty();
    }

    LocalSurfaceHit hit = localHit.get();
    Vec3 worldPoint = toWorldPoint(hit.point(), center, orientation);
    if (worldPoint.subtract(rayOrigin).length() > maxReach) {
      return Optional.empty();
    }

    Vec3 worldNormal = toWorldDirection(hit.outwardNormal(), orientation).normalize();
    return Optional.of(new SurfaceHit(worldPoint, worldNormal));
  }

  public Vec3 localToWorld(Vec3 localPoint, Vec3 center, Quaternionf orientation) {
    return toWorldPoint(localPoint, center, orientation);
  }

  public Vec3[] worldCorners(Vec3 center, Quaternionf orientation) {
    return worldCorners(center, orientation, 0.0D);
  }

  public Vec3[] worldCorners(Vec3 center, Quaternionf orientation, double halfExtentInflate) {
    Vec3[] corners = new Vec3[CORNER_SIGNS.length];
    double hx = halfX + halfExtentInflate;
    double hy = halfY + halfExtentInflate;
    double hz = halfZ + halfExtentInflate;
    for (int i = 0; i < CORNER_SIGNS.length; i++) {
      int[] signs = CORNER_SIGNS[i];
      corners[i] =
          localToWorld(new Vec3(signs[0] * hx, signs[1] * hy, signs[2] * hz), center, orientation);
    }
    return corners;
  }

  public AABB orientedBounds(Vec3 center, Quaternionf orientation, double inflate) {
    Vec3[] corners = worldCorners(center, orientation);
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;

    for (Vec3 corner : corners) {
      minX = Math.min(minX, corner.x);
      minY = Math.min(minY, corner.y);
      minZ = Math.min(minZ, corner.z);
      maxX = Math.max(maxX, corner.x);
      maxY = Math.max(maxY, corner.y);
      maxZ = Math.max(maxZ, corner.z);
    }

    AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    return inflate > 0.0D ? bounds.inflate(inflate) : bounds;
  }

  public static Vec3 inwardNormal(Vec3 outwardNormal) {
    return outwardNormal.scale(-1.0D);
  }

  private static Optional<LocalSurfaceHit> intersectRayBox(
      Vec3 origin,
      Vec3 direction,
      double halfX,
      double halfY,
      double halfZ,
      double surfaceTolerance) {
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
    return Optional.of(new LocalSurfaceHit(point, outwardNormal));
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
}
