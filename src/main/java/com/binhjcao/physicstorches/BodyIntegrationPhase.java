package com.binhjcao.physicstorches;

public class BodyIntegrationPhase {
  public static void integrateVelocity(RigidBody body, double dt) {
    if (body.isSleeping() || body.freeze()) {
      return;
    }

    integrateLinearVelocity(body, dt);
    integrateAngularVelocity(body, dt);
    dampLinearVelocity(body, dt);
    dampAngularVelocity(body, dt);
  }

  public static void integrateSleep(RigidBody body, double dt) {
    if (body.isSleeping() || body.freeze() || Physics.SLEEP_THRESHOLD <= 1.0E-8D) {
      return;
    }

    body.accumulateSleepTimer(dt);

    if (body.belowSleepThresholdTime() >= Physics.TIME_BEFORE_SLEEP
        && body.maxPointVelocity() < Physics.SLEEP_THRESHOLD) {
      body.enterSleep();
    }
  }

  private static void integrateLinearVelocity(RigidBody body, double dt) {
    if (body.isSleeping() || body.linearLock()) {
      return;
    }

    body.position(body.position().add(body.linearVelocity().scale(dt)));
  }

  public static void integrateAngularVelocity(RigidBody body, double dt) {
    if (body.isSleeping()) {
      return;
    }

    var angularVelocity = body.angularVelocity();
    if (angularVelocity.lengthSqr() <= 1.0E-8D) {
      return;
    }

    var orientation = body.orientation();
    orientation.rotateX((float) (angularVelocity.x * dt));
    orientation.rotateY((float) (angularVelocity.y * dt));
    orientation.rotateZ((float) (angularVelocity.z * dt));
    orientation.normalize();
  }

  private static void dampLinearVelocity(RigidBody body, double dt) {
    if (body.linearDamp() <= 1.0E-8D) {
      return;
    }
    body.linearVelocity(body.linearVelocity().scale(1.0D - body.linearDamp() * dt));
  }

  private static void dampAngularVelocity(RigidBody body, double dt) {
    if (body.angularDamp() <= 1.0E-8D) {
      return;
    }
    body.angularVelocity(body.angularVelocity().scale(1.0D - body.angularDamp() * dt));
  }

  private BodyIntegrationPhase() {}
}
