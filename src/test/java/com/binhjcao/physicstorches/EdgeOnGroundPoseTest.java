package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import com.binhjcao.physicstorches.physics.RigidBody;

class EdgeOnGroundPoseTest {
  private static final double EPS = 1.0E-4D;
  private static final double GROUND_TOP_Y = 1.0D;

  @Test
  void CubeOnGroundBlock_RestsOnEdge_NotOnCorner() {
    var cube = EdgeOnGroundPose.cubeOnGroundBlock(new Vec3(0.5D, 0.0D, 0.5D));

    assertEquals(2, EdgeOnGroundPose.cornersOnPlane(cube, GROUND_TOP_Y, EPS));
    assertTrue(EdgeOnGroundPose.lowestCornerY(cube) < GROUND_TOP_Y - 0.1D);
  }

  @Test
  void CubeOnGroundBlock_DiffersFromCornerPropOrientation() {
    var edgeCube = EdgeOnGroundPose.cubeOnGroundBlock(new Vec3(0.5D, 0.0D, 0.5D));
    var cornerPropCube = cornerPropCubeOnGround(new Vec3(0.5D, 0.0D, 0.5D));

    assertEquals(2, EdgeOnGroundPose.cornersOnPlane(edgeCube, GROUND_TOP_Y, EPS));
    assertEquals(1, EdgeOnGroundPose.cornersOnPlane(cornerPropCube, GROUND_TOP_Y, EPS));
    assertTrue(
        EdgeOnGroundPose.highestCornerY(edgeCube) < EdgeOnGroundPose.highestCornerY(cornerPropCube));
  }

  private static RigidBody cornerPropCubeOnGround(Vec3 horizontalCenter) {
    Quaternionf orientation =
        new Quaternionf()
            .rotateX((float) (Math.PI / 4.0D))
            .rotateY((float) (Math.PI / 4.0D));
    double centerY = EdgeOnGroundPose.centerYForLowestCornerOnPlane(orientation, GROUND_TOP_Y);
    return new RigidBody(
        BoxCollider.cube(0.5D),
        new Vec3(horizontalCenter.x, centerY, horizontalCenter.z),
        orientation);
  }
}
