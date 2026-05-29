package com.binhjcao.physicstorches.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class RigidBodyPhysics {
  public static final double TICK_DT = 0.05D;
  private static final double GRAVITY_PER_TICK = 0.12D;
  private static final double SLEEP_LINEAR = 1.0E-4D;
  private static final double SLEEP_ANGULAR = 1.0E-4D;
  private RigidBodyPhysics() {}

  public static void step(Level level, Entity host, RigidBodyState body, RigidBodyCollider collider) {
    if (body.sleeping) {
      return;
    }

    double gravityForce = body.mass * (GRAVITY_PER_TICK / TICK_DT);
    RigidBodyIntegrator.integrate(body, TICK_DT, 0.0D, -gravityForce, 0.0D, 0.0D, 0.0D, 0.0D);

    BlockContactSolver.resolve(level, body, collider);
    EntityContactSolver.resolve(level, host, body, collider);

    if (body.onGround) {
      body.vx *= 0.52D;
      body.vz *= 0.52D;
      if (RigidBodyOrientation.isMostlyFlat(body)) {
        dampFlatAngularVelocity(body);
      } else {
        body.wx *= 0.93D;
        body.wy *= 0.93D;
        body.wz *= 0.93D;
      }
    }

    if (body.onGround && body.linearSpeedSq() < SLEEP_LINEAR && body.angularSpeedSq() < SLEEP_ANGULAR) {
      body.vx = 0.0D;
      body.vy = 0.0D;
      body.vz = 0.0D;
      body.wx = 0.0D;
      body.wy = 0.0D;
      body.wz = 0.0D;
      body.sleeping = true;
    }
  }

  private static void dampFlatAngularVelocity(RigidBodyState body) {
    org.joml.Vector3f stickAxis = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
    body.orientation.transform(stickAxis);
    double along =
        body.wx * stickAxis.x + body.wy * stickAxis.y + body.wz * stickAxis.z;
    body.wx = stickAxis.x * along * 0.85D;
    body.wy = stickAxis.y * along * 0.85D;
    body.wz = stickAxis.z * along * 0.85D;
  }
}
