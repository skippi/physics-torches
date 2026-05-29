package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityPhysicsTorch;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class PhysicsTorchesEntities {
  private static final ResourceKey<EntityType<?>> PHYSICS_TORCH_KEY =
      ResourceKey.create(
          Registries.ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "physics_torch"));

  public static final EntityType<EntityPhysicsTorch> PHYSICS_TORCH =
      EntityType.Builder.<EntityPhysicsTorch>of(EntityPhysicsTorch::new, MobCategory.MISC)
          .sized((float) (EntityPhysicsTorch.HALF_WIDTH * 2F), (float) EntityPhysicsTorch.HEIGHT)
          .clientTrackingRange(64)
          .updateInterval(1)
          .build(PHYSICS_TORCH_KEY);

  static void register() {
    Registry.register(BuiltInRegistries.ENTITY_TYPE, PHYSICS_TORCH_KEY, PHYSICS_TORCH);
  }

  private PhysicsTorchesEntities() {}
}
