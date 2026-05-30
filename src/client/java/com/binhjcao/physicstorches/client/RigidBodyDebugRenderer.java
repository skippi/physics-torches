package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.EntityRigidBodyTorque;
import com.binhjcao.physicstorches.entity.RigidBodyCollisionModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;

public final class RigidBodyDebugRenderer {
  private static final int COLLISION_OUTLINE_COLOR = 0xFF00FF00;
  private static final int HIT_POINT_COLOR = 0xFFFF5555;
  private static final int HIT_NORMAL_COLOR = 0xFF55FFFF;
  private static final int FORCE_HIT_COLOR = 0xFFFFAA00;
  private static final int FORCE_DIRECTION_COLOR = 0xFFFF5500;
  private static final double FORCE_ARROW_LENGTH = 0.75D;

  private RigidBodyDebugRenderer() {}

  public static void tick(Minecraft client) {
    if (!(client.level instanceof ClientLevel level) || client.player == null) {
      return;
    }

    boolean visualizeCollision =
        client.debugEntries.isCurrentlyEnabled(PhysicsTorchesDebugOptions.VISUALIZE_RIGIDBODY);
    boolean visualizeTransform =
        client.debugEntries.isCurrentlyEnabled(PhysicsTorchesDebugOptions.VISUALIZE_TRANSFORM);
    if (!visualizeCollision && !visualizeTransform) {
      return;
    }

    try (var ignored = client.collectPerTickGizmos()) {
      float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      if (visualizeCollision) {
        renderCollisionOutlines(level, partialTick);
      }

      if (visualizeTransform) {
        renderTransformTarget(client, level, partialTick);
      }
    }
  }

  private static void renderCollisionOutlines(ClientLevel level, float partialTick) {
    for (Entity entity : level.entitiesForRendering()) {
      if (entity instanceof EntityRigidBody rigidBody) {
        renderCollisionOutline(rigidBody, partialTick);
        if (entity instanceof EntityRigidBodyTorque torqueBody) {
          renderDebugImpulse(torqueBody);
        }
      }
    }
  }

  private static void renderDebugImpulse(EntityRigidBodyTorque body) {
    if (!body.hasDebugImpulse()) {
      return;
    }

    Vec3 hitPoint = body.getDebugHitPoint();
    Vec3 force = body.getDebugForce();
    if (force.lengthSqr() < 1.0E-8D) {
      return;
    }

    Vec3 forceDirection = force.normalize();
    Gizmos.point(hitPoint, FORCE_HIT_COLOR, 5.0F);
    Gizmos.arrow(
        hitPoint.subtract(forceDirection.scale(FORCE_ARROW_LENGTH)),
        hitPoint,
        FORCE_DIRECTION_COLOR);
  }

  private static void renderCollisionOutline(EntityRigidBody body, float partialTick) {
    Quaternionf orientation = body.getOrientation(partialTick);
    Vec3 center = body.position();
    Vec3[] corners = body.collisionModel().worldCorners(center, orientation);

    emitEdge(corners[0], corners[1]);
    emitEdge(corners[1], corners[3]);
    emitEdge(corners[3], corners[2]);
    emitEdge(corners[2], corners[0]);
    emitEdge(corners[4], corners[5]);
    emitEdge(corners[5], corners[7]);
    emitEdge(corners[7], corners[6]);
    emitEdge(corners[6], corners[4]);
    emitEdge(corners[0], corners[4]);
    emitEdge(corners[1], corners[5]);
    emitEdge(corners[2], corners[6]);
    emitEdge(corners[3], corners[7]);
  }

  private static void emitEdge(Vec3 start, Vec3 end) {
    Gizmos.line(start, end, COLLISION_OUTLINE_COLOR);
  }

  private static void renderTransformTarget(Minecraft client, ClientLevel level, float partialTick) {
    var player = client.player;
    if (player == null) {
      return;
    }

    EntityRigidBody.PlayerLookRay ray = EntityRigidBody.PlayerLookRay.from(player, partialTick);
    AABB searchBox = player.getBoundingBox().inflate(EntityRigidBody.TARGET_REACH);
    Optional<RigidBodyCollisionModel.SurfaceHit> closestHit =
        EntityRigidBody.raycastClosest(level, searchBox, ray, partialTick, EntityRigidBody.TARGET_REACH);
    if (closestHit.isEmpty()) {
      return;
    }

    Vec3 hitPoint = closestHit.get().worldPoint();
    Vec3 normal = closestHit.get().worldNormal();
    Gizmos.point(hitPoint, HIT_POINT_COLOR, 5.0F);
    Gizmos.arrow(hitPoint, hitPoint.add(normal.scale(0.35D)), HIT_NORMAL_COLOR);
  }
}
