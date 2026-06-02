package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.RaycastHit;
import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.physics.ContactManifold;
import com.binhjcao.physicstorches.physics.Physics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Optional;

public final class RigidBodyDebugRenderer {
  private static final int COLLISION_OUTLINE_COLOR = 0xFF00FF00;
  private static final int HIT_POINT_COLOR = 0xFFFF5555;
  private static final int HIT_NORMAL_COLOR = 0xFF55FFFF;
  private static final int CONTACT_POINT_COLOR = 0xFFFFAA00;
  private static final int CONTACT_NORMAL_COLOR = 0xFFFF55FF;

  private RigidBodyDebugRenderer() {}

  public static void tick(Minecraft client) {
    if (!(client.level instanceof ClientLevel level) || client.player == null) {
      return;
    }

    if (!client.getDebugOverlay().showDebugScreen()) {
      return;
    }

    boolean visualizeCollision =
        client.debugEntries.isCurrentlyEnabled(PhysicsTorchesDebugOptions.VISUALIZE_RIGIDBODY);
    boolean visualizeTransform =
        client.debugEntries.isCurrentlyEnabled(PhysicsTorchesDebugOptions.VISUALIZE_TRANSFORM);
    boolean visualizeContacts =
        client.debugEntries.isCurrentlyEnabled(PhysicsTorchesDebugOptions.VISUALIZE_CONTACTS);
    if (!visualizeCollision && !visualizeTransform && !visualizeContacts) {
      return;
    }

    try (var ignored = client.collectPerTickGizmos()) {
      float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      if (visualizeCollision) {
        renderCollisionOutlines(level, partialTick);
      }

      if (visualizeContacts) {
        renderContactPoints(level);
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
      }
    }
  }

  private static void renderCollisionOutline(EntityRigidBody body, float partialTick) {
    Quaternionf orientation = body.getOrientation(partialTick);
    Vec3 center = body.getPosition(partialTick);
    Vec3[] corners = body.collider().worldCorners(center, orientation);

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

  private static void renderContactPoints(ClientLevel level) {
    for (Entity entity : level.entitiesForRendering()) {
      if (!(entity instanceof EntityRigidBody rigidBody) || rigidBody.isSleeping()) {
        continue;
      }
      for (ContactManifold manifold : Physics.findContactManifoldsForEntity(rigidBody, level)) {
        renderContactSide(manifold.contactsOnA(), manifold.normal());
      }
    }
  }

  private static void renderContactSide(ArrayList<Vec3> points, Vec3 outwardNormal) {
    for (Vec3 point : points) {
      Gizmos.point(point, CONTACT_POINT_COLOR, 8.0F);
      Gizmos.arrow(point, point.add(outwardNormal.scale(0.25D)), CONTACT_NORMAL_COLOR);
    }
  }

  private static void renderTransformTarget(Minecraft client, ClientLevel level, float partialTick) {
    var player = client.player;
    if (player == null) {
      return;
    }

    EntityRigidBody.PlayerLookRay ray = EntityRigidBody.PlayerLookRay.from(player, partialTick);
    AABB searchBox = player.getBoundingBox().inflate(EntityRigidBody.TARGET_REACH);
    Optional<RaycastHit> closestHit =
        Physics.raycast(level, searchBox, ray, partialTick, EntityRigidBody.TARGET_REACH);
    if (closestHit.isEmpty()) {
      return;
    }

    Vec3 hitPoint = closestHit.get().point();
    Vec3 normal = closestHit.get().normal();
    Gizmos.point(hitPoint, HIT_POINT_COLOR, 5.0F);
    Gizmos.arrow(hitPoint, hitPoint.add(normal.scale(0.35D)), HIT_NORMAL_COLOR);
  }
}
