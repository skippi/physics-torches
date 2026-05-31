package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.entity.RigidBodyCollisionModel.SurfaceHit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class RigidBodyCrosshairHover {
  private static @Nullable EntityRigidBody hoveredBody;

  private RigidBodyCrosshairHover() {}

  public static void tick(Minecraft client, ClientLevel level, float partialTick) {
    if (client.player == null || client.screen != null || client.isPaused()) {
      setHovered(null);
      return;
    }

    EntityRigidBody.PlayerLookRay ray = EntityRigidBody.PlayerLookRay.from(client.player, partialTick);
    if (!ray.isValid()) {
      setHovered(null);
      return;
    }

    AABB searchBox = client.player.getBoundingBox().inflate(EntityRigidBody.TARGET_REACH);
    setHovered(findHoveredBody(client, level, searchBox, ray, partialTick).orElse(null));
  }

  private static void setHovered(@Nullable EntityRigidBody body) {
    hoveredBody = body;
  }

  public static @Nullable EntityRigidBody hoveredBody() {
    return hoveredBody;
  }

  private static Optional<EntityRigidBody> findHoveredBody(
      Minecraft client,
      ClientLevel level,
      AABB searchBox,
      EntityRigidBody.PlayerLookRay ray,
      float partialTick) {
    SurfaceHit closestHit = null;
    EntityRigidBody closestBody = null;

    for (EntityRigidBody body : level.getEntitiesOfClass(EntityRigidBody.class, searchBox)) {
      if (!body.inputRayPickable()) {
        continue;
      }

      Optional<SurfaceHit> hit =
          body.raycastSurface(ray.origin(), ray.direction(), partialTick, EntityRigidBody.TARGET_REACH);
      if (hit.isEmpty()) {
        continue;
      }

      double dist = hit.get().worldPoint().distanceToSqr(ray.origin());
      if (closestHit == null || dist < closestHit.worldPoint().distanceToSqr(ray.origin())) {
        closestHit = hit.get();
        closestBody = body;
      }
    }

    if (closestHit == null || closestBody == null) {
      return Optional.empty();
    }

    double bodyDistSq = closestHit.worldPoint().distanceToSqr(ray.origin());
    if (bodyDistSq >= closestBlockHitDistanceSq(client, ray)) {
      return Optional.empty();
    }

    if (closerEntityBlocksBody(client, ray, closestBody, bodyDistSq)) {
      return Optional.empty();
    }

    return Optional.of(closestBody);
  }

  private static double closestBlockHitDistanceSq(Minecraft client, EntityRigidBody.PlayerLookRay ray) {
    HitResult hitResult = client.hitResult;
    if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() != HitResult.Type.MISS) {
      return blockHit.getLocation().distanceToSqr(ray.origin());
    }

    return Double.POSITIVE_INFINITY;
  }

  private static boolean closerEntityBlocksBody(
      Minecraft client,
      EntityRigidBody.PlayerLookRay ray,
      EntityRigidBody body,
      double bodyDistSq) {
    HitResult hitResult = client.hitResult;
    if (!(hitResult instanceof EntityHitResult entityHit) || hitResult.getType() == HitResult.Type.MISS) {
      return false;
    }

    if (entityHit.getEntity() == body) {
      return false;
    }

    return entityHit.getLocation().distanceToSqr(ray.origin()) <= bodyDistSq;
  }
}
