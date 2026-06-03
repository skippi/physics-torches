package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.client.EntityRigidBodyRenderer;
import com.binhjcao.physicstorches.client.EntityTorchRenderer;
import com.binhjcao.physicstorches.client.PhysicsTorchesDebugOptions;
import com.binhjcao.physicstorches.client.PhysicsTorchesKeyMappings;
import com.binhjcao.physicstorches.client.RigidBodyDebugRenderer;
import com.binhjcao.physicstorches.client.TorchHoverOutlineRenderer;
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
  private static final int THROW_TORCH_DELAY = 4;
  private static int throwTorchDelay;

  @Override
  public void onInitializeClient() {
    PhysicsTorchesDebugOptions.register();
    PhysicsTorchesKeyMappings.register();

    EntityRenderers.register(ModEntityTypes.TORCH, EntityTorchRenderer::new);
    EntityRenderers.register(ModEntityTypes.REDSTONE_TORCH, EntityTorchRenderer::new);
    EntityRenderers.register(ModEntityTypes.SOUL_TORCH, EntityTorchRenderer::new);
    EntityRenderers.register(ModEntityTypes.COPPER_TORCH, EntityTorchRenderer::new);
    EntityRenderers.register(ModEntityTypes.RIGID_BODY_CUBE, EntityRigidBodyRenderer::new);

    ClientTickEvents.END_CLIENT_TICK.register(PhysicsTorchesModClient::onClientTick);
  }

  private static void onClientTick(Minecraft client) {
    if (client.level instanceof ClientLevel clientLevel) {
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

    if (throwTorchDelay > 0) {
      throwTorchDelay--;
    }

    while (PhysicsTorchesKeyMappings.throwTorch.consumeClick()
        && throwTorchDelay == 0
        && !client.player.isUsingItem()) {
      tryThrowTorch(client);
    }
  }

  private static void tryThrowTorch(Minecraft client) {
    if (!holdingTorch(client.player)
        || !ClientPlayNetworking.canSend(DropTorchPayload.TYPE)) {
      return;
    }

    ClientPlayNetworking.send(new DropTorchPayload(movementPerTick(client.player)));
    throwTorchDelay = THROW_TORCH_DELAY;
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
