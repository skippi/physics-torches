package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityRigidBodyLinear extends EntityRigidBody {
  private static final double IMPULSE_STRENGTH = 2.5D;

  private int lastImpulseTick = -1;

  public EntityRigidBodyLinear(EntityType<? extends EntityRigidBodyLinear> type, Level level) {
    super(type, level);
    gravityScale(0);
    linearDamp(0);
    angularDamp(0);
    sleepThreshold(0);
  }

  public EntityRigidBodyLinear(Level level, Vec3 position) {
    super(PhysicsTorchesEntities.RIGID_BODY_LINEAR, level, position, null);
    gravityScale(0);
    linearDamp(0);
    angularDamp(0);
    sleepThreshold(0);
  }

  @Override
  public boolean isAttackable() {
    return true;
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean skipAttackInteraction(Entity attacker) {
    if (!level().isClientSide() && attacker instanceof ServerPlayer player) {
      return applyImpulseFromPlayer(player);
    }

    return false;
  }

  @Override
  public boolean hurtServer(
      net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
    Entity attacker = source.getEntity();
    if (attacker instanceof ServerPlayer player && source.is(DamageTypes.PLAYER_ATTACK)) {
      return applyImpulseFromPlayer(player);
    }

    return false;
  }

  private boolean applyImpulseFromPlayer(ServerPlayer player) {
    if (tickCount == lastImpulseTick) {
      return true;
    }

    PlayerLookRay ray = PlayerLookRay.from(player, 1.0F);
    if (!ray.isValid()) {
      return false;
    }

    var hit = raycastSurface(ray.origin(), ray.direction(), 1.0F, TARGET_REACH);
    if (hit.isEmpty()) {
      return false;
    }

    RigidBodyCollisionModel.SurfaceHit surfaceHit = hit.get();
    Vec3 hitPoint = surfaceHit.worldPoint();
    Vec3 impulse = ray.normalizedDirection().scale(IMPULSE_STRENGTH);
    Vec3 leverArm = hitPoint.subtract(position());
    applyImpulse(impulse, leverArm);
    lastImpulseTick = tickCount;
    return true;
  }
}
