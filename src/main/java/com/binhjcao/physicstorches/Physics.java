package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;
import java.util.function.Predicate;

public final class Physics {
  public static final double SURFACE_TOLERANCE = 0.05D;

  private Physics() {}

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance) {
    return raycast(level, searchBox, origin, direction, partialTick, maxDistance, body -> true);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      EntityRigidBody.PlayerLookRay ray,
      float partialTick,
      double maxDistance) {
    return raycast(level, searchBox, ray.origin(), ray.direction(), partialTick, maxDistance);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      EntityRigidBody.PlayerLookRay ray,
      float partialTick,
      double maxDistance,
      Predicate<EntityRigidBody> filter) {
    return raycast(
        level, searchBox, ray.origin(), ray.direction(), partialTick, maxDistance, filter);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance,
      Predicate<EntityRigidBody> filter) {
    RaycastHit closestHit = null;
    for (EntityRigidBody body : level.getEntitiesOfClass(EntityRigidBody.class, searchBox)) {
      if (!filter.test(body)) {
        continue;
      }

      Optional<RaycastHit> hit = raycastBody(body, origin, direction, partialTick, maxDistance);
      if (hit.isEmpty()) {
        continue;
      }

      if (closestHit == null
          || hit.get().point().distanceToSqr(origin) < closestHit.point().distanceToSqr(origin)) {
        closestHit = hit.get();
      }
    }

    return Optional.ofNullable(closestHit);
  }

  private record ColliderHit(Vec3 point, Vec3 normal) {}

  private static Optional<RaycastHit> raycastBody(
      EntityRigidBody body,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance) {
    Vec3 center = new Vec3(body.getX(), body.getY(), body.getZ());
    Quaternionf orientation = body.getOrientation(partialTick);
    BoxCollider collider = body.collider();
    Optional<ColliderHit> hit =
        raycastCollider(collider, orientation, center, origin, direction, maxDistance);
    if (hit.isEmpty() && SURFACE_TOLERANCE > 0.0D) {
      hit =
          raycastCollider(
              collider,
              orientation,
              center,
              origin,
              direction,
              maxDistance,
              SURFACE_TOLERANCE);
    }
    return hit.map(result -> new RaycastHit(body, result.point(), result.normal()));
  }

  private static Optional<ColliderHit> raycastCollider(
      BoxCollider collider,
      Quaternionf orientation,
      Vec3 center,
      Vec3 origin,
      Vec3 direction,
      double maxDistance) {
    return raycastCollider(
        collider, orientation, center, origin, direction, maxDistance, 0.0D);
  }

  private static Optional<ColliderHit> raycastCollider(
      BoxCollider collider,
      Quaternionf orientation,
      Vec3 center,
      Vec3 origin,
      Vec3 direction,
      double maxDistance,
      double surfaceTolerance) {
    return collider
        .raycast(orientation, center, origin, direction, maxDistance, surfaceTolerance)
        .map(hit -> new ColliderHit(hit.worldPoint(), hit.worldNormal()));
  }
}
