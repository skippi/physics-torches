package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityRigidBodyTorque extends EntityRigidBody {
  private static final double IMPULSE_STRENGTH = 3.0D;
  private static final double TORQUE_SCALE = 0.35D;
  private static final double ANGULAR_DAMPING = 0.985D;
  private static final int DEBUG_IMPULSE_TICKS = 40;

  private static final EntityDataAccessor<Integer> DATA_DEBUG_IMPULSE_AGE =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_HIT_X =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_HIT_Y =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_HIT_Z =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_FORCE_X =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_FORCE_Y =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_DEBUG_FORCE_Z =
      SynchedEntityData.defineId(EntityRigidBodyTorque.class, EntityDataSerializers.FLOAT);

  private Vec3 angularVelocity = Vec3.ZERO;
  private int lastImpulseTick = -1;

  public EntityRigidBodyTorque(EntityType<? extends EntityRigidBodyTorque> type, Level level) {
    super(type, level);
  }

  public EntityRigidBodyTorque(Level level, Vec3 position) {
    super(PhysicsTorchesEntities.RIGID_BODY_TORQUE, level, position, null);
  }

  public boolean hasDebugImpulse() {
    return entityData.get(DATA_DEBUG_IMPULSE_AGE) > 0;
  }

  public Vec3 getDebugHitPoint() {
    return new Vec3(
        entityData.get(DATA_DEBUG_HIT_X),
        entityData.get(DATA_DEBUG_HIT_Y),
        entityData.get(DATA_DEBUG_HIT_Z));
  }

  public Vec3 getDebugForce() {
    return new Vec3(
        entityData.get(DATA_DEBUG_FORCE_X),
        entityData.get(DATA_DEBUG_FORCE_Y),
        entityData.get(DATA_DEBUG_FORCE_Z));
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_DEBUG_IMPULSE_AGE, 0);
    builder.define(DATA_DEBUG_HIT_X, 0.0F);
    builder.define(DATA_DEBUG_HIT_Y, 0.0F);
    builder.define(DATA_DEBUG_HIT_Z, 0.0F);
    builder.define(DATA_DEBUG_FORCE_X, 0.0F);
    builder.define(DATA_DEBUG_FORCE_Y, 0.0F);
    builder.define(DATA_DEBUG_FORCE_Z, 0.0F);
  }

  @Override
  public void tick() {
    super.tick();

    if (!level().isClientSide()) {
      int age = entityData.get(DATA_DEBUG_IMPULSE_AGE);
      if (age > 0) {
        entityData.set(DATA_DEBUG_IMPULSE_AGE, age - 1);
      }
    }
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

  @Override
  protected void applyPhysics() {
    if (angularVelocity.lengthSqr() <= 1.0E-8D) {
      return;
    }

    double speed = angularVelocity.length();
    Vec3 axis = angularVelocity.scale(1.0D / speed);
    rotateAroundWorldAxis(axis, (float) speed);
    angularVelocity = angularVelocity.scale(ANGULAR_DAMPING);
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
    Vec3 force = surfaceHit.inwardNormal().scale(IMPULSE_STRENGTH);
    Vec3 leverArm = hitPoint.subtract(getCollisionCenter());
    angularVelocity = angularVelocity.add(leverArm.cross(force).scale(TORQUE_SCALE));
    lastImpulseTick = tickCount;
    recordDebugImpulse(hitPoint, force);
    return true;
  }

  private void recordDebugImpulse(Vec3 hitPoint, Vec3 force) {
    entityData.set(DATA_DEBUG_HIT_X, (float) hitPoint.x);
    entityData.set(DATA_DEBUG_HIT_Y, (float) hitPoint.y);
    entityData.set(DATA_DEBUG_HIT_Z, (float) hitPoint.z);
    entityData.set(DATA_DEBUG_FORCE_X, (float) force.x);
    entityData.set(DATA_DEBUG_FORCE_Y, (float) force.y);
    entityData.set(DATA_DEBUG_FORCE_Z, (float) force.z);
    entityData.set(DATA_DEBUG_IMPULSE_AGE, DEBUG_IMPULSE_TICKS);
  }
}
