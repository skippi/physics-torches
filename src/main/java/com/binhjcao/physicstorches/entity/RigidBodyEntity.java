package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.physics.RigidBodyCollider;
import com.binhjcao.physicstorches.physics.RigidBodyPhysics;
import com.binhjcao.physicstorches.physics.RigidBodyState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public abstract class RigidBodyEntity extends Entity {
  private static final EntityDataAccessor<Float> DATA_ORIENT_X =
      SynchedEntityData.defineId(RigidBodyEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Y =
      SynchedEntityData.defineId(RigidBodyEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Z =
      SynchedEntityData.defineId(RigidBodyEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_W =
      SynchedEntityData.defineId(RigidBodyEntity.class, EntityDataSerializers.FLOAT);

  protected final RigidBodyState body = new RigidBodyState();
  protected RigidBodyCollider collider;
  private int sleepTicks;

  protected RigidBodyEntity(EntityType<? extends Entity> type, Level level) {
    super(type, level);
    setNoGravity(true);
    collider = createCollider();
    configureInertia(body);
    body.orientation.identity();
    body.prevOrientation.identity();
  }

  protected abstract RigidBodyCollider createCollider();

  protected abstract void configureInertia(RigidBodyState body);

  public RigidBodyState body() {
    return body;
  }

  public Quaternionf getOrientation(float partialTick) {
    return body.orientation(partialTick);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_ORIENT_X, 0.0F);
    builder.define(DATA_ORIENT_Y, 0.0F);
    builder.define(DATA_ORIENT_Z, 0.0F);
    builder.define(DATA_ORIENT_W, 1.0F);
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    input.read("OrientX", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.orientation.x = v);
    input.read("OrientY", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.orientation.y = v);
    input.read("OrientZ", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.orientation.z = v);
    input.read("OrientW", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.orientation.w = v);
    input.read("VelX", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.vx = v);
    input.read("VelY", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.vy = v);
    input.read("VelZ", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.vz = v);
    input.read("AngVelX", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.wx = v);
    input.read("AngVelY", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.wy = v);
    input.read("AngVelZ", com.mojang.serialization.Codec.FLOAT).ifPresent(v -> body.wz = v);
    body.prevOrientation.set(body.orientation);
    applyBodyToEntity();
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.store("OrientX", com.mojang.serialization.Codec.FLOAT, body.orientation.x);
    output.store("OrientY", com.mojang.serialization.Codec.FLOAT, body.orientation.y);
    output.store("OrientZ", com.mojang.serialization.Codec.FLOAT, body.orientation.z);
    output.store("OrientW", com.mojang.serialization.Codec.FLOAT, body.orientation.w);
    output.store("VelX", com.mojang.serialization.Codec.FLOAT, (float) body.vx);
    output.store("VelY", com.mojang.serialization.Codec.FLOAT, (float) body.vy);
    output.store("VelZ", com.mojang.serialization.Codec.FLOAT, (float) body.vz);
    output.store("AngVelX", com.mojang.serialization.Codec.FLOAT, (float) body.wx);
    output.store("AngVelY", com.mojang.serialization.Codec.FLOAT, (float) body.wy);
    output.store("AngVelZ", com.mojang.serialization.Codec.FLOAT, (float) body.wz);
  }

  protected void initBodyFromSpawn(Vec3 position, Vec3 velocity) {
    body.setPosition(position.x, position.y, position.z);
    body.setLinearVelocity(velocity.x, velocity.y, velocity.z);
    body.setAngularVelocity(0.0D, 0.0D, 0.0D);
    body.orientation.identity();
    body.wake();
    applyBodyToEntity();
    syncOrientationData();
  }

  @Override
  public void tick() {
    body.snapshotOrientation();

    if (level().isClientSide()) {
      readOrientationFromData();
      applyBodyToEntity();
      super.tick();
      return;
    }

    if (isInWater()) {
      body.vx *= 0.8D;
      body.vy *= 0.8D;
      body.vz *= 0.8D;
    }

    if (body.sleeping) {
      sleepTicks++;
      applyBodyToEntity();
      syncOrientationData();
      super.tick();
      return;
    }

    sleepTicks = 0;
    RigidBodyPhysics.step(level(), this, body, collider);
    applyBodyToEntity();
    syncOrientationData();
    super.tick();
  }

  protected void applyBodyToEntity() {
    setPos(body.px, body.py, body.pz);
    setDeltaMovement(new Vec3(body.vx, body.vy, body.vz));
    if (collider != null) {
      setBoundingBox(collider.worldBounds(body));
    }
    setOnGround(body.onGround);
  }

  private void syncOrientationData() {
    entityData.set(DATA_ORIENT_X, body.orientation.x);
    entityData.set(DATA_ORIENT_Y, body.orientation.y);
    entityData.set(DATA_ORIENT_Z, body.orientation.z);
    entityData.set(DATA_ORIENT_W, body.orientation.w);
  }

  private void readOrientationFromData() {
    body.orientation.set(
        entityData.get(DATA_ORIENT_X),
        entityData.get(DATA_ORIENT_Y),
        entityData.get(DATA_ORIENT_Z),
        entityData.get(DATA_ORIENT_W));
  }

  @Override
  protected AABB makeBoundingBox(Vec3 position) {
    if (collider == null) {
      EntityDimensions dimensions = getType().getDimensions();
      double halfWidth = dimensions.width() * 0.5D;
      double height = dimensions.height();
      return new AABB(
          position.x - halfWidth,
          position.y,
          position.z - halfWidth,
          position.x + halfWidth,
          position.y + height,
          position.z + halfWidth);
    }

    body.setPosition(position.x, position.y, position.z);
    return collider.worldBounds(body);
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean canBeCollidedWith(@Nullable Entity other) {
    return true;
  }

  @Override
  public boolean canCollideWith(Entity entity) {
    return !isPassengerOfSameVehicle(entity);
  }

  @Override
  public void push(Entity entity) {
    if (!level().isClientSide()) {
      body.wake();
    }
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }
}
