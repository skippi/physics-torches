package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityTorchRigidBody;
import com.binhjcao.physicstorches.entity.EntityRigidBody;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class PhysicsTorchesEntities {
  private static final ResourceKey<EntityType<?>> TORCH_RIGIDBODY_KEY =
      ResourceKey.create(
          Registries.ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "torch_rigidbody"));

  public static final EntityType<EntityTorchRigidBody> TORCH_RIGIDBODY =
      EntityType.Builder.<EntityTorchRigidBody>of(EntityTorchRigidBody::new, MobCategory.MISC)
          .sized((float) (EntityTorchRigidBody.HALF_WIDTH * 2F), (float) EntityTorchRigidBody.HEIGHT)
          .clientTrackingRange(64)
          .updateInterval(1)
          .build(TORCH_RIGIDBODY_KEY);

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
    Registry.register(BuiltInRegistries.ENTITY_TYPE, TORCH_RIGIDBODY_KEY, TORCH_RIGIDBODY);
    Registry.register(BuiltInRegistries.ENTITY_TYPE, RIGID_BODY_CUBE_KEY, RIGID_BODY_CUBE);
  }

  private PhysicsTorchesEntities() {}
}
