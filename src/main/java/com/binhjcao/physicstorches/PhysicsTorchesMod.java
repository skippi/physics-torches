package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.EntityTorch;
import com.binhjcao.physicstorches.entity.LevelRigidBodyRegistry;
import com.binhjcao.physicstorches.entity.TorchLight;
import com.binhjcao.physicstorches.network.DropTorchPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class PhysicsTorchesMod implements ModInitializer {
  public static final String MOD_ID = "physics-torches";

  @Override
  public void onInitialize() {
    ServerTickEvents.END_LEVEL_TICK.register(Physics::step);
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, level) -> {
          if (entity instanceof EntityRigidBody body) {
            LevelRigidBodyRegistry.add(body);
          }
        });
    ServerEntityEvents.ENTITY_UNLOAD.register(
        (entity, level) -> {
          if (entity instanceof EntityRigidBody body) {
            LevelRigidBodyRegistry.remove(body);
          }
        });
    ServerLevelEvents.LOAD.register((server, level) -> {
      TorchLight.onLevelLoad(level);
      LevelRigidBodyRegistry.reconcile(level);
    });
    ServerLevelEvents.UNLOAD.register((server, level) -> LevelRigidBodyRegistry.clearLevel(level));
    PhysicsTorchesCommands.register();
    PayloadTypeRegistry.serverboundPlay().register(DropTorchPayload.TYPE, DropTorchPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(
        DropTorchPayload.TYPE,
        (payload, context) -> throwHeldTorch(context.player(), payload.playerDeltaMovement()));
  }

  public static boolean isTorch(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }

    return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("torch");
  }

  static void throwHeldTorch(ServerPlayer player, Vec3 playerDeltaMovement) {
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack stack = player.getItemInHand(hand);
      if (isTorch(stack) && EntityTorch.throwFromPlayer(player, stack, hand, playerDeltaMovement)) {
        return;
      }
    }
  }
}
