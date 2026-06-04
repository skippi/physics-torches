package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import com.binhjcao.physicstorches.physics.NarrowPhase;
import com.binhjcao.physicstorches.physics.RigidBody;

class NarrowPhaseTest {
  private static final double EPS = 1.0E-5D;

  @Test
  void FindContactCandidate_StoresNormalFromBodyAToBodyB_CubeRestsOnGroundBlock() {
    var cube = RigidBody.cube(0.5D, new Vec3(0.5D, 1.45D, 0.5D));
    var block = groundBlock();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();

    assertEquals(0.0D, contact.normal().x, EPS);
    assertEquals(-1.0D, contact.normal().y, EPS);
    assertEquals(0.0D, contact.normal().z, EPS);
  }

  @Test
  void FindContactCandidate_ReturnsCandidate_CubeIsSeparatedByManifoldTolerance() {
    var cube = RigidBody.cube(0.5D, new Vec3(0.5D, 1.005D + 0.5D, 0.5D));
    var block = groundBlock();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();

    assertEquals(-0.005D, contact.penetration(), EPS);
    assertEquals(-1.0D, contact.normal().y, EPS);
  }

  private static RigidBody groundBlock() {
    return RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
  }
}
