package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BroadPhaseSequentialTest {
  @Test
  void FindActiveBodies_ReturnsNoPairs_BodyListIsEmpty() {
    assertTrue(BroadPhaseSequential.findActiveBodies(List.of()).isEmpty());
  }

  @Test
  void FindActiveBodies_ReturnsNoPairs_OnlyOneActiveBodyIsPresent() {
    var body = RigidBody.cube(0.5D, Vec3.ZERO);

    assertTrue(BroadPhaseSequential.findActiveBodies(List.of(body)).isEmpty());
  }

  @Test
  void FindActiveBodies_ReturnsOnePair_TwoActiveBodiesOverlap() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));

    var pairs = BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB));

    assertEquals(1, pairs.size());
    assertPair(pairs.getFirst(), bodyA, bodyB);
  }

  @Test
  void FindActiveBodies_ReturnsNoPairs_TwoActiveBodiesAreSeparated() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(5.0D, 0.0D, 0.0D));

    assertTrue(BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB)).isEmpty());
  }

  @Test
  void FindActiveBodies_ReturnsNoPairs_BothOverlappingBodiesAreFrozen() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    bodyA.freeze(true);
    bodyB.freeze(true);

    assertTrue(BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB)).isEmpty());
  }

  @Test
  void FindActiveBodies_ReturnsOnePair_FrozenBodyOverlapsActiveBody() {
    var frozen = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var active = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    frozen.freeze(true);

    var pairs = BroadPhaseSequential.findActiveBodies(List.of(frozen, active));

    assertEquals(1, pairs.size());
    assertPair(pairs.getFirst(), frozen, active);
  }

  @Test
  void FindActiveBodies_ReturnsNoPairs_BothOverlappingBodiesAreSleeping() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    bodyA.enterSleep();
    bodyB.enterSleep();

    assertTrue(BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB)).isEmpty());
  }

  @Test
  void FindActiveBodies_ReturnsOnePair_SleepingBodyOverlapsAwakeBody() {
    var sleeping = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var awake = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    sleeping.enterSleep();

    var pairs = BroadPhaseSequential.findActiveBodies(List.of(sleeping, awake));

    assertEquals(1, pairs.size());
    assertPair(pairs.getFirst(), sleeping, awake);
  }

  @Test
  void FindActiveBodies_ReturnsOnlyOverlappingPairs_MultipleBodiesArePresent() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    var bodyC = RigidBody.cube(0.5D, new Vec3(10.0D, 0.0D, 0.0D));

    var pairs = BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB, bodyC));

    assertEquals(1, pairs.size());
    assertPair(pairs.getFirst(), bodyA, bodyB);
  }

  @Test
  void FindActiveBodies_ReturnsPairsInIndexOrder_ThreeBodiesMutuallyOverlap() {
    var bodyA = RigidBody.cube(0.5D, new Vec3(0.0D, 0.0D, 0.0D));
    var bodyB = RigidBody.cube(0.5D, new Vec3(0.5D, 0.0D, 0.0D));
    var bodyC = RigidBody.cube(0.5D, new Vec3(0.25D, 0.0D, 0.0D));

    var pairs = BroadPhaseSequential.findActiveBodies(List.of(bodyA, bodyB, bodyC));

    assertEquals(3, pairs.size());
    assertPair(pairs.get(0), bodyA, bodyB);
    assertPair(pairs.get(1), bodyA, bodyC);
    assertPair(pairs.get(2), bodyB, bodyC);
  }

  private static void assertPair(BroadPhasePair pair, RigidBody expectedA, RigidBody expectedB) {
    assertEquals(expectedA, pair.a());
    assertEquals(expectedB, pair.b());
  }
}
