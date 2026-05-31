package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.network.DropTorchPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class PhysicsTorchesMod implements ModInitializer {
  public static final String MOD_ID = "physics-torches";

  @Override
  public void onInitialize() {
    PayloadTypeRegistry.serverboundPlay().register(DropTorchPayload.TYPE, DropTorchPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(
        DropTorchPayload.TYPE, (payload, context) -> dropHeldTorch(context.player()));
  }

  public static boolean isTorch(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }

    return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("torch");
  }

  static void dropHeldTorch(ServerPlayer player) {
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack stack = player.getItemInHand(hand);
      if (isTorch(stack)) {
        player.drop(stack.split(1), true);
        return;
      }
    }
  }
}
