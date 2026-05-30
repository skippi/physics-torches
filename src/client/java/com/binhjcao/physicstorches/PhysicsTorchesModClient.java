package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.client.EntityPhysicsTorchRenderer;
import com.binhjcao.physicstorches.client.EntityRigidBodyRenderer;
import com.binhjcao.physicstorches.client.PhysicsTorchParticles;
import com.binhjcao.physicstorches.client.PhysicsTorchesDebugOptions;
import com.binhjcao.physicstorches.client.RigidBodyDebugRenderer;
import com.binhjcao.physicstorches.network.DropTorchPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

public class PhysicsTorchesModClient implements ClientModInitializer {
  private static boolean useKeyWasDown;

  @Override
  public void onInitializeClient() {
    PhysicsTorchesDebugOptions.register();

    EntityRendererRegistry.register(
        PhysicsTorchesEntities.PHYSICS_TORCH, EntityPhysicsTorchRenderer::new);
    EntityRendererRegistry.register(
        PhysicsTorchesEntities.RIGID_BODY_CUBE, EntityRigidBodyRenderer::new);

    ClientTickEvents.END_CLIENT_TICK.register(PhysicsTorchesModClient::onClientTick);

    UseBlockCallback.EVENT.register(PhysicsTorchesModClient::cancelIfThrowingTorch);
    UseItemCallback.EVENT.register(PhysicsTorchesModClient::cancelIfThrowingTorch);
  }

  private static InteractionResult cancelIfThrowingTorch(
      Player player, Level world, InteractionHand hand) {
    if (!world.isClientSide() || !shouldThrowTorch(player)) {
      return InteractionResult.PASS;
    }

    return InteractionResult.FAIL;
  }

  private static InteractionResult cancelIfThrowingTorch(
      Player player, Level world, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
    return cancelIfThrowingTorch(player, world, hand);
  }

  private static void onClientTick(Minecraft client) {
    if (client.level instanceof ClientLevel clientLevel) {
      PhysicsTorchParticles.tick(clientLevel, client);
      RigidBodyDebugRenderer.tick(client);
    }

    if (client.player == null || client.screen != null) {
      useKeyWasDown = client.options.keyUse.isDown();
      return;
    }

    boolean useKeyDown = client.options.keyUse.isDown();
    if (useKeyDown
        && !useKeyWasDown
        && shouldThrowTorch(client.player)
        && ClientPlayNetworking.canSend(DropTorchPayload.TYPE)) {
      ClientPlayNetworking.send(new DropTorchPayload());
    }

    useKeyWasDown = useKeyDown;
  }

  private static boolean shouldThrowTorch(Player player) {
    Minecraft client = Minecraft.getInstance();
    if (!InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)) {
      return false;
    }

    if (!client.options.keyUse.isDown()) {
      return false;
    }

    return PhysicsTorchesMod.isTorch(player.getItemInHand(InteractionHand.MAIN_HAND))
        || PhysicsTorchesMod.isTorch(player.getItemInHand(InteractionHand.OFF_HAND));
  }
}
