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
import org.joml.Vector3f;

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
  public static final double GRAVITY = 9.81D;

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

  private double mass = 1.0D;
  private double linearDamp = 0.0D;
  private double angularDamp = 0.0D;
  private double gravityScale = 1.0D;
  private double sleepThreshold = 0.005D;
  private Vec3 angularVelocity = Vec3.ZERO;
  private Vec3 linearVelocity = Vec3.ZERO;
  private Vec3 constantForce = Vec3.ZERO;
  private Vec3 constantTorque = Vec3.ZERO;
  private boolean sleeping = false;

  public EntityRigidBody(EntityType<? extends EntityRigidBody> type, Level level) {
    super(type, level);
    setNoGravity(true);
    orientation.identity();
    prevOrientation.identity();
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
    this.sleepThreshold = Math.clamp(sleepThreshold, 0.0D, 1.0D);
  }

  public Vec3 inertia() {
    return cubeInertia(mass(), HALF_SIZE);
  }

  protected boolean sleeping() {
    return sleeping;
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
        position(),
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
  protected void readAdditionalSaveData(ValueInput input) {
    mass(input.getDoubleOr("mass", mass));
    linearDamp(input.getDoubleOr("linear_damp", linearDamp));
    angularDamp(input.getDoubleOr("angular_damp", angularDamp));
    gravityScale(input.getDoubleOr("gravity_scale", gravityScale));
    sleepThreshold(input.getDoubleOr("sleep_threshold", sleepThreshold));
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
    syncOrientationData();
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.putDouble("mass", mass);
    output.putDouble("linear_damp", linearDamp);
    output.putDouble("angular_damp", angularDamp);
    output.putDouble("gravity_scale", gravityScale);
    output.putDouble("sleep_threshold", sleepThreshold);
    output.store("angular_velocity", Vec3.CODEC, angularVelocity);
    output.store("linear_velocity", Vec3.CODEC, linearVelocity);
    output.store("constant_force", Vec3.CODEC, constantForce);
    output.store("constant_torque", Vec3.CODEC, constantTorque);
    output.putFloat("orient_x", orientation.x);
    output.putFloat("orient_y", orientation.y);
    output.putFloat("orient_z", orientation.z);
    output.putFloat("orient_w", orientation.w);
    output.putBoolean("sleeping", sleeping);
  }

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
      applyConstantForces();
      integrateAngularVelocity();
      integrateLinearVelocity();
      syncOrientationData();
      updateSleepState();
    }

    refreshOrientedBoundingBox();
    super.tick();
  }

  private void refreshOrientedBoundingBox() {
    setBoundingBox(
        COLLISION.orientedBounds(position(), getOrientation(1.0F), PICK_BBOX_INFLATE));
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

    Vec3 gravityForce = new Vec3(0.0D, -mass * GRAVITY * gravityScale, 0.0D);
    applyForce(gravityForce);
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

  protected void integrateLinearVelocity() {
    if (linearVelocity().lengthSqr() <= 1.0E-8D) {
      return;
    }

    Vec3 next = position().add(linearVelocity());
    setPos(next.x, next.y, next.z);
    linearVelocity(linearVelocity().scale(1.0D - linearDamp * physicsDt()));
  }

  protected double physicsDt() {
    return 1.0D / level().tickRateManager().tickrate();
  }

  protected void wake() {
    sleeping = false;
  }

  protected void updateSleepState() {
    if (sleepThreshold() <= 1.0E-8D) {
      return;
    }

    if (specificKineticEnergy() < sleepThreshold()) {
      sleeping = true;
      linearVelocity(Vec3.ZERO);
      angularVelocity(Vec3.ZERO);
    }
  }

  protected double specificKineticEnergy() {
    double linearEnergy = 0.5D * linearVelocity.lengthSqr();
    double angularEnergy =
        0.5D
            * (inertia().x * angularVelocity.x * angularVelocity.x
                + inertia().y * angularVelocity.y * angularVelocity.y
                + inertia().z * angularVelocity.z * angularVelocity.z);
    return (linearEnergy + angularEnergy) / mass;
  }

  public static Vec3 cubeInertia(double mass, double halfSize) {
    double edgeLength = halfSize * 2.0D;
    double component = (mass / 12.0D) * (edgeLength * edgeLength + edgeLength * edgeLength);
    return new Vec3(component, component, component);
  }

  protected Vec3 toBodyDirection(Vec3 worldDirection) {
    Vector3f local =
        new Vector3f(
            (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  protected Vec3 divideByInertia(Vec3 vector) {
    return new Vec3(vector.x / inertia().x, vector.y / inertia().y, vector.z / inertia().z);
  }

  protected void syncOrientationData() {
    entityData.set(DATA_ORIENT_X, orientation.x);
    entityData.set(DATA_ORIENT_Y, orientation.y);
    entityData.set(DATA_ORIENT_Z, orientation.z);
    entityData.set(DATA_ORIENT_W, orientation.w);
  }
}
