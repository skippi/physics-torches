package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.PhysicsTorchesMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PhysicsTorchesKeyMappings {
  public static KeyMapping throwTorch;

  private PhysicsTorchesKeyMappings() {}

  public static void register() {
    KeyMapping.Category category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "general"));
    throwTorch =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key." + PhysicsTorchesMod.MOD_ID + ".throw_torch",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));
  }
}
