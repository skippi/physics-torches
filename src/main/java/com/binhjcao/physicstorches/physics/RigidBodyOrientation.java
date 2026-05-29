package com.binhjcao.physicstorches.physics;

import org.joml.Vector3f;

public final class RigidBodyOrientation {
  private static final Vector3f LOCAL_UP = new Vector3f(0.0F, 1.0F, 0.0F);
  private static final Vector3f WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);

  private RigidBodyOrientation() {}

  public static float localUpDotWorldUp(RigidBodyState body) {
    Vector3f axis = new Vector3f(LOCAL_UP);
    body.orientation.transform(axis);
    return axis.dot(WORLD_UP);
  }

  public static boolean isMostlyFlat(RigidBodyState body) {
    return Math.abs(localUpDotWorldUp(body)) < 0.55F;
  }

  public static boolean isMostlyUpright(RigidBodyState body) {
    return localUpDotWorldUp(body) > 0.85F;
  }
}
