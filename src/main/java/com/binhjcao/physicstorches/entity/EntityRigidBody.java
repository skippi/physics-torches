package com.binhjcao.physicstorches.entity;

import java.util.Optional;

import org.joml.Quaternionf;

import com.binhjcao.physicstorches.BoxCollider;
import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.ModEntityTypes;
import com.binhjcao.physicstorches.RaycastHit;
import com.binhjcao.physicstorches.physics.Physics;
import com.binhjcao.physicstorches.physics.RigidBody;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class EntityRigidBody extends Entity {
  public static final double HALF_SIZE = 0.5D;
  public static final double TARGET_REACH = 6.0D;
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

  private final RigidBody rigidBody;
  private static final EntityDataAccessor<Float> DATA_ORIENT_X =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Y =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_Z =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_ORIENT_W =
      SynchedEntityData.defineId(EntityRigidBody.class, EntityDataSerializers.FLOAT);

  private final InterpolationHandler interpolation = new InterpolationHandler(this);

  private boolean inputRayPickable = true;
  private int lastSurfaceInputTick = -1;

  public EntityRigidBody(EntityType<? extends EntityRigidBody> type, Level level) {
    super(type, level);
    rigidBody = createRigidBody();
    setNoGravity(true);
    syncOrientationData();
  }

  protected RigidBody createRigidBody() {
    return new RigidBody(createCollider());
  }

  public RigidBody rigidBody() {
    return rigidBody;
  }

  protected Collider createCollider() {
    return BoxCollider.cube(HALF_SIZE);
  }

  protected Vec3 collisionCenter() {
    return rigidBody.position();
  }

  protected void setCollisionCenter(Vec3 center) {
    rigidBody.position(center);
    setPos(center.x, center.y, center.z);
  }

  public EntityRigidBody(Level level, Vec3 position) {
    this(level, position, null);
  }

  public EntityRigidBody(Level level, Vec3 position, Quaternionf initialOrientation) {
    this(ModEntityTypes.RIGID_BODY_CUBE, level, position, initialOrientation);
  }

  public EntityRigidBody(
      EntityType<? extends EntityRigidBody> type,
      Level level,
      Vec3 position,
      Quaternionf initialOrientation) {
    this(type, level);
    if (initialOrientation != null) {
      rigidBody.orientation().set(initialOrientation);
      rigidBody.prevOrientation().set(initialOrientation);
    }
    setCollisionCenter(position);
    syncOrientationData();
  }

  public void syncToRigidBody() {
    rigidBody.position(new Vec3(getX(), getY(), getZ()));
  }

  public void syncFromRigidBody() {
    syncEntityFromRigidBody();
  }

  public boolean inputRayPickable() {
    return inputRayPickable;
  }

  public void inputRayPickable(boolean inputRayPickable) {
    this.inputRayPickable = inputRayPickable;
  }

  public Quaternionf getOrientation(float partialTick) {
    return rigidBody.getOrientation(partialTick);
  }

  @Override
  public InterpolationHandler getInterpolation() {
    return interpolation;
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
    rigidBody.mass(input.getDoubleOr("mass", rigidBody.mass()));
    rigidBody.linearDamp(input.getDoubleOr("linear_damp", rigidBody.linearDamp()));
    rigidBody.angularDamp(input.getDoubleOr("angular_damp", rigidBody.angularDamp()));
    rigidBody.gravityScale(input.getDoubleOr("gravity_scale", rigidBody.gravityScale()));
    rigidBody.bounce(input.getDoubleOr("bounce", rigidBody.bounce()));
    rigidBody.friction(input.getDoubleOr("friction", rigidBody.friction()));
    input.read("angular_velocity", Vec3.CODEC).ifPresent(rigidBody::angularVelocity);
    input.read("linear_velocity", Vec3.CODEC).ifPresent(rigidBody::linearVelocity);
    var orientation = rigidBody.orientation();
    orientation.set(
        input.getFloatOr("orient_x", orientation.x),
        input.getFloatOr("orient_y", orientation.y),
        input.getFloatOr("orient_z", orientation.z),
        input.getFloatOr("orient_w", orientation.w));
    orientation.normalize();
    rigidBody.prevOrientation().set(orientation);
    if (input.getBooleanOr("sleeping", rigidBody.isSleeping())) {
      enterSleep();
    } else {
      wake();
    }
    rigidBody.linearLock(input.getBooleanOr("linear_lock", rigidBody.linearLock()));
    inputRayPickable = input.getBooleanOr("input_ray_pickable", inputRayPickable);
    syncToRigidBody();
    syncOrientationData();
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.putDouble("mass", rigidBody.mass());
    output.putDouble("linear_damp", rigidBody.linearDamp());
    output.putDouble("angular_damp", rigidBody.angularDamp());
    output.putDouble("gravity_scale", rigidBody.gravityScale());
    output.putDouble("bounce", rigidBody.bounce());
    output.putDouble("friction", rigidBody.friction());
    output.store("angular_velocity", Vec3.CODEC, rigidBody.angularVelocity());
    output.store("linear_velocity", Vec3.CODEC, rigidBody.linearVelocity());
    var orientation = rigidBody.orientation();
    output.putFloat("orient_x", orientation.x);
    output.putFloat("orient_y", orientation.y);
    output.putFloat("orient_z", orientation.z);
    output.putFloat("orient_w", orientation.w);
    output.putBoolean("sleeping", rigidBody.isSleeping());
    output.putBoolean("linear_lock", rigidBody.linearLock());
    output.putBoolean("input_ray_pickable", inputRayPickable);
  }

  @Override
  public boolean isPickable() {
    return inputRayPickable;
  }

  @Override
  public boolean isAttackable() {
    return inputRayPickable;
  }

  @Override
  public boolean skipAttackInteraction(Entity source) {
    if (!(source instanceof Player player) || !inputRayPickable) {
      return false;
    }

    if (!level().isClientSide()) {
      handlePlayerSurfaceInput(player);
    }

    return true;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }

  protected boolean handlePlayerSurfaceInput(Player player) {
    if (!inputRayPickable || level().isClientSide()) {
      return false;
    }

    if (tickCount == lastSurfaceInputTick) {
      return true;
    }

    PlayerLookRay ray = PlayerLookRay.from(player, 1.0F);
    if (!ray.isValid()) {
      return false;
    }

    Optional<RaycastHit> hit =
        Physics.raycast(
            level(),
            getBoundingBox(),
            ray.origin(),
            ray.direction(),
            1.0F,
            TARGET_REACH,
            body -> body == this);
    if (hit.isEmpty()) {
      return false;
    }

    RaycastHit surfaceHit = hit.get();
    onSurfaceInput(player, surfaceHit.point(), surfaceHit.normal());
    lastSurfaceInputTick = tickCount;
    return true;
  }

  protected void onSurfaceInput(Player player, Vec3 surfacePosition, Vec3 surfaceNormal) {}

  @Override
  public void tick() {
    interpolation.interpolate();

    if (level().isClientSide()) {
      syncRigidBodyFromEntity();
    }

    syncEntityFromRigidBody();
    super.tick();
  }

  private void syncRigidBodyFromEntity() {
    rigidBody.snapshotPrevOrientation();
    rigidBody.position(new Vec3(getX(), getY(), getZ()));
    var orientation = rigidBody.orientation();
    orientation.set(
        entityData.get(DATA_ORIENT_X),
        entityData.get(DATA_ORIENT_Y),
        entityData.get(DATA_ORIENT_Z),
        entityData.get(DATA_ORIENT_W));
    orientation.normalize();
  }

  private void syncEntityFromRigidBody() {
    Vec3 position = rigidBody.position();
    setPos(position.x, position.y, position.z);
    if (!level().isClientSide()) {
      syncOrientationData();
    }
    refreshOrientedBoundingBox();
  }

  private void refreshOrientedBoundingBox() {
    setBoundingBox(
        rigidBody
            .collider()
            .orientedBounds(collisionCenter(), getOrientation(1.0F), PICK_BBOX_INFLATE));
  }

  protected void wake() {
    rigidBody.wake();
  }

  public void enterSleep() {
    rigidBody.enterSleep();
  }

  protected void syncOrientationData() {
    var orientation = rigidBody.orientation();
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
