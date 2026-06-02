package com.binhjcao.physicstorches;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import com.binhjcao.physicstorches.physics.RigidBody;

final class EdgeOnGroundPose {
  private static final double HALF = 0.5D;
  private static final double GROUND_TOP_Y = 1.0D;

  private EdgeOnGroundPose() {}

  static RigidBody cubeOnGroundBlock(Vec3 horizontalCenter) {
    Quaternionf orientation = edgeOnGroundOrientation();
    double centerY = centerYForSupportingEdgeOnPlane(orientation, GROUND_TOP_Y);
    return new RigidBody(
        BoxCollider.cube(HALF),
        new Vec3(horizontalCenter.x, centerY, horizontalCenter.z),
        orientation);
  }

  static Quaternionf edgeOnGroundOrientation() {
    return new Quaternionf().rotateX((float) (Math.PI / 4.0D));
  }

  static double centerYForSupportingEdgeOnPlane(Quaternionf orientation, double planeY) {
    var collider = BoxCollider.cube(HALF);
    Vec3 center = Vec3.ZERO;
    double maxY = Double.NEGATIVE_INFINITY;
    for (Vec3 corner : collider.worldCorners(center, orientation)) {
      maxY = Math.max(maxY, corner.y);
    }
    return planeY - maxY;
  }

  static double centerYForLowestCornerOnPlane(Quaternionf orientation, double planeY) {
    var collider = BoxCollider.cube(HALF);
    Vec3 center = Vec3.ZERO;
    double minY = Double.POSITIVE_INFINITY;
    for (Vec3 corner : collider.worldCorners(center, orientation)) {
      minY = Math.min(minY, corner.y);
    }
    return planeY - minY;
  }

  static double lowestCornerY(RigidBody body) {
    double minY = Double.POSITIVE_INFINITY;
    for (Vec3 corner : body.collider().worldCorners(body.position(), body.orientation())) {
      minY = Math.min(minY, corner.y);
    }
    return minY;
  }

  static double highestCornerY(RigidBody body) {
    double maxY = Double.NEGATIVE_INFINITY;
    for (Vec3 corner : body.collider().worldCorners(body.position(), body.orientation())) {
      maxY = Math.max(maxY, corner.y);
    }
    return maxY;
  }

  static int cornersOnPlane(RigidBody body, double planeY, double tolerance) {
    int count = 0;
    for (Vec3 corner : body.collider().worldCorners(body.position(), body.orientation())) {
      if (Math.abs(corner.y - planeY) <= tolerance) {
        count++;
      }
    }
    return count;
  }
}
