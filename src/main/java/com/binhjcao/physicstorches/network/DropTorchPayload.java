package com.binhjcao.physicstorches.network;

import com.binhjcao.physicstorches.PhysicsTorchesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record DropTorchPayload(Vec3 playerDeltaMovement) implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<DropTorchPayload> TYPE =
      new CustomPacketPayload.Type<>(
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "drop_torch"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DropTorchPayload> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.DOUBLE,
          payload -> payload.playerDeltaMovement().x,
          ByteBufCodecs.DOUBLE,
          payload -> payload.playerDeltaMovement().y,
          ByteBufCodecs.DOUBLE,
          payload -> payload.playerDeltaMovement().z,
          (x, y, z) -> new DropTorchPayload(new Vec3(x, y, z)));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
