package com.binhjcao.physicstorches;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;

public final class BoxCollider {
  private static final int[][] CORNER_SIGNS = {
    {-1, -1, -1}, {1, -1, -1}, {-1, -1, 1}, {1, -1, 1},
    {-1, 1, -1}, {1, 1, -1}, {-1, 1, 1}, {1, 1, 1}
  };

  public record SurfaceHit(Vec3 worldPoint, Vec3 worldNormal) {}

  private final double halfX;
  private final double halfY;
  private final double halfZ;

  private BoxCollider(double halfX, double halfY, double halfZ) {
    this.halfX = halfX;
    this.halfY = halfY;
    this.halfZ = halfZ;
  }

  public static BoxCollider cube(double halfSize) {
    return box(halfSize, halfSize, halfSize);
  }

  public static BoxCollider box(double halfX, double halfY, double halfZ) {
    return new BoxCollider(halfX, halfY, halfZ);
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
    Vec3 localOrigin = OrientedTransform.toLocalPoint(rayOrigin, center, orientation);
    Vec3 localDirection = OrientedTransform.toLocalDirection(rayDirection, orientation);
    Optional<BoxRayIntersection.LocalHit> localHit =
        BoxRayIntersection.intersect(
            localOrigin, localDirection, halfX, halfY, halfZ, surfaceTolerance);
    if (localHit.isEmpty()) {
      return Optional.empty();
    }

    BoxRayIntersection.LocalHit hit = localHit.get();
    Vec3 worldPoint = OrientedTransform.toWorldPoint(hit.point(), center, orientation);
    if (worldPoint.subtract(rayOrigin).length() > maxReach) {
      return Optional.empty();
    }

    Vec3 worldNormal =
        OrientedTransform.toWorldDirection(hit.outwardNormal(), orientation).normalize();
    return Optional.of(new SurfaceHit(worldPoint, worldNormal));
  }

  public Vec3 localToWorld(Vec3 localPoint, Vec3 center, Quaternionf orientation) {
    return OrientedTransform.toWorldPoint(localPoint, center, orientation);
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
}
