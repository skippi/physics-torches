package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.EntityTorch;
import com.binhjcao.physicstorches.entity.RigidBodyCrosshairHover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class TorchHoverOutlineRenderer {
  private static final double OUTLINE_INFLATE = 0.05D;
  private static final int OUTLINE_COLOR = ARGB.black(102);

  private TorchHoverOutlineRenderer() {}

  public static void emit(Minecraft client, ClientLevel level, float partialTick) {
    if (client.player == null || client.screen != null || client.isPaused()) {
      return;
    }

    EntityRigidBody hovered = RigidBodyCrosshairHover.hoveredBody();
    if (!(hovered instanceof EntityTorch torch)) {
      return;
    }

    float lineWidth =
        client.gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
    renderCollisionOutline(torch, partialTick, lineWidth);
  }

  private static void renderCollisionOutline(EntityRigidBody body, float partialTick, float lineWidth) {
    Quaternionf orientation = body.getOrientation(partialTick);
    Vec3 center = body.getPosition(partialTick);
    Vec3[] corners = body.rigidBody().collider().worldCorners(center, orientation, OUTLINE_INFLATE);

    emitEdge(corners[0], corners[1], lineWidth);
    emitEdge(corners[1], corners[3], lineWidth);
    emitEdge(corners[3], corners[2], lineWidth);
    emitEdge(corners[2], corners[0], lineWidth);
    emitEdge(corners[4], corners[5], lineWidth);
    emitEdge(corners[5], corners[7], lineWidth);
    emitEdge(corners[7], corners[6], lineWidth);
    emitEdge(corners[6], corners[4], lineWidth);
    emitEdge(corners[0], corners[4], lineWidth);
    emitEdge(corners[1], corners[5], lineWidth);
    emitEdge(corners[2], corners[6], lineWidth);
    emitEdge(corners[3], corners[7], lineWidth);
  }

  private static void emitEdge(Vec3 start, Vec3 end, float lineWidth) {
    Gizmos.line(start, end, OUTLINE_COLOR, lineWidth);
  }
}
