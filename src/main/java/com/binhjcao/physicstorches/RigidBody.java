package com.binhjcao.physicstorches;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RigidBody {
  private final Collider collider;
  private final Quaternionf orientation = new Quaternionf();
  private final Quaternionf prevOrientation = new Quaternionf();

  private Vec3 position = Vec3.ZERO;
  private Vec3 prevPosition = Vec3.ZERO;
  private double mass = 1.0D;
  private double linearDamp = 0.0D;
  private double angularDamp = 0.0D;
  private double gravityScale = 1.0D;
  private double bounce = 0.0D;
  private double friction = 0.0D;
  private double belowSleepThresholdTime = 0.0D;
  private Vec3 angularVelocity = Vec3.ZERO;
  private Vec3 linearVelocity = Vec3.ZERO;
  private Vec3 constantForce = Vec3.ZERO;
  private Vec3 constantTorque = Vec3.ZERO;
  private boolean linearLock = false;
  private boolean freeze = false;
  private boolean sleeping = false;

  public RigidBody(Collider collider) {
    this(collider, Vec3.ZERO, null);
  }

  public RigidBody(Collider collider, Vec3 position) {
    this(collider, position, null);
  }

  public RigidBody(Collider collider, Vec3 position, Quaternionf initialOrientation) {
    this.collider = collider;
    this.position = position;
    this.prevPosition = position;
    orientation.identity();
    prevOrientation.identity();
    if (initialOrientation != null) {
      orientation.set(initialOrientation);
      prevOrientation.set(initialOrientation);
    }
  }

  public static RigidBody cube(double halfSize) {
    return new RigidBody(BoxCollider.cube(halfSize));
  }

  public static RigidBody cube(double halfSize, Vec3 position) {
    return new RigidBody(BoxCollider.cube(halfSize), position);
  }

  public static RigidBody frozenFromBlockAabb(AABB block) {
    BlockCollider collider = BlockCollider.from(block);
    RigidBody body = new RigidBody(collider, collider.center());
    body.freeze(true);
    body.gravityScale(0.0D);
    body.friction(1.0D);
    return body;
  }

  public Vec3 position() {
    return position;
  }

  public Vec3 prevPosition() {
    return prevPosition;
  }

  public void snapshotPrevPosition() {
    prevPosition = position;
  }

  public void position(Vec3 position) {
    this.position = position;
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

  public Vec3 linearVelocity() {
    return linearVelocity;
  }

  public void linearVelocity(Vec3 linearVelocity) {
    this.linearVelocity = linearVelocity;
  }

  public double belowSleepThresholdTime() {
    return belowSleepThresholdTime;
  }

  public boolean linearLock() {
    return linearLock;
  }

  public void linearLock(boolean linearLock) {
    this.linearLock = linearLock;
  }

  public boolean freeze() {
    return freeze;
  }

  public void freeze(boolean freeze) {
    this.freeze = freeze;
  }

  public boolean isSleeping() {
    return sleeping;
  }

  public Collider collider() {
    return collider;
  }

  public Quaternionf orientation() {
    return orientation;
  }

  public Quaternionf prevOrientation() {
    return prevOrientation;
  }

  public void snapshotPrevOrientation() {
    prevOrientation.set(orientation);
  }

  public Quaternionf getOrientation(float partialTick) {
    if (partialTick >= 1.0F) {
      return new Quaternionf(orientation);
    }
    return new Quaternionf(prevOrientation).slerp(orientation, partialTick);
  }

  public AABB bounds() {
    return collider.orientedBounds(position, orientation, 0.0D);
  }

  public Vec3 inertia() {
    Vec3 halfExtents = collider.halfExtents();
    return boxInertia(mass(), halfExtents.x, halfExtents.y, halfExtents.z);
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

  public void applyForce(Vec3 force, double dt) {
    applyImpulse(force.scale(dt));
  }

  public void applyTorque(Vec3 torque, double dt) {
    applyAngularImpulse(torque.scale(dt));
  }

  public void applyImpulse(Vec3 impulse) {
    applyImpulse(impulse, Vec3.ZERO);
  }

  public void applyImpulse(Vec3 impulse, Vec3 leverArm) {
    if (freeze) {
      return;
    }
    wake();
    linearVelocity = linearVelocity.add(impulse.scale(1.0D / mass));
    if (leverArm.lengthSqr() > 1.0E-8D) {
      applyAngularImpulse(leverArm.cross(impulse));
    }
  }

  public void applyAngularImpulse(Vec3 impulse) {
    if (freeze) {
      return;
    }
    wake();
    var localImpulse = toBodyDirection(impulse);
    angularVelocity = angularVelocity.add(divideByInertia(localImpulse));
  }

  public void applyPositionCorrection(Vec3 normal, Vec3 leverArm, double lambda) {
    if (freeze || linearLock) {
      return;
    }
    position(position.add(normal.scale(lambda / mass)));
    if (leverArm.lengthSqr() > 1.0E-8D) {
      Vec3 angularStep =
          divideByInertia(toBodyDirection(leverArm.cross(normal).scale(lambda)));
      orientation.rotateX((float) angularStep.x);
      orientation.rotateY((float) angularStep.y);
      orientation.rotateZ((float) angularStep.z);
      orientation.normalize();
    }
  }

  public void wake() {
    sleeping = false;
    belowSleepThresholdTime = 0.0D;
  }

  public void accumulateSleepTimer(double dt) {
    if (maxPointVelocity() < Physics.SLEEP_THRESHOLD) {
      belowSleepThresholdTime += dt;
    } else {
      belowSleepThresholdTime = 0.0D;
    }
  }

  public void enterSleep() {
    sleeping = true;
    linearVelocity = Vec3.ZERO;
    angularVelocity = Vec3.ZERO;
    belowSleepThresholdTime = 0.0D;
  }

  public double maxPointVelocity() {
    double maxSpeed = 0.0D;
    for (Vec3 corner : collider.worldCorners(position, orientation)) {
      Vec3 leverArm = corner.subtract(position);
      maxSpeed = Math.max(maxSpeed, velocityAtPoint(leverArm).length());
    }
    return maxSpeed;
  }

  public Vec3 toBodyDirection(Vec3 worldDirection) {
    Vector3f local =
        new Vector3f(
            (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  public Vec3 toWorldDirection(Vec3 bodyDirection) {
    Vector3f world =
        new Vector3f(
            (float) bodyDirection.x, (float) bodyDirection.y, (float) bodyDirection.z);
    orientation.transform(world);
    return new Vec3(world.x, world.y, world.z);
  }

  public Vec3 divideByInertia(Vec3 vector) {
    return new Vec3(vector.x / inertia().x, vector.y / inertia().y, vector.z / inertia().z);
  }

  public Vec3 velocityAtPoint(Vec3 leverArm) {
    if (freeze) {
      return Vec3.ZERO;
    }
    Vec3 spin = toWorldDirection(angularVelocity).cross(leverArm);
    return linearVelocity.add(spin);
  }

  public double effectiveMassInvAtContact(Vec3 leverArm, Vec3 direction) {
    if (freeze) {
      return 0.0D;
    }
    Vec3 rCrossN = leverArm.cross(direction);
    Vec3 iInvRCrossNWorld = toWorldDirection(divideByInertia(toBodyDirection(rCrossN)));
    return 1.0 / mass + iInvRCrossNWorld.dot(rCrossN);
  }
}
