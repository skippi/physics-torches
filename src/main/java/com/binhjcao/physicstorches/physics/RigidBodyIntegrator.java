package com.binhjcao.physicstorches.physics;

public final class RigidBodyIntegrator {
  private static final double AIR_LINEAR_DRAG = 1.0D;
  private static final double GROUND_LINEAR_DRAG = 0.996D;
  private static final double AIR_ANGULAR_DRAG = 0.985D;
  private static final double GROUND_ANGULAR_DRAG = 0.97D;

  private RigidBodyIntegrator() {}

  public static void integrate(RigidBodyState body, double dt, double fx, double fy, double fz, double tx, double ty, double tz) {
    if (body.sleeping) {
      return;
    }

    body.vx += fx * body.invMass * dt;
    body.vy += fy * body.invMass * dt;
    body.vz += fz * body.invMass * dt;

    body.wx += tx * body.invIx * dt;
    body.wy += ty * body.invIy * dt;
    body.wz += tz * body.invIz * dt;

    double linearDrag = body.onGround ? GROUND_LINEAR_DRAG : AIR_LINEAR_DRAG;
    double angularDrag = body.onGround ? GROUND_ANGULAR_DRAG : AIR_ANGULAR_DRAG;
    body.vx *= linearDrag;
    body.vy *= linearDrag;
    body.vz *= linearDrag;
    body.wx *= angularDrag;
    body.wy *= angularDrag;
    body.wz *= angularDrag;

    body.px += body.vx * dt;
    body.py += body.vy * dt;
    body.pz += body.vz * dt;

    double angularSpeed = Math.sqrt(body.wx * body.wx + body.wy * body.wy + body.wz * body.wz);
    if (angularSpeed > 1.0E-6D) {
      float angle = (float) (angularSpeed * dt);
      body.orientation.rotateAxis(
          angle,
          (float) (body.wx / angularSpeed),
          (float) (body.wy / angularSpeed),
          (float) (body.wz / angularSpeed));
    }
  }
}
