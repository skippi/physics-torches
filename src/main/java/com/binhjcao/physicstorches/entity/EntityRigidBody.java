package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.BoxCollider;
import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.ModEntityTypes;
import com.binhjcao.physicstorches.Physics;
import com.binhjcao.physicstorches.RaycastHit;
import com.binhjcao.physicstorches.RigidBody;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EntityRigidBody extends Entity {
  public static final double HALF_SIZE = 0.5D;
  public static final double TARGET_REACH = 6.0D;
  public static final double PICK_BBOX_INFLATE = 0.03D;
  private static final double SUPPORT_SURFACE_EPS = 0.06D;
  private static final double STABLE_COM_EPS = 0.05D;

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

  public double mass() {
    return rigidBody.mass();
  }

  public void mass(double mass) {
    rigidBody.mass(mass);
  }

  public double linearDamp() {
    return rigidBody.linearDamp();
  }

  public void linearDamp(double linearDamp) {
    rigidBody.linearDamp(linearDamp);
  }

  public double angularDamp() {
    return rigidBody.angularDamp();
  }

  public void angularDamp(double angularDamp) {
    rigidBody.angularDamp(angularDamp);
  }

  public Vec3 angularVelocity() {
    return rigidBody.angularVelocity();
  }

  public void angularVelocity(Vec3 angularVelocity) {
    rigidBody.angularVelocity(angularVelocity);
  }

  public Vec3 constantForce() {
    return rigidBody.constantForce();
  }

  public void constantForce(Vec3 constantForce) {
    rigidBody.constantForce(constantForce);
  }

  public Vec3 constantTorque() {
    return rigidBody.constantTorque();
  }

  public void constantTorque(Vec3 constantTorque) {
    rigidBody.constantTorque(constantTorque);
  }

  public double gravityScale() {
    return rigidBody.gravityScale();
  }

  public void gravityScale(double gravityScale) {
    rigidBody.gravityScale(gravityScale);
  }

  public double bounce() {
    return rigidBody.bounce();
  }

  public void bounce(double bounce) {
    rigidBody.bounce(bounce);
  }

  public double friction() {
    return rigidBody.friction();
  }

  public void friction(double friction) {
    rigidBody.friction(friction);
  }

  public Vec3 linearVelocity() {
    return rigidBody.linearVelocity();
  }

  public void linearVelocity(Vec3 linearVelocity) {
    rigidBody.linearVelocity(linearVelocity);
  }

  public Vec3 inertia() {
    return rigidBody.inertia();
  }

  public boolean linearLock() {
    return rigidBody.linearLock();
  }

  public void linearLock(boolean lock) {
    rigidBody.linearLock(lock);
  }

  public boolean inputRayPickable() {
    return inputRayPickable;
  }

  public void inputRayPickable(boolean inputRayPickable) {
    this.inputRayPickable = inputRayPickable;
  }

  public boolean isSleeping() {
    return rigidBody.isSleeping();
  }

  public Collider collider() {
    return rigidBody.collider();
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
    mass(input.getDoubleOr("mass", mass()));
    linearDamp(input.getDoubleOr("linear_damp", linearDamp()));
    angularDamp(input.getDoubleOr("angular_damp", angularDamp()));
    gravityScale(input.getDoubleOr("gravity_scale", gravityScale()));
    bounce(input.getDoubleOr("bounce", bounce()));
    friction(input.getDoubleOr("friction", friction()));
    input.read("angular_velocity", Vec3.CODEC).ifPresent(this::angularVelocity);
    input.read("linear_velocity", Vec3.CODEC).ifPresent(this::linearVelocity);
    input.read("constant_force", Vec3.CODEC).ifPresent(this::constantForce);
    input.read("constant_torque", Vec3.CODEC).ifPresent(this::constantTorque);
    var orientation = rigidBody.orientation();
    orientation.set(
        input.getFloatOr("orient_x", orientation.x),
        input.getFloatOr("orient_y", orientation.y),
        input.getFloatOr("orient_z", orientation.z),
        input.getFloatOr("orient_w", orientation.w));
    orientation.normalize();
    rigidBody.prevOrientation().set(orientation);
    if (input.getBooleanOr("sleeping", isSleeping())) {
      enterSleep();
    } else {
      wake();
    }
    linearLock(input.getBooleanOr("linear_lock", linearLock()));
    inputRayPickable = input.getBooleanOr("input_ray_pickable", inputRayPickable);
    syncToRigidBody();
    syncOrientationData();
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.putDouble("mass", mass());
    output.putDouble("linear_damp", linearDamp());
    output.putDouble("angular_damp", angularDamp());
    output.putDouble("gravity_scale", gravityScale());
    output.putDouble("bounce", bounce());
    output.putDouble("friction", friction());
    output.store("angular_velocity", Vec3.CODEC, angularVelocity());
    output.store("linear_velocity", Vec3.CODEC, linearVelocity());
    output.store("constant_force", Vec3.CODEC, constantForce());
    output.store("constant_torque", Vec3.CODEC, constantTorque());
    var orientation = rigidBody.orientation();
    output.putFloat("orient_x", orientation.x);
    output.putFloat("orient_y", orientation.y);
    output.putFloat("orient_z", orientation.z);
    output.putFloat("orient_w", orientation.w);
    output.putBoolean("sleeping", isSleeping());
    output.putBoolean("linear_lock", linearLock());
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
        collider().orientedBounds(collisionCenter(), getOrientation(1.0F), PICK_BBOX_INFLATE));
  }

  public void applyForce(Vec3 force) {
    applyForce(force, Vec3.ZERO);
  }

  public void applyForce(Vec3 force, Vec3 position) {
    wake();
    rigidBody.applyImpulse(force.scale(physicsDt()), position);
  }

  public void applyImpulse(Vec3 impulse) {
    applyImpulse(impulse, Vec3.ZERO);
  }

  public void applyImpulse(Vec3 impulse, Vec3 position) {
    wake();
    rigidBody.applyImpulse(impulse, position);
  }

  public void applyTorque(Vec3 torque) {
    wake();
    rigidBody.applyTorque(torque, physicsDt());
  }

  public void applyTorqueImpulse(Vec3 impulse) {
    wake();
    rigidBody.applyAngularImpulse(impulse);
  }

  public double physicsDt() {
    return 1.0D / level().tickRateManager().tickrate();
  }

  protected void wake() {
    rigidBody.wake();
  }

  public void enterSleep() {
    rigidBody.enterSleep();
  }

  protected List<AABB> collectBlockAABBs(AABB searchBounds) {
    List<AABB> blocks = new ArrayList<>();
    for (VoxelShape shape : level().getBlockCollisions(this, searchBounds)) {
      blocks.addAll(shape.toAabbs());
    }
    return blocks;
  }

  protected int minimumStableSupportPoints() {
    return 3;
  }

  protected List<Vec3> findGroundSupportPoints() {
    Vec3 center = collisionCenter();
    Vec3[] corners = collider().worldCorners(center, rigidBody.orientation());
    List<Vec3> supportPoints = new ArrayList<>();
    for (Vec3 corner : corners) {
      if (isCornerSupported(corner)) {
        supportPoints.add(corner);
      }
    }
    return supportPoints;
  }

  private boolean isCornerSupported(Vec3 corner) {
    AABB probe =
        new AABB(
            corner.x - 0.01D,
            corner.y - 0.15D,
            corner.z - 0.01D,
            corner.x + 0.01D,
            corner.y + 0.05D,
            corner.z + 0.01D);
    for (AABB block : collectBlockAABBs(probe)) {
      if (Math.abs(corner.y - block.maxY) > SUPPORT_SURFACE_EPS) {
        continue;
      }
      if (corner.x >= block.minX - 1.0E-4D
          && corner.x <= block.maxX + 1.0E-4D
          && corner.z >= block.minZ - 1.0E-4D
          && corner.z <= block.maxZ + 1.0E-4D) {
        return true;
      }
    }
    return false;
  }

  public Vec3 toBodyDirection(Vec3 worldDirection) {
    return rigidBody.toBodyDirection(worldDirection);
  }

  public Vec3 toWorldDirection(Vec3 bodyDirection) {
    return rigidBody.toWorldDirection(bodyDirection);
  }

  protected void syncOrientationData() {
    var orientation = rigidBody.orientation();
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
