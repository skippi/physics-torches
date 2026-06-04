package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.binhjcao.physicstorches.physics.RigidBody;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RigidBodyTest {
  @Test
  void ClampAngularVelocity_LeavesZeroAngularVelocityUnchanged() {
    var body = RigidBody.cube(0.5D);

    body.clampAngularVelocity();

    assertEquals(Vec3.ZERO, body.angularVelocity());
  }

  @Test
  void ClampAngularVelocity_LeavesBelowLimitAngularVelocityUnchanged() {
    var body = RigidBody.cube(0.5D);
    body.maxAngularVelocity(2.0D);
    body.angularVelocity(new Vec3(1.0D, 0.0D, 0.0D));

    body.clampAngularVelocity();

    assertEquals(new Vec3(1.0D, 0.0D, 0.0D), body.angularVelocity());
  }
}
