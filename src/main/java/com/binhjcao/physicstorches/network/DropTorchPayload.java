package com.binhjcao.physicstorches.network;

import com.binhjcao.physicstorches.PhysicsTorchesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DropTorchPayload() implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<DropTorchPayload> TYPE =
      new CustomPacketPayload.Type<>(
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "drop_torch"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DropTorchPayload> CODEC =
      StreamCodec.unit(new DropTorchPayload());

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
