package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import com.binhjcao.physicstorches.physics.ContactConstraint;
import com.binhjcao.physicstorches.physics.ContactConstraintPhase;
import com.binhjcao.physicstorches.physics.ContactManifoldPhase;
import com.binhjcao.physicstorches.physics.NarrowPhase;
import com.binhjcao.physicstorches.physics.RigidBody;
import com.binhjcao.physicstorches.physics.VelocitySolverPhase;

class VelocitySolverPhaseTest {
  private static final double EPS = 1.0E-5D;

  @Test
  void Solve_MakesBodyAVelocityAlongNormalNonNegative_CubeHasDownwardVelocityPenetratingGroundBlock() {
    var cube = RigidBody.cube(0.5D, new Vec3(0.5D, 1.45D, 0.5D));
    var block = groundBlock();
    cube.linearVelocity(new Vec3(0.0D, -1.0D, 0.0D));

    var constraint = groundConstraint(cube, block);
    VelocitySolverPhase.solve(List.of(constraint));

    assertTrue(cube.linearVelocity().y >= -EPS);
  }

  private static ContactConstraint groundConstraint(RigidBody cube, RigidBody block) {
    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);
    return ContactConstraintPhase.setupContactConstraint(manifold, 1.0D / 20.0D);
  }

  private static RigidBody groundBlock() {
    return RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
  }
}
