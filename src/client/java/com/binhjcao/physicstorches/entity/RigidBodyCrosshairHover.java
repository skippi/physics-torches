package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.RaycastHit;
import com.binhjcao.physicstorches.physics.Physics;

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
    Optional<RaycastHit> hit =
        Physics.raycast(
            level,
            searchBox,
            ray,
            partialTick,
            EntityRigidBody.TARGET_REACH,
            EntityRigidBody::inputRayPickable);
    if (hit.isEmpty()) {
      return Optional.empty();
    }

    RaycastHit bodyHit = hit.get();
    double bodyDistSq = bodyHit.point().distanceToSqr(ray.origin());
    if (bodyDistSq >= closestBlockHitDistanceSq(client, ray)) {
      return Optional.empty();
    }

    if (closerEntityBlocksBody(client, ray, bodyHit.body(), bodyDistSq)) {
      return Optional.empty();
    }

    return Optional.of(bodyHit.body());
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
