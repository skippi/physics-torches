package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;

public class EntityRigidBody extends Entity {
  public static final double HALF_SIZE = 0.5D;
  public static final double TARGET_REACH = 6.0D;
  public static final double SURFACE_TOLERANCE = 0.05D;
  public static final double PICK_BBOX_INFLATE = 0.03D;

  public record PlayerLookRay(Vec3 origin, Vec3 direction) {
    public static PlayerLookRay from(Player player, float partialTick) {
      return new PlayerLookRay(player.getEyePosition(partialTick), player.getViewVector(partialTick));
    }

    public Vec3 normalizedDirection() {
      return direction.lengthSqr() > 1.0E-6D ? direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    public boolean isValid() {
      return direction.lengthSqr() >= 1.0E-8D;
    }
  }

  private static final RigidBodyCollisionModel COLLISION = RigidBodyCollisionModel.cube(HALF_SIZE);
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
  private SpinAxis[] spinAxes = new SpinAxis[0];

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
    this.spinAxes = spinAxes;
    if (initialOrientation != null) {
      orientation.set(initialOrientation);
      prevOrientation.set(initialOrientation);
    }
    setPos(position.x, position.y, position.z);
    syncOrientationData();
  }

  public EntityRigidBody(
      EntityType<? extends EntityRigidBody> type,
      Level level,
      Vec3 position,
      Quaternionf initialOrientation,
      SpinAxis... spinAxes) {
    this(type, level);
    this.spinAxes = spinAxes;
    if (initialOrientation != null) {
      orientation.set(initialOrientation);
      prevOrientation.set(initialOrientation);
    }
    setPos(position.x, position.y, position.z);
    syncOrientationData();
  }

  public Vec3 getCollisionCenter() {
    return new Vec3(getX(), getY() + HALF_SIZE, getZ());
  }

  public RigidBodyCollisionModel collisionModel() {
    return COLLISION;
  }

  public Optional<RigidBodyCollisionModel.SurfaceHit> raycastSurface(
      Vec3 rayOrigin, Vec3 rayDirection, float partialTick, double maxReach) {
    Optional<RigidBodyCollisionModel.SurfaceHit> hit =
        raycastSurface(rayOrigin, rayDirection, partialTick, maxReach, 0.0D);
    if (hit.isEmpty() && SURFACE_TOLERANCE > 0.0D) {
      hit = raycastSurface(rayOrigin, rayDirection, partialTick, maxReach, SURFACE_TOLERANCE);
    }
    return hit;
  }

  public Optional<RigidBodyCollisionModel.SurfaceHit> raycastSurface(
      Vec3 rayOrigin,
      Vec3 rayDirection,
      float partialTick,
      double maxReach,
      double surfaceTolerance) {
    return COLLISION.raycast(
        getOrientation(partialTick),
        getCollisionCenter(),
        rayOrigin,
        rayDirection,
        maxReach,
        surfaceTolerance);
  }

  public static Optional<RigidBodyCollisionModel.SurfaceHit> raycastClosest(
      Level level,
      AABB searchBox,
      Vec3 rayOrigin,
      Vec3 rayDirection,
      float partialTick,
      double maxReach) {
    RigidBodyCollisionModel.SurfaceHit closestHit = null;
    for (EntityRigidBody body : level.getEntitiesOfClass(EntityRigidBody.class, searchBox)) {
      Optional<RigidBodyCollisionModel.SurfaceHit> hit =
          body.raycastSurface(rayOrigin, rayDirection, partialTick, maxReach);
      if (hit.isEmpty()) {
        continue;
      }

      if (closestHit == null
          || hit.get().worldPoint().distanceToSqr(rayOrigin)
              < closestHit.worldPoint().distanceToSqr(rayOrigin)) {
        closestHit = hit.get();
      }
    }

    return Optional.ofNullable(closestHit);
  }

  public static Optional<RigidBodyCollisionModel.SurfaceHit> raycastClosest(
      Level level, AABB searchBox, PlayerLookRay ray, float partialTick, double maxReach) {
    return raycastClosest(level, searchBox, ray.origin(), ray.direction(), partialTick, maxReach);
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
      applyConstantSpin();
      applyPhysics();
      syncOrientationData();
    }

    refreshOrientedBoundingBox();
    super.tick();
  }

  private void refreshOrientedBoundingBox() {
    setBoundingBox(
        COLLISION.orientedBounds(getCollisionCenter(), getOrientation(1.0F), PICK_BBOX_INFLATE));
  }

  protected void applyConstantSpin() {
    float angle = (float) (SPIN_SPEED * TICK_DT);
    for (SpinAxis spinAxis : spinAxes) {
      spinAxis.rotate(orientation, angle);
    }
  }

  protected void applyPhysics() {}

  protected void rotateAroundWorldAxis(Vec3 axis, float angle) {
    orientation.rotateAxis(angle, (float) axis.x, (float) axis.y, (float) axis.z);
  }

  protected void syncOrientationData() {
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
