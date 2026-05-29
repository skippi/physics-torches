package com.binhjcao.physicstorches.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RigidBodyState {
  public double px;
  public double py;
  public double pz;
  public final Quaternionf orientation = new Quaternionf();
  public final Quaternionf prevOrientation = new Quaternionf();
  public double vx;
  public double vy;
  public double vz;
  public double wx;
  public double wy;
  public double wz;
  public double mass = 0.1D;
  public double invMass = 10.0D;
  public double invIx = 1.0D;
  public double invIy = 1.0D;
  public double invIz = 1.0D;
  public boolean sleeping;
  public boolean onGround;

  public void setPosition(double x, double y, double z) {
    px = x;
    py = y;
    pz = z;
  }

  public void setLinearVelocity(double x, double y, double z) {
    vx = x;
    vy = y;
    vz = z;
  }

  public void setAngularVelocity(double x, double y, double z) {
    wx = x;
    wy = y;
    wz = z;
  }

  public void setDiagonalInertia(double ix, double iy, double iz) {
    invIx = ix > 0.0D ? 1.0D / ix : 0.0D;
    invIy = iy > 0.0D ? 1.0D / iy : 0.0D;
    invIz = iz > 0.0D ? 1.0D / iz : 0.0D;
  }

  public void snapshotOrientation() {
    prevOrientation.set(orientation);
  }

  public Quaternionf orientation(float partialTick) {
    if (partialTick >= 1.0F) {
      return new Quaternionf(orientation);
    }
    return new Quaternionf(prevOrientation).slerp(orientation, partialTick);
  }

  public Vector3f centerOfMassWorld() {
    return new Vector3f((float) px, (float) py, (float) pz);
  }

  public double linearSpeedSq() {
    return vx * vx + vy * vy + vz * vz;
  }

  public double angularSpeedSq() {
    return wx * wx + wy * wy + wz * wz;
  }

  public void wake() {
    sleeping = false;
  }
}
