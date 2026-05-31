package com.binhjcao.physicstorches;

import org.jspecify.annotations.NonNull;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.EntityTorch;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
  public static final EntityType<EntityTorch> TORCH = register("torch", EntityType.Builder.<EntityTorch>of(EntityTorch::new, MobCategory.MISC)
          .sized((float) (EntityTorch.HALF_WIDTH * 2F), (float) EntityTorch.HEIGHT)
          .clientTrackingRange(64)
          .updateInterval(1));

  public static final EntityType<EntityRigidBody> RIGID_BODY_CUBE = register("rigid_body_cube", EntityType.Builder.<EntityRigidBody>of(EntityRigidBody::new, MobCategory.MISC)
          .sized((float) (EntityRigidBody.HALF_SIZE * 2F), (float) (EntityRigidBody.HALF_SIZE * 2F))
          .clientTrackingRange(64)
          .updateInterval(1));

  private static <T extends Entity> EntityType<T> register(@NonNull String name, EntityType.Builder<T> builder) {
    ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, name));
    return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
  }

  private ModEntityTypes() {}
}
