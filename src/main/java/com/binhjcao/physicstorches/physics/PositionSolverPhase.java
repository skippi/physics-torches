package com.binhjcao.physicstorches.physics;

import java.util.Collection;

import com.binhjcao.physicstorches.OrientedTransform;

import net.minecraft.world.phys.Vec3;

public class PositionSolverPhase {
  public static void solve(Collection<ContactConstraint> constraints) {
    if (constraints.isEmpty()) {
      return;
    }

    for (int iteration = 0; iteration < Physics.POSITION_SOLVER_ITERATIONS; iteration++) {
      for (ContactConstraint constraint : constraints) {
        solveConstraintStep(constraint);
      }
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
    for (ContactConstraintPoint point : constraint.points()) {
      solvePoint(a, b, normal, point);
    }
  }

  private static void solvePoint(
      RigidBody a, RigidBody b, Vec3 normal, ContactConstraintPoint point) {
    float separation = computeSeparation(a, b, point, normal);
    float positionDelta = ContactConstraintPhase.positionBiasFromSeparation(separation);
    if (positionDelta <= 1.0E-8F) {
      return;
    }

    float normalMass = point.normalMass();
    if (normalMass <= 1.0E-8F) {
      return;
    }

    Vec3 worldA =
        OrientedTransform.toWorldPoint(
            point.localContactA(), a.position(), a.orientation());
    Vec3 worldB =
        OrientedTransform.toWorldPoint(
            point.localContactB(), b.position(), b.orientation());
    Vec3 leverArmA = worldA.subtract(a.position());
    Vec3 leverArmB = worldB.subtract(b.position());

    double lambda = positionDelta / normalMass;
    if (!a.freeze()) {
      a.applyPositionCorrection(normal, leverArmA, -lambda);
    }
    if (!b.freeze()) {
      b.applyPositionCorrection(normal, leverArmB, lambda);
    }
  }

  private static float computeSeparation(
      RigidBody a, RigidBody b, ContactConstraintPoint point, Vec3 normal) {
    Vec3 worldA =
        OrientedTransform.toWorldPoint(
            point.localContactA(), a.position(), a.orientation());
    Vec3 worldB =
        OrientedTransform.toWorldPoint(
            point.localContactB(), b.position(), b.orientation());
    return (float) worldB.subtract(worldA).dot(normal);
  }

  private PositionSolverPhase() {}
}
