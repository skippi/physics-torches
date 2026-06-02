package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.PhysicsTorchesMod;
import net.minecraft.client.gui.components.debug.DebugEntryNoop;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;

public final class PhysicsTorchesDebugOptions {
  public static final Identifier VISUALIZE_RIGIDBODY =
      Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "visualize_rigidbody");
  public static final Identifier VISUALIZE_TRANSFORM =
      Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "visualize_transform");
  public static final Identifier VISUALIZE_CONTACTS =
      Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "visualize_contacts");

  private PhysicsTorchesDebugOptions() {}

  public static void register() {
    DebugScreenEntries.register(VISUALIZE_RIGIDBODY, new DebugEntryNoop(true));
    DebugScreenEntries.register(VISUALIZE_TRANSFORM, new DebugEntryNoop(true));
    DebugScreenEntries.register(VISUALIZE_CONTACTS, new DebugEntryNoop(true));
  }
}
