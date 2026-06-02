package com.binhjcao.physicstorches;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;

public abstract class Collider {
  public record SurfaceHit(Vec3 worldPoint, Vec3 worldNormal) {}

  public abstract Vec3 halfExtents();

  public abstract Vec3[] worldCorners(Vec3 center, Quaternionf orientation, double halfExtentInflate);

  public Vec3[] worldCorners(Vec3 center, Quaternionf orientation) {
    return worldCorners(center, orientation, 0.0D);
  }

  public abstract AABB orientedBounds(Vec3 center, Quaternionf orientation, double inflate);

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
    Vec3 half = halfExtents();
    Vec3 localOrigin = OrientedTransform.toLocalPoint(rayOrigin, center, orientation);
    Vec3 localDirection = OrientedTransform.toLocalDirection(rayDirection, orientation);
    Optional<BoxRayIntersection.LocalHit> localHit =
        BoxRayIntersection.intersect(
            localOrigin, localDirection, half.x, half.y, half.z, surfaceTolerance);
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

  protected static AABB boundsFromCorners(Vec3[] corners, double inflate) {
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
