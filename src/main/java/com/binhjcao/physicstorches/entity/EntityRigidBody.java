package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EntityRigidBody extends Entity {
  public static final double HALF_SIZE = 0.5D;
  public static final double TARGET_REACH = 6.0D;
  public static final double SURFACE_TOLERANCE = 0.05D;
  public static final double PICK_BBOX_INFLATE = 0.03D;
  private static final double SUPPORT_SURFACE_EPS = 0.06D;
  private static final double STABLE_COM_EPS = 0.05D;
  private static final int CAST_BINARY_STEPS = 16;
  private static final int RECOVERY_ITERATIONS = 8;

  public record MoveAndCollideResult(
      Vec3 position,
      Vec3 normal,
      Vec3 travel,
      Vec3 remainder,
      AABB collider,
      double depth,
      boolean recovered,
      Vec3 leverArm) {}

  private record CastHit(double fraction, Vec3 normal, double depth, AABB block, Vec3 contactPoint) {}

  private record SATResult(Vec3 normal, double depth) {}

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

  private final RigidBodyCollisionModel collisionModel;
  public static final double GRAVITY = 9.81D; // m/s²
  private static final double BLOCK_FRICTION = 1.0D;

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
  private final InterpolationHandler interpolation = new InterpolationHandler(this);

  private double mass = 1.0D;
  private double linearDamp = 0.0D;
  private double angularDamp = 0.0D;
  private double gravityScale = 1.0D;
  private double bounce = 0.0D;
  private double friction = 0.0D;
  private double contactMaxAllowedPenetration = 0.01D;
  private double penetrationSlop = 0.02D;
  private double sleepThreshold = 0.03D;
  private double timeBeforeSleep = 0.5D;
  private int belowSleepThresholdTick = -1;
  private Vec3 angularVelocity = Vec3.ZERO;
  private Vec3 linearVelocity = Vec3.ZERO;
  private Vec3 constantForce = Vec3.ZERO;
  private Vec3 constantTorque = Vec3.ZERO;
  private boolean linearLock = false;
  private boolean inputRayPickable = true;
  private boolean sleeping = false;
  private int lastSurfaceInputTick = -1;

  public EntityRigidBody(EntityType<? extends EntityRigidBody> type, Level level) {
    super(type, level);
    collisionModel = createCollisionModel();
    setNoGravity(true);
    orientation.identity();
    prevOrientation.identity();
  }

  protected RigidBodyCollisionModel createCollisionModel() {
    return RigidBodyCollisionModel.cube(HALF_SIZE);
  }

  protected Vec3 collisionCenter() {
    return position();
  }

  protected void setCollisionCenter(Vec3 center) {
    setPos(center);
  }

  public EntityRigidBody(Level level, Vec3 position) {
    this(level, position, null);
  }

  public EntityRigidBody(Level level, Vec3 position, Quaternionf initialOrientation) {
    this(PhysicsTorchesEntities.RIGID_BODY_CUBE, level, position, initialOrientation);
  }

  public EntityRigidBody(
      EntityType<? extends EntityRigidBody> type,
      Level level,
      Vec3 position,
      Quaternionf initialOrientation) {
    this(type, level);
    if (initialOrientation != null) {
      orientation.set(initialOrientation);
      prevOrientation.set(initialOrientation);
    }
    setPos(position.x, position.y, position.z);
    syncOrientationData();
  }

  public double mass() {
    return mass;
  }

  public void mass(double mass) {
    this.mass = Math.max(1.0E-4D, mass);
  }

  public double linearDamp() {
    return linearDamp;
  }

  public void linearDamp(double linearDamp) {
    this.linearDamp = Math.clamp(linearDamp, 0.0D, 1.0D);
  }

  public double angularDamp() {
    return angularDamp;
  }

  public void angularDamp(double angularDamp) {
    this.angularDamp = Math.clamp(angularDamp, 0.0D, 1.0D);
  }

  public Vec3 angularVelocity() {
    return angularVelocity;
  }

  public void angularVelocity(Vec3 angularVelocity) {
    this.angularVelocity = angularVelocity;
  }

  public Vec3 constantForce() {
    return constantForce;
  }

  public void constantForce(Vec3 constantForce) {
    this.constantForce = constantForce;
  }

  public Vec3 constantTorque() {
    return constantTorque;
  }

  public void constantTorque(Vec3 constantTorque) {
    this.constantTorque = constantTorque;
  }

  public double gravityScale() {
    return gravityScale;
  }

  public void gravityScale(double gravityScale) {
    this.gravityScale = Math.clamp(gravityScale, 0.0D, 1.0D);
  }

  public double bounce() {
    return bounce;
  }

  public void bounce(double bounce) {
    this.bounce = Math.clamp(bounce, 0.0D, 1.0D);
  }

  public double friction() {
    return friction;
  }

  public void friction(double friction) {
    this.friction = Math.clamp(friction, 0.0D, 1.0D);
  }

  public double contactMaxAllowedPenetration() {
    return contactMaxAllowedPenetration;
  }

  public void contactMaxAllowedPenetration(double contactMaxAllowedPenetration) {
    this.contactMaxAllowedPenetration = Math.max(0.0D, contactMaxAllowedPenetration);
  }

  public double penetrationSlop() {
    return penetrationSlop;
  }

  public void penetrationSlop(double penetrationSlop) {
    this.penetrationSlop = Math.max(0.0D, penetrationSlop);
  }

  public Vec3 linearVelocity() {
    return linearVelocity;
  }

  public void linearVelocity(Vec3 linearVelocity) {
    this.linearVelocity = linearVelocity;
  }

  public double sleepThreshold() {
    return sleepThreshold;
  }

  public void sleepThreshold(double sleepThreshold) {
    this.sleepThreshold = Math.max(0.0D, sleepThreshold);
  }

  public double timeBeforeSleep() {
    return timeBeforeSleep;
  }

  public void timeBeforeSleep(double timeBeforeSleep) {
    this.timeBeforeSleep = Math.max(0.0D, timeBeforeSleep);
  }

  public Vec3 inertia() {
    Vec3 halfExtents = collisionModel.halfExtents();
    return boxInertia(mass(), halfExtents.x, halfExtents.y, halfExtents.z);
  }

  public boolean linearLock() {
    return linearLock;
  }

  public void linearLock(boolean lock) {
    this.linearLock = lock;
  }

  public boolean inputRayPickable() {
    return inputRayPickable;
  }

  public void inputRayPickable(boolean inputRayPickable) {
    this.inputRayPickable = inputRayPickable;
  }

  protected boolean sleeping() {
    return sleeping;
  }

  public RigidBodyCollisionModel collisionModel() {
    return collisionModel;
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
    return collisionModel.raycast(
        getOrientation(partialTick),
        collisionCenter(),
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
    mass(input.getDoubleOr("mass", mass));
    linearDamp(input.getDoubleOr("linear_damp", linearDamp));
    angularDamp(input.getDoubleOr("angular_damp", angularDamp));
    gravityScale(input.getDoubleOr("gravity_scale", gravityScale));
    bounce(input.getDoubleOr("bounce", bounce));
    friction(input.getDoubleOr("friction", friction));
    contactMaxAllowedPenetration(
        input.getDoubleOr("contact_max_allowed_penetration", contactMaxAllowedPenetration));
    penetrationSlop(input.getDoubleOr("penetration_slop", penetrationSlop));
    sleepThreshold(input.getDoubleOr("sleep_threshold", sleepThreshold));
    timeBeforeSleep(input.getDoubleOr("time_before_sleep", timeBeforeSleep));
    input.read("angular_velocity", Vec3.CODEC).ifPresent(this::angularVelocity);
    input.read("linear_velocity", Vec3.CODEC).ifPresent(this::linearVelocity);
    input.read("constant_force", Vec3.CODEC).ifPresent(this::constantForce);
    input.read("constant_torque", Vec3.CODEC).ifPresent(this::constantTorque);
    orientation.set(
        input.getFloatOr("orient_x", orientation.x),
        input.getFloatOr("orient_y", orientation.y),
        input.getFloatOr("orient_z", orientation.z),
        input.getFloatOr("orient_w", orientation.w));
    orientation.normalize();
    prevOrientation.set(orientation);
    sleeping = input.getBooleanOr("sleeping", sleeping);
    linearLock = input.getBooleanOr("linear_lock", linearLock);
    inputRayPickable = input.getBooleanOr("input_ray_pickable", inputRayPickable);
    syncOrientationData();
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.putDouble("mass", mass);
    output.putDouble("linear_damp", linearDamp);
    output.putDouble("angular_damp", angularDamp);
    output.putDouble("gravity_scale", gravityScale);
    output.putDouble("bounce", bounce);
    output.putDouble("friction", friction);
    output.putDouble("contact_max_allowed_penetration", contactMaxAllowedPenetration);
    output.putDouble("penetration_slop", penetrationSlop);
    output.putDouble("sleep_threshold", sleepThreshold);
    output.putDouble("time_before_sleep", timeBeforeSleep);
    output.store("angular_velocity", Vec3.CODEC, angularVelocity);
    output.store("linear_velocity", Vec3.CODEC, linearVelocity);
    output.store("constant_force", Vec3.CODEC, constantForce);
    output.store("constant_torque", Vec3.CODEC, constantTorque);
    output.putFloat("orient_x", orientation.x);
    output.putFloat("orient_y", orientation.y);
    output.putFloat("orient_z", orientation.z);
    output.putFloat("orient_w", orientation.w);
    output.putBoolean("sleeping", sleeping);
    output.putBoolean("linear_lock", linearLock);
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
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    Entity attacker = source.getEntity();
    if (attacker instanceof Player player && source.is(DamageTypes.PLAYER_ATTACK)) {
      return handlePlayerSurfaceInput(player);
    }

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

    Optional<RigidBodyCollisionModel.SurfaceHit> hit =
        raycastSurface(ray.origin(), ray.direction(), 1.0F, TARGET_REACH);
    if (hit.isEmpty()) {
      return false;
    }

    RigidBodyCollisionModel.SurfaceHit surfaceHit = hit.get();
    onSurfaceInput(player, surfaceHit.worldPoint(), surfaceHit.worldNormal());
    lastSurfaceInputTick = tickCount;
    return true;
  }

  protected void onSurfaceInput(Player player, Vec3 surfacePosition, Vec3 surfaceNormal) {}

  @Override
  public void tick() {
    interpolation.interpolate();
    prevOrientation.set(orientation);

    if (level().isClientSide()) {
      orientation.set(
          entityData.get(DATA_ORIENT_X),
          entityData.get(DATA_ORIENT_Y),
          entityData.get(DATA_ORIENT_Z),
          entityData.get(DATA_ORIENT_W));
    } else {
      applyConstantForces();
      if (!sleeping && !linearLock) {
        moveAndCollide(linearVelocity().scale(physicsDt()));
      }
      if (!sleeping) {
        integrateAngularVelocity();
        resolvePenetration();
      }
      linearVelocity(linearVelocity().scale(1.0D - linearDamp * physicsDt()));
      syncOrientationData();
      updateSleepState();
    }

    refreshOrientedBoundingBox();
    super.tick();
  }

  private void refreshOrientedBoundingBox() {
    setBoundingBox(
        collisionModel.orientedBounds(collisionCenter(), getOrientation(1.0F), PICK_BBOX_INFLATE));
  }

  protected void applyConstantForces() {
    if (sleeping) {
      return;
    }

    applyGravity();

    if (constantForce.lengthSqr() > 1.0E-8D) {
      applyForce(constantForce);
    }

    if (constantTorque.lengthSqr() > 1.0E-8D) {
      applyTorque(constantTorque);
    }
  }

  protected void applyGravity() {
    if (gravityScale <= 1.0E-8D) {
      return;
    }

    Vec3 gravityAccel = new Vec3(0.0D, -GRAVITY * gravityScale, 0.0D);
    linearVelocity(linearVelocity().add(gravityAccel.scale(physicsDt())));
    applyGravityTorqueAboutSupport(gravityAccel.scale(mass));
  }

  private void applyGravityTorqueAboutSupport(Vec3 gravityForce) {
    if (isStableSupport()) {
      return;
    }

    List<Vec3> supportPoints = findGroundSupportPoints();
    if (supportPoints.isEmpty()) {
      return;
    }

    Vec3 pivot = averagePoint(supportPoints);
    Vec3 leverArm = collisionCenter().subtract(pivot);
    applyTorque(leverArm.cross(gravityForce));
  }

  public void applyForce(Vec3 force) {
    applyForce(force, Vec3.ZERO);
  }

  public void applyForce(Vec3 force, Vec3 position) {
    wake();
    linearVelocity(linearVelocity().add(force.scale(physicsDt() / mass)));
    if (position.lengthSqr() > 1.0E-8D) {
      applyTorque(position.cross(force));
    }
  }

  public void applyImpulse(Vec3 impulse) {
    applyImpulse(impulse, Vec3.ZERO);
  }

  public void applyImpulse(Vec3 impulse, Vec3 position) {
    wake();
    linearVelocity(linearVelocity().add(impulse.scale(1.0D / mass)));
    if (position.lengthSqr() > 1.0E-8D) {
      applyTorqueImpulse(position.cross(impulse));
    }
  }

  public void applyTorque(Vec3 torque) {
    wake();
    Vec3 torqueBody = toBodyDirection(torque);
    angularVelocity(angularVelocity().add(divideByInertia(torqueBody).scale(physicsDt())));
  }

  public void applyTorqueImpulse(Vec3 impulse) {
    wake();
    Vec3 impulseBody = toBodyDirection(impulse);
    angularVelocity(angularVelocity().add(divideByInertia(impulseBody)));
  }

  protected void integrateAngularVelocity() {
    if (angularVelocity.lengthSqr() <= 1.0E-8D) {
      return;
    }

    double dt = physicsDt();
    orientation.rotateX((float) (angularVelocity.x * dt));
    orientation.rotateY((float) (angularVelocity.y * dt));
    orientation.rotateZ((float) (angularVelocity.z * dt));
    orientation.normalize();
    angularVelocity(angularVelocity().scale(1.0D - angularDamp * physicsDt()));
  }

  public MoveAndCollideResult moveAndCollide(Vec3 motion) {
    return moveAndCollide(motion, false, 0.001D, false, RECOVERY_ITERATIONS);
  }

  public MoveAndCollideResult moveAndCollide(
      Vec3 motion,
      boolean testOnly,
      double safeMargin,
      boolean recoveryAsCollision,
      int maxCollisions) {
    if (!testOnly) {
      wake();
    }

    MoveAndCollideResult recovery = null;
    if (isPenetrating()) {
      recovery = recoverFromPenetration(testOnly, safeMargin, maxCollisions);
    }

    Vec3 startPos = collisionCenter();
    if (motion.lengthSqr() <= 1.0E-12D) {
      if (recoveryAsCollision && recovery != null) {
        return recovery;
      }
      return null;
    }

    refreshOrientedBoundingBox();
    AABB sweepBounds =
        collisionModel
            .orientedBounds(startPos, orientation, PICK_BBOX_INFLATE)
            .minmax(
                collisionModel.orientedBounds(
                    startPos.add(motion), orientation, PICK_BBOX_INFLATE));

    CastHit earliest = null;
    for (AABB block : collectBlockAABBs(sweepBounds)) {
      CastHit hit = castMotionAgainstBlock(startPos, motion, block);
      if (hit != null && (earliest == null || hit.fraction() < earliest.fraction())) {
        earliest = hit;
      }
    }

    if (earliest != null) {
      Vec3 travel = motion.scale(earliest.fraction());
      Vec3 hitCenter = startPos.add(travel);
      Vec3 remainder = motion.subtract(travel);

      if (!testOnly) {
        setCollisionCenter(hitCenter.add(earliest.normal().scale(safeMargin)));
      }

      Vec3 leverArm = earliest.contactPoint().subtract(collisionCenter());
      MoveAndCollideResult result =
          new MoveAndCollideResult(
              earliest.contactPoint(),
              earliest.normal(),
              travel,
              remainder,
              earliest.block(),
              earliest.depth(),
              false,
              leverArm);

      if (!testOnly) {
        applyCollisionImpulse(result);
      }
      return result;
    }

    if (!testOnly) {
      setCollisionCenter(startPos.add(motion));
    }

    if (recoveryAsCollision && recovery != null) {
      return recovery;
    }

    return null;
  }

  protected double physicsDt() {
    return 1.0D / level().tickRateManager().tickrate();
  }

  protected void wake() {
    sleeping = false;
    belowSleepThresholdTick = -1;
  }

  protected void updateSleepState() {
    if (sleepThreshold() <= 1.0E-8D) {
      belowSleepThresholdTick = -1;
      return;
    }

    if (maxPointVelocity() < sleepThreshold()) {
      if (belowSleepThresholdTick < 0) {
        belowSleepThresholdTick = tickCount;
      }

      double belowThresholdTime = (tickCount - belowSleepThresholdTick) * physicsDt();
      if (belowThresholdTime >= timeBeforeSleep() && !isPenetrating() && isStableSupport()) {
        sleeping = true;
        linearVelocity(Vec3.ZERO);
        angularVelocity(Vec3.ZERO);
        belowSleepThresholdTick = -1;
      }
    } else {
      belowSleepThresholdTick = -1;
    }
  }

  private boolean isPenetrating() {
    refreshOrientedBoundingBox();
    for (AABB block : collectBlockAABBs(getBoundingBox())) {
      SATResult sat = satOBBvsAABB(collisionCenter(), block);
      if (sat != null && sat.depth() > contactMaxAllowedPenetration()) {
        return true;
      }
    }
    return false;
  }

  private List<AABB> collectBlockAABBs(AABB searchBounds) {
    List<AABB> blocks = new ArrayList<>();
    for (VoxelShape shape : level().getBlockCollisions(this, searchBounds)) {
      blocks.addAll(shape.toAabbs());
    }
    return blocks;
  }

  protected int minimumStableSupportPoints() {
    return 3;
  }

  protected boolean isStableSupport() {
    List<Vec3> supportPoints = findGroundSupportPoints();
    if (supportPoints.size() < minimumStableSupportPoints()) {
      return false;
    }
    return isComInsideSupportPolygon(supportPoints);
  }

  protected List<Vec3> findGroundSupportPoints() {
    Vec3 center = collisionCenter();
    Vec3[] corners = collisionModel.worldCorners(center, orientation);
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

  private boolean isComInsideSupportPolygon(List<Vec3> supportPoints) {
    Vec3 com = collisionCenter();
    if (supportPoints.size() == 1) {
      double dx = com.x - supportPoints.getFirst().x;
      double dz = com.z - supportPoints.getFirst().z;
      return dx * dx + dz * dz <= STABLE_COM_EPS * STABLE_COM_EPS;
    }
    if (supportPoints.size() == 2) {
      return isPointOnSegmentXZ(
          com, supportPoints.get(0), supportPoints.get(1), STABLE_COM_EPS);
    }

    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    for (Vec3 point : supportPoints) {
      minX = Math.min(minX, point.x);
      maxX = Math.max(maxX, point.x);
      minZ = Math.min(minZ, point.z);
      maxZ = Math.max(maxZ, point.z);
    }

    if (com.x < minX - STABLE_COM_EPS
        || com.x > maxX + STABLE_COM_EPS
        || com.z < minZ - STABLE_COM_EPS
        || com.z > maxZ + STABLE_COM_EPS) {
      return false;
    }

    List<Vec3> polygon = sortPointsXZ(supportPoints);
    for (int i = 0; i < polygon.size(); i++) {
      Vec3 a = polygon.get(i);
      Vec3 b = polygon.get((i + 1) % polygon.size());
      if (isLeftOfEdgeXZ(com, a, b)) {
        return false;
      }
    }
    return true;
  }

  private static List<Vec3> sortPointsXZ(List<Vec3> points) {
    Vec3 centroid = averagePoint(points);
    List<Vec3> sorted = new ArrayList<>(points);
    sorted.sort(
        (a, b) ->
            Double.compare(
                Math.atan2(a.z - centroid.z, a.x - centroid.x),
                Math.atan2(b.z - centroid.z, b.x - centroid.x)));
    return sorted;
  }

  private static boolean isPointOnSegmentXZ(Vec3 point, Vec3 a, Vec3 b, double maxDist) {
    double abX = b.x - a.x;
    double abZ = b.z - a.z;
    double abLenSq = abX * abX + abZ * abZ;
    if (abLenSq < 1.0E-12D) {
      double dx = point.x - a.x;
      double dz = point.z - a.z;
      return dx * dx + dz * dz <= maxDist * maxDist;
    }

    double t =
        Math.clamp(((point.x - a.x) * abX + (point.z - a.z) * abZ) / abLenSq, 0.0D, 1.0D);
    double closestX = a.x + abX * t;
    double closestZ = a.z + abZ * t;
    double dx = point.x - closestX;
    double dz = point.z - closestZ;
    return dx * dx + dz * dz <= maxDist * maxDist;
  }

  private static boolean isLeftOfEdgeXZ(Vec3 point, Vec3 a, Vec3 b) {
    return (b.x - a.x) * (point.z - a.z) - (b.z - a.z) * (point.x - a.x) < -1.0E-8D;
  }

  private static Vec3 averagePoint(List<Vec3> points) {
    Vec3 sum = Vec3.ZERO;
    for (Vec3 point : points) {
      sum = sum.add(point);
    }
    return sum.scale(1.0D / points.size());
  }

  private MoveAndCollideResult recoverFromPenetration(
      boolean testOnly, double safeMargin, int maxCollisions) {
    MoveAndCollideResult deepestRecovery = null;
    double deepestDepth = 0.0D;
    int iterations = Math.min(Math.max(maxCollisions, 1), RECOVERY_ITERATIONS);

    for (int iteration = 0; iteration < iterations; iteration++) {
      Vec3 center = collisionCenter();
      refreshOrientedBoundingBox();
      SATResult deepest = null;
      AABB deepestBlock = null;

      for (AABB block : collectBlockAABBs(getBoundingBox())) {
        SATResult sat = satOBBvsAABB(center, block);
        if (sat != null && (deepest == null || sat.depth() > deepest.depth())) {
          deepest = sat;
          deepestBlock = block;
        }
      }

      if (deepest == null) {
        break;
      }

      double excessDepth = deepest.depth() - contactMaxAllowedPenetration();
      if (excessDepth <= penetrationSlop()) {
        break;
      }

      Vec3 contact = findContactPoint(center, deepestBlock, deepest.normal());
      Vec3 separation = deepest.normal().scale(excessDepth + safeMargin);
      if (!testOnly) {
        setCollisionCenter(center.add(separation));
      }

      deepestRecovery =
          new MoveAndCollideResult(
              contact,
              deepest.normal(),
              separation,
              Vec3.ZERO,
              deepestBlock,
              deepest.depth(),
              true,
              contact.subtract(center));

      deepestDepth = deepest.depth();
    }

    return deepestRecovery != null && deepestDepth > penetrationSlop() ? deepestRecovery : null;
  }

  private void resolvePenetration() {
    refreshOrientedBoundingBox();
    boolean overlapping = false;
    for (AABB block : collectBlockAABBs(getBoundingBox())) {
      if (satOBBvsAABB(collisionCenter(), block) != null) {
        overlapping = true;
        break;
      }
    }
    if (!overlapping) {
      return;
    }

    if (isPenetrating()) {
      recoverFromPenetration(false, 0.001D, RECOVERY_ITERATIONS);
    }

    clampLinearVelocityAgainstPenetration();
    resolveRotationalContacts();
  }

  private void clampLinearVelocityAgainstPenetration() {
    if (bounce > 1.0E-8D) {
      return;
    }

    refreshOrientedBoundingBox();
    for (AABB block : collectBlockAABBs(getBoundingBox())) {
      SATResult sat = satOBBvsAABB(collisionCenter(), block);
      if (sat == null) {
        continue;
      }

      double vn = linearVelocity().dot(sat.normal());
      if (vn < 0.0D) {
        linearVelocity(linearVelocity().subtract(sat.normal().scale(vn)));
      }
    }
  }

  private void resolveRotationalContacts() {
    if (bounce > 1.0E-8D) {
      return;
    }

    refreshOrientedBoundingBox();
    boolean floorFrictionApplied = false;
    for (AABB block : collectBlockAABBs(getBoundingBox())) {
      SATResult sat = satOBBvsAABB(collisionCenter(), block);
      if (sat == null) {
        continue;
      }

      Vec3 normal = sat.normal();
      if (normal.y > 0.7D) {
        if (floorFrictionApplied) {
          continue;
        }

        if (!isStableSupport()) {
          Vec3 contact = findContactPoint(collisionCenter(), block, normal);
          applyRotationalContactResponse(contact.subtract(collisionCenter()), normal);
        }

        applyGodotComFriction(normal);
        floorFrictionApplied = true;
        continue;
      }

      Vec3 contact = findContactPoint(collisionCenter(), block, normal);
      Vec3 leverArm = contact.subtract(collisionCenter());
      applyRotationalContactResponse(leverArm, normal);
      applyGodotPointFriction(leverArm, normal);
    }
  }

  private void applyRotationalContactResponse(Vec3 leverArm, Vec3 normal) {
    double vn = velocityAtPoint(leverArm).dot(normal);
    if (vn >= 0.0D) {
      return;
    }

    double K = effectiveMassInvAtContact(leverArm, normal);
    if (K < 1.0E-8D) {
      return;
    }

    double j = -vn / K;
    Vec3 linearBefore = linearVelocity();
    applyImpulse(normal.scale(j), leverArm);
    linearVelocity(linearBefore);
  }

  private double effectiveContactFriction() {
    return Math.min(friction, BLOCK_FRICTION);
  }

  private void applyGodotComFriction(Vec3 normal) {
    double contactFriction = effectiveContactFriction();
    if (contactFriction <= 1.0E-8D) {
      return;
    }

    Vec3 velocity = linearVelocity();
    Vec3 tangent = velocity.subtract(normal.scale(velocity.dot(normal)));
    if (tangent.lengthSqr() > 1.0E-8D) {
      linearVelocity(velocity.subtract(tangent.scale(contactFriction)));
    }

    applyGodotAngularFriction(contactFriction);
  }

  private void applyGodotPointFriction(Vec3 leverArm, Vec3 normal) {
    double contactFriction = effectiveContactFriction();
    if (contactFriction <= 1.0E-8D) {
      return;
    }

    Vec3 velocity = velocityAtPoint(leverArm);
    Vec3 tangent = velocity.subtract(normal.scale(velocity.dot(normal)));
    if (tangent.lengthSqr() > 1.0E-8D) {
      applyContactVelocityDelta(leverArm, tangent.scale(-contactFriction));
    }
  }

  private void applyGodotAngularFriction(double contactFriction) {
    if (angularVelocity().lengthSqr() <= 1.0E-8D) {
      return;
    }

    angularVelocity(angularVelocity().scale(1.0D - contactFriction));
  }

  private void applyContactVelocityDelta(Vec3 leverArm, Vec3 deltaV) {
    double deltaSpeed = deltaV.length();
    if (deltaSpeed <= 1.0E-8D) {
      return;
    }

    Vec3 dir = deltaV.scale(1.0D / deltaSpeed);
    double K = effectiveMassInvAtContact(leverArm, dir);
    if (K <= 1.0E-8D) {
      return;
    }

    applyImpulse(dir.scale(deltaSpeed / K), leverArm);
  }

  private CastHit castMotionAgainstBlock(Vec3 start, Vec3 motion, AABB block) {
    if (satOBBvsAABB(start, block) != null) {
      return null;
    }

    if (satOBBvsAABB(start.add(motion), block) == null) {
      return null;
    }

    double lo = 0.0D;
    double hi = 1.0D;
    for (int step = 0; step < CAST_BINARY_STEPS; step++) {
      double mid = (lo + hi) * 0.5D;
      if (satOBBvsAABB(start.add(motion.scale(mid)), block) != null) {
        hi = mid;
      } else {
        lo = mid;
      }
    }

    double fraction = Math.max(0.0D, lo);
    Vec3 hitCenter = start.add(motion.scale(fraction));
    SATResult sat = satOBBvsAABB(hitCenter, block);
    if (sat == null) {
      sat = satOBBvsAABB(start.add(motion.scale(hi)), block);
    }
    if (sat == null) {
      return null;
    }

    Vec3 contact = findContactPoint(hitCenter, block, sat.normal());
    return new CastHit(fraction, sat.normal(), sat.depth(), block, contact);
  }

  private Vec3 findContactPoint(Vec3 center, AABB block, Vec3 normal) {
    Vec3[] corners = collisionModel.worldCorners(center, orientation);
    List<Vec3> contacts = new ArrayList<>();
    for (Vec3 corner : corners) {
      if (corner.x >= block.minX && corner.x <= block.maxX
          && corner.y >= block.minY && corner.y <= block.maxY
          && corner.z >= block.minZ && corner.z <= block.maxZ) {
        contacts.add(corner);
      }
    }

    if (!contacts.isEmpty()) {
      Vec3 best = contacts.getFirst();
      double bestVn = Double.POSITIVE_INFINITY;
      for (Vec3 contact : contacts) {
        double vn = contact.subtract(center).dot(normal);
        if (vn < bestVn) {
          bestVn = vn;
          best = contact;
        }
      }
      return best;
    }

    Vec3 blockCenter =
        new Vec3(
            (block.minX + block.maxX) * 0.5,
            (block.minY + block.maxY) * 0.5,
            (block.minZ + block.maxZ) * 0.5);
    Vec3 closest = corners[0];
    for (Vec3 corner : corners) {
      if (corner.distanceToSqr(blockCenter) < closest.distanceToSqr(blockCenter)) {
        closest = corner;
      }
    }
    return closest;
  }

  private void applyCollisionImpulse(MoveAndCollideResult collision) {
    Vec3 normal = collision.normal();
    Vec3 leverArm = collision.leverArm();

    if (bounce <= 1.0E-8D) {
      if (isStableSupport() && normal.y > 0.7D) {
        double comVn = linearVelocity().dot(normal);
        if (comVn < 0.0D) {
          linearVelocity(linearVelocity().subtract(normal.scale(comVn)));
        }
      } else {
        applyRotationalContactResponse(leverArm, normal);
      }
      return;
    }

    double vn = velocityAtPoint(leverArm).dot(normal);
    if (vn >= 0.0D) {
      return;
    }

    double K = effectiveMassInvAtContact(leverArm, normal);
    if (K < 1.0E-8D) {
      return;
    }

    double j = -(1.0 + bounce) * vn / K;
    applyImpulse(normal.scale(j), leverArm);
  }

  private SATResult satOBBvsAABB(Vec3 center, AABB block) {
    Vec3 blockCenter =
        new Vec3(
            (block.minX + block.maxX) * 0.5,
            (block.minY + block.maxY) * 0.5,
            (block.minZ + block.maxZ) * 0.5);
    Vec3 blockHalf =
        new Vec3(
            (block.maxX - block.minX) * 0.5,
            (block.maxY - block.minY) * 0.5,
            (block.maxZ - block.minZ) * 0.5);
    Vec3 halfExtents = collisionModel.halfExtents();
    Vec3 u0 = toWorldDirection(new Vec3(1, 0, 0));
    Vec3 u1 = toWorldDirection(new Vec3(0, 1, 0));
    Vec3 u2 = toWorldDirection(new Vec3(0, 0, 1));
    Vec3 WX = new Vec3(1, 0, 0), WY = new Vec3(0, 1, 0), WZ = new Vec3(0, 0, 1);
    Vec3[] axes = {
      WX, WY, WZ,
      u0, u1, u2,
      WX.cross(u0), WX.cross(u1), WX.cross(u2),
      WY.cross(u0), WY.cross(u1), WY.cross(u2),
      WZ.cross(u0), WZ.cross(u1), WZ.cross(u2)
    };

    double minDepth = Double.MAX_VALUE;
    Vec3 bestNormal = null;
    for (Vec3 axis : axes) {
      double lenSq = axis.lengthSqr();
      if (lenSq < 1.0E-8) continue;
      Vec3 a = axis.scale(1.0 / Math.sqrt(lenSq));
      double blockR =
          Math.abs(a.x) * blockHalf.x
              + Math.abs(a.y) * blockHalf.y
              + Math.abs(a.z) * blockHalf.z;
      double obbR =
          Math.abs(a.dot(u0)) * halfExtents.x
              + Math.abs(a.dot(u1)) * halfExtents.y
              + Math.abs(a.dot(u2)) * halfExtents.z;
      double blockProj = a.dot(blockCenter);
      double bodyProj = a.dot(center);
      double depth = blockR + obbR - Math.abs(bodyProj - blockProj);
      if (depth <= 0.0) return null;
      if (depth < minDepth) {
        minDepth = depth;
        bestNormal = a.scale(bodyProj >= blockProj ? 1.0 : -1.0);
      }
    }
    return bestNormal == null ? null : new SATResult(bestNormal, minDepth);
  }

  private double maxPointVelocity() {
    double maxSpeed = 0.0D;
    for (Vec3 corner : collisionModel.worldCorners(collisionCenter(), orientation)) {
      Vec3 r = corner.subtract(collisionCenter());
      maxSpeed = Math.max(maxSpeed, velocityAtPoint(r).length());
    }
    return maxSpeed;
  }

  public static Vec3 cubeInertia(double mass, double halfSize) {
    return boxInertia(mass, halfSize, halfSize, halfSize);
  }

  public static Vec3 boxInertia(double mass, double halfX, double halfY, double halfZ) {
    double sizeX = halfX * 2.0D;
    double sizeY = halfY * 2.0D;
    double sizeZ = halfZ * 2.0D;
    return new Vec3(
        (mass / 12.0D) * (sizeY * sizeY + sizeZ * sizeZ),
        (mass / 12.0D) * (sizeX * sizeX + sizeZ * sizeZ),
        (mass / 12.0D) * (sizeX * sizeX + sizeY * sizeY));
  }

  protected Vec3 toBodyDirection(Vec3 worldDirection) {
    Vector3f local =
        new Vector3f(
            (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  protected Vec3 toWorldDirection(Vec3 bodyDirection) {
    Vector3f world =
        new Vector3f(
            (float) bodyDirection.x, (float) bodyDirection.y, (float) bodyDirection.z);
    orientation.transform(world);
    return new Vec3(world.x, world.y, world.z);
  }

  protected Vec3 divideByInertia(Vec3 vector) {
    return new Vec3(vector.x / inertia().x, vector.y / inertia().y, vector.z / inertia().z);
  }

  private Vec3 velocityAtPoint(Vec3 r) {
    Vec3 spin = toWorldDirection(angularVelocity).cross(r);
    return linearVelocity.add(spin);
  }

  private double effectiveMassInvAtContact(Vec3 r, Vec3 n) {
    Vec3 rCrossN = r.cross(n);
    Vec3 iInvRCrossNWorld = toWorldDirection(divideByInertia(toBodyDirection(rCrossN)));
    return 1.0 / mass + iInvRCrossNWorld.dot(rCrossN);
  }

  protected void syncOrientationData() {
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
