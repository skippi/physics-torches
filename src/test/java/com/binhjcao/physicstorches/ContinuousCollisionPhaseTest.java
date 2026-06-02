package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import com.binhjcao.physicstorches.physics.BodyIntegrationPhase;
import com.binhjcao.physicstorches.physics.ContactConstraint;
import com.binhjcao.physicstorches.physics.ContinuousCollisionPhase;
import com.binhjcao.physicstorches.physics.RigidBody;

class ContinuousCollisionPhaseTest {
  private static final double DT = 1.0D / 20.0D;

  @Test
  void Solve_KeepsBodyAboveBlockTopAndStopsInwardVelocity_ThinBodyFallsOntoGroundBlock() {
    var body =
        new RigidBody(
            BoxCollider.box(0.0625D, 0.3125D, 0.0625D),
            new Vec3(0.5D, 1.75D, 0.5D));
    body.linearVelocity(new Vec3(0.0D, -25.0D, 0.0D));
    var block = RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
    var constraints = new ArrayList<ContactConstraint>();
    integrateWithCcd(body, block, constraints);

    assertTrue(body.position().y > 1.0D, "body tunneled, y=" + body.position().y);
    assertTrue(body.linearVelocity().y >= -1.0E-5D);
  }

  @Test
  void Solve_KeepsBodyAboveBlockTop_FastAngledThrowOntoGroundBlock() {
    var body =
        new RigidBody(
            BoxCollider.box(0.0625D, 0.3125D, 0.0625D),
            new Vec3(0.5D, 2.5D, 0.5D));
    body.linearVelocity(new Vec3(12.0D, -40.0D, 8.0D));
    var block = RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
    integrateWithCcd(body, block, new ArrayList<>());

    assertTrue(body.position().y >= 1.0D, "body tunneled below floor top, y=" + body.position().y);
    assertTrue(body.position().y < 2.5D, "body did not move, y=" + body.position().y);
  }

  @Test
  void Solve_KeepsBodyAboveBlockTop_FastHorizontalThrowOverGroundBlock() {
    var body =
        new RigidBody(
            BoxCollider.box(0.0625D, 0.3125D, 0.0625D),
            new Vec3(-1.0D, 1.35D, 0.5D));
    body.linearVelocity(new Vec3(25.0D, -8.0D, 0.0D));
    var block = RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
    integrateWithCcd(body, block, new ArrayList<>());

    assertTrue(body.position().y >= 1.0D, "body tunneled through floor, y=" + body.position().y);
    assertTrue(
        body.position().x > -0.5D,
        "body did not advance toward floor, x=" + body.position().x);
  }

  private static void integrateWithCcd(
      RigidBody body, RigidBody block, List<ContactConstraint> constraints) {
    body.snapshotPrevPosition();
    BodyIntegrationPhase.integrateVelocity(body, DT);
    ContinuousCollisionPhase.solve(List.of(body, block), constraints, DT);
  }
}
