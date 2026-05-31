package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityTorch;
import com.binhjcao.physicstorches.entity.EntityRigidBody;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class PhysicsTorchesEntities {
  private static final ResourceKey<EntityType<?>> TORCH_KEY =
      ResourceKey.create(
          Registries.ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "torch"));

  public static final EntityType<EntityTorch> TORCH =
      EntityType.Builder.<EntityTorch>of(EntityTorch::new, MobCategory.MISC)
          .sized((float) (EntityTorch.HALF_WIDTH * 2F), (float) EntityTorch.HEIGHT)
          .clientTrackingRange(64)
          .updateInterval(1)
          .build(TORCH_KEY);

  private static final ResourceKey<EntityType<?>> RIGID_BODY_CUBE_KEY =
      ResourceKey.create(
          Registries.ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "rigid_body_cube"));

  public static final EntityType<EntityRigidBody> RIGID_BODY_CUBE =
      EntityType.Builder.<EntityRigidBody>of(EntityRigidBody::new, MobCategory.MISC)
          .sized((float) (EntityRigidBody.HALF_SIZE * 2F), (float) (EntityRigidBody.HALF_SIZE * 2F))
          .clientTrackingRange(64)
          .updateInterval(1)
          .build(RIGID_BODY_CUBE_KEY);

  static void register() {
    Registry.register(BuiltInRegistries.ENTITY_TYPE, TORCH_KEY, TORCH);
    Registry.register(BuiltInRegistries.ENTITY_TYPE, RIGID_BODY_CUBE_KEY, RIGID_BODY_CUBE);
  }

  private PhysicsTorchesEntities() {}
}
