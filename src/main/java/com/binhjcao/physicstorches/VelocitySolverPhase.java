package com.binhjcao.physicstorches;

import java.util.List;

import net.minecraft.world.phys.Vec3;

public class VelocitySolverPhase {
  public static void solve(List<ContactConstraint> constraints) {
    if (constraints.isEmpty()) {
      return;
    }

    for (ContactConstraint constraint : constraints) {
      warmStartConstraint(constraint);
    }

    for (int iteration = 0; iteration < Physics.VELOCITY_SOLVER_ITERATIONS; iteration++) {
      for (ContactConstraint constraint : constraints) {
        solveConstraintStep(constraint);
      }
    }
  }

  private static void warmStartConstraint(ContactConstraint constraint) {
    RigidBody a = constraint.a();
    RigidBody b = constraint.b();
    if (a.freeze() && b.freeze()) {
      return;
    }
    if (a.isSleeping() && b.isSleeping()) {
      return;
    }
    Vec3 normal = constraint.normal();
    Vec3 tangent1 = constraint.tangent1();
    Vec3 tangent2 = constraint.tangent2();
    for (int i = 0; i < constraint.points().size(); i++) {
      warmStartPoint(a, b, normal, tangent1, tangent2, constraint.points().get(i));
    }
  }

  private static void solveConstraintStep(ContactConstraint constraint) {
    RigidBody a = constraint.a();
    RigidBody b = constraint.b();
    if (a.freeze() && b.freeze()) {
      return;
    }
    if (a.isSleeping() && b.isSleeping()) {
      return;
    }
    Vec3 normal = constraint.normal();
    Vec3 tangent1 = constraint.tangent1();
    Vec3 tangent2 = constraint.tangent2();
    float friction = (float) Math.min(a.friction(), b.friction());

    for (int i = 0; i < constraint.points().size(); i++) {
      constraint
          .points()
          .set(
              i,
              solvePoint(a, b, normal, tangent1, tangent2, friction, constraint.points().get(i)));
    }
  }

  private static void warmStartPoint(
      RigidBody a,
      RigidBody b,
      Vec3 normal,
      Vec3 tangent1,
      Vec3 tangent2,
      ContactConstraintPoint point) {
    Vec3 impulse =
        normal
            .scale(point.accumulatedNormalImpulse())
            .add(tangent1.scale(point.accumulatedTangentImpulseU()))
            .add(tangent2.scale(point.accumulatedTangentImpulseV()));
    if (impulse.lengthSqr() <= 1.0E-12D) {
      return;
    }
    applyPairImpulse(a, b, point.leverArmA(), point.leverArmB(), impulse);
  }

  private static ContactConstraintPoint solvePoint(
      RigidBody a,
      RigidBody b,
      Vec3 normal,
      Vec3 tangent1,
      Vec3 tangent2,
      float friction,
      ContactConstraintPoint point) {
    Vec3 relativeVelocity =
        b.velocityAtPoint(point.leverArmB()).subtract(a.velocityAtPoint(point.leverArmA()));

    float normalVelocity = (float) relativeVelocity.dot(normal);
    float normalMass = point.normalMass();
    if (normalMass <= 1.0E-8F) {
      return point;
    }
    float normalImpulse = (-normalVelocity + point.velocityBias()) / normalMass;
    float accumulatedNormal = point.accumulatedNormalImpulse() + normalImpulse;
    if (accumulatedNormal < 0.0F) {
      normalImpulse = -point.accumulatedNormalImpulse();
      accumulatedNormal = 0.0F;
    }
    if (Math.abs(normalImpulse) > 1.0E-8F) {
      applyPairImpulse(a, b, point.leverArmA(), point.leverArmB(), normal.scale(normalImpulse));
    }

    relativeVelocity =
        b.velocityAtPoint(point.leverArmB()).subtract(a.velocityAtPoint(point.leverArmA()));
    float frictionLimit = friction * accumulatedNormal;
    float tangentMass1 = point.tangentMass1();
    float tangentImpulseU =
        tangentMass1 <= 1.0E-8F
            ? 0.0F
            : clampIncrementalImpulse(
                (float) -relativeVelocity.dot(tangent1) / tangentMass1,
                point.accumulatedTangentImpulseU(),
                frictionLimit);
    float accumulatedTangentU = point.accumulatedTangentImpulseU() + tangentImpulseU;
    if (Math.abs(tangentImpulseU) > 1.0E-8F) {
      applyPairImpulse(a, b, point.leverArmA(), point.leverArmB(), tangent1.scale(tangentImpulseU));
      relativeVelocity =
          b.velocityAtPoint(point.leverArmB()).subtract(a.velocityAtPoint(point.leverArmA()));
    }

    float tangentMass2 = point.tangentMass2();
    float tangentImpulseV =
        tangentMass2 <= 1.0E-8F
            ? 0.0F
            : clampIncrementalImpulse(
                (float) -relativeVelocity.dot(tangent2) / tangentMass2,
                point.accumulatedTangentImpulseV(),
                frictionLimit);
    float accumulatedTangentV = point.accumulatedTangentImpulseV() + tangentImpulseV;
    if (Math.abs(tangentImpulseV) > 1.0E-8F) {
      applyPairImpulse(a, b, point.leverArmA(), point.leverArmB(), tangent2.scale(tangentImpulseV));
    }

    return new ContactConstraintPoint(
        point.leverArmA(),
        point.leverArmB(),
        point.localContactA(),
        point.localContactB(),
        point.normalMass(),
        point.tangentMass1(),
        point.tangentMass2(),
        point.velocityBias(),
        point.positionBias(),
        accumulatedNormal,
        accumulatedTangentU,
        accumulatedTangentV);
  }

  private static void applyPairImpulse(
      RigidBody a,
      RigidBody b,
      Vec3 leverArmA,
      Vec3 leverArmB,
      Vec3 impulse) {
    a.applyImpulse(impulse.scale(-1.0D), leverArmA);
    b.applyImpulse(impulse, leverArmB);
  }

  private static float clampIncrementalImpulse(
      float impulse, float accumulated, float limit) {
    return Math.max(-limit - accumulated, Math.min(limit - accumulated, impulse));
  }

  private VelocitySolverPhase() {}
}
