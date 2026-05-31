package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.client.EntityRigidBodyRenderer;
import com.binhjcao.physicstorches.client.EntityTorchRenderer;
import com.binhjcao.physicstorches.client.PhysicsTorchesDebugOptions;
import com.binhjcao.physicstorches.client.PhysicsTorchesKeyMappings;
import com.binhjcao.physicstorches.client.RigidBodyDebugRenderer;
import com.binhjcao.physicstorches.client.TorchHoverOutlineRenderer;
import com.binhjcao.physicstorches.client.TorchParticles;
import com.binhjcao.physicstorches.entity.RigidBodyCrosshairHover;
import com.binhjcao.physicstorches.network.DropTorchPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PhysicsTorchesModClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    PhysicsTorchesDebugOptions.register();
    PhysicsTorchesKeyMappings.register();

    EntityRenderers.register(
        ModEntityTypes.TORCH, EntityTorchRenderer::new);
    EntityRenderers.register(
        ModEntityTypes.RIGID_BODY_CUBE, EntityRigidBodyRenderer::new);

    ClientTickEvents.END_CLIENT_TICK.register(PhysicsTorchesModClient::onClientTick);
  }

  private static void onClientTick(Minecraft client) {
    if (client.level instanceof ClientLevel clientLevel) {
      TorchParticles.tick(clientLevel, client);
      RigidBodyDebugRenderer.tick(client);

      float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      RigidBodyCrosshairHover.tick(client, clientLevel, partialTick);
      try (var ignored = client.collectPerTickGizmos()) {
        TorchHoverOutlineRenderer.emit(client, clientLevel, partialTick);
      }
    }

    if (client.player == null || client.screen != null) {
      return;
    }

    while (PhysicsTorchesKeyMappings.throwTorch.consumeClick()) {
      if (!holdingTorch(client.player)
          || !ClientPlayNetworking.canSend(DropTorchPayload.TYPE)) {
        continue;
      }

      ClientPlayNetworking.send(new DropTorchPayload(movementPerTick(client.player)));
    }
  }

  private static boolean holdingTorch(Player player) {
    return PhysicsTorchesMod.isTorch(player.getItemInHand(InteractionHand.MAIN_HAND))
        || PhysicsTorchesMod.isTorch(player.getItemInHand(InteractionHand.OFF_HAND));
  }

  private static Vec3 movementPerTick(Player player) {
    return new Vec3(
        player.getX() - player.xOld,
        player.getY() - player.yOld,
        player.getZ() - player.zOld);
  }
}
