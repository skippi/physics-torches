package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityPhysicsTorch;
import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.EntityRigidBodyTorque;
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

  private static final ResourceKey<EntityType<?>> RIGID_BODY_TORQUE_KEY =
      ResourceKey.create(
          Registries.ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "rigid_body_torque"));

  public static final EntityType<EntityRigidBodyTorque> RIGID_BODY_TORQUE =
      EntityType.Builder.<EntityRigidBodyTorque>of(EntityRigidBodyTorque::new, MobCategory.MISC)
          .sized((float) (EntityRigidBody.HALF_SIZE * 2F), (float) (EntityRigidBody.HALF_SIZE * 2F))
          .clientTrackingRange(64)
          .updateInterval(1)
          .build(RIGID_BODY_TORQUE_KEY);

  static void register() {
    Registry.register(BuiltInRegistries.ENTITY_TYPE, PHYSICS_TORCH_KEY, PHYSICS_TORCH);
    Registry.register(BuiltInRegistries.ENTITY_TYPE, RIGID_BODY_CUBE_KEY, RIGID_BODY_CUBE);
    Registry.register(BuiltInRegistries.ENTITY_TYPE, RIGID_BODY_TORQUE_KEY, RIGID_BODY_TORQUE);
  }

  private PhysicsTorchesEntities() {}
}
