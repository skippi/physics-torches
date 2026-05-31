package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.network.DropTorchPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class PhysicsTorchesModClient implements ClientModInitializer {
  private static boolean useKeyWasDown;

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(PhysicsTorchesModClient::onClientTick);

    UseItemCallback.EVENT.register(
        (player, world, hand) -> {
          if (!world.isClientSide() || !shouldDropTorch(player)) {
            return InteractionResult.PASS;
          }

          return InteractionResult.SUCCESS;
        });
  }

  private static void onClientTick(Minecraft client) {
    if (client.player == null || client.screen != null) {
      useKeyWasDown = client.options.keyUse.isDown();
      return;
    }

    boolean useKeyDown = client.options.keyUse.isDown();
    if (useKeyDown
        && !useKeyWasDown
        && shouldDropTorch(client.player)
        && ClientPlayNetworking.canSend(DropTorchPayload.TYPE)) {
      ClientPlayNetworking.send(new DropTorchPayload());
    }

    useKeyWasDown = useKeyDown;
  }

  private static boolean shouldDropTorch(Player player) {
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
