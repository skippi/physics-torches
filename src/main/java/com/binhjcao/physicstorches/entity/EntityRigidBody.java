package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class EntityRigidBody extends Entity {
  public static final double HALF_SIZE = 0.5D;
  private static final double SPIN_SPEED = 2.5D;
  private static final float TICK_DT = 0.05F;

  public enum SpinAxis {
    X,
    Y,
    Z;

    void rotate(Quaternionf orientation, float angle) {
      switch (this) {
        case X -> orientation.rotateX(angle);
        case Y -> orientation.rotateY(angle);
        case Z -> orientation.rotateZ(angle);
      }
    }
  }

  private static final EntityDataAccessor<Float> DATA_ORIENT_X =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Y =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Z =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_W =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);

  private final Quaternionf orientation = new Quaternionf();
  private final Quaternionf prevOrientation = new Quaternionf();
  private SpinAxis[] spinAxes = new SpinAxis[] {SpinAxis.Y};

  public EntityRigidBody(EntityType<? extends EntityRigidBody> type, Level level) {
    super(type, level);
    setNoGravity(true);
    orientation.identity();
    prevOrientation.identity();
  }

  public EntityRigidBody(Level level, Vec3 position, SpinAxis... spinAxes) {
    this(level, position, null, spinAxes);
  }

  public EntityRigidBody(
      Level level, Vec3 position, Quaternionf initialOrientation, SpinAxis... spinAxes) {
    this(PhysicsTorchesEntities.RIGID_BODY_CUBE, level);
    if (spinAxes.length > 0) {
      this.spinAxes = spinAxes;
    }
    if (initialOrientation != null) {
      orientation.set(initialOrientation);
      prevOrientation.set(initialOrientation);
    }
    setPos(position.x, position.y, position.z);
    syncOrientationData();
  }

  public Quaternionf getOrientation(float partialTick) {
    if (partialTick >= 1.0F) {
      return new Quaternionf(orientation);
    }
    return new Quaternionf(prevOrientation).slerp(orientation, partialTick);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_ORIENT_X, 0.0F);
    builder.define(DATA_ORIENT_Y, 0.0F);
    builder.define(DATA_ORIENT_Z, 0.0F);
    builder.define(DATA_ORIENT_W, 1.0F);
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {}

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {}

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }

  @Override
  public void tick() {
    prevOrientation.set(orientation);

    if (level().isClientSide()) {
      orientation.set(
          entityData.get(DATA_ORIENT_X),
          entityData.get(DATA_ORIENT_Y),
          entityData.get(DATA_ORIENT_Z),
          entityData.get(DATA_ORIENT_W));
    } else {
      float angle = (float) (SPIN_SPEED * TICK_DT);
      for (SpinAxis spinAxis : spinAxes) {
        spinAxis.rotate(orientation, angle);
      }
      syncOrientationData();
    }

    super.tick();
  }

  private void syncOrientationData() {
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
