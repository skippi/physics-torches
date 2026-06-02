package com.binhjcao.physicstorches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import com.binhjcao.physicstorches.physics.ContactManifoldPhase;
import com.binhjcao.physicstorches.physics.NarrowPhase;
import com.binhjcao.physicstorches.physics.RigidBody;

class ContactManifoldPhaseTest {
  private static final double EPS = 1.0E-5D;
  private static final double LOOSE_EPS = 0.15D;
  private static final double GROUND_TOP_Y = 1.0D;

  @Test
  void GenerateContactManifold_PlacesContactPointsOnCubeBottomAndBlockTop_CubeRestsOnGroundBlock() {
    var cube = RigidBody.cube(0.5D, new Vec3(0.5D, 1.45D, 0.5D));
    var block = groundBlock();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);

    assertFalse(manifold.contactsOnA().isEmpty());
    for (int i = 0; i < manifold.contactsOnA().size(); i++) {
      Vec3 contactA = manifold.contactsOnA().get(i);
      Vec3 contactB = manifold.contactsOnB().get(i);
      assertEquals(0.95D, contactA.y, EPS);
      assertEquals(1.0D, contactB.y, EPS);
      assertTrue(contactB.subtract(contactA).dot(manifold.normal()) < 0.0D);
    }
  }

  @Test
  void GenerateContactManifold_ProducesMultipleContactsAlongEdge_CubeTiltedOnEdgeOnGroundBlock() {
    var cube = cubeTiltedOnEdgeOnGround();
    var block = wideFloor();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);

    double floorTopY = floorTopY(block);

    assertTrue(manifold.contactsOnA().size() >= 2);
    assertRestingEdgeContacts(manifold.contactsOnA(), floorTopY, cube.position().z);
    assertRestingEdgeContacts(manifold.contactsOnB(), floorTopY, cube.position().z);
    for (int i = 0; i < manifold.contactsOnA().size(); i++) {
      Vec3 contactA = manifold.contactsOnA().get(i);
      Vec3 contactB = manifold.contactsOnB().get(i);
      assertEquals(floorTopY, contactB.y, LOOSE_EPS, "contactB=" + contactB);
    }
  }

  @Test
  void GenerateContactManifold_UsesVerticalNormal_CubeTiltedOnEdgeOnGroundBlock() {
    var cube = cubeTiltedOnEdgeOnGround();
    var block = wideFloor();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);

    assertTrue(Math.abs(manifold.normal().y) > 0.5D, "normal=" + manifold.normal());
    assertEquals(0.0D, manifold.normal().x, 0.15D, "normal=" + manifold.normal());
    assertEquals(0.0D, manifold.normal().z, 0.15D, "normal=" + manifold.normal());
  }

  @Test
  void GenerateContactManifold_PlacesContactsOnRestingEdge_CubeTiltedOnEdgeOnGroundBlock() {
    var cube = cubeTiltedOnEdgeOnGround();
    var block = wideFloor();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);

    assertRestingEdgeContacts(manifold.contactsOnA(), floorTopY(block), cube.position().z);
    for (Vec3 contactA : manifold.contactsOnA()) {
      assertTrue(contactA.y <= floorTopY(block) + LOOSE_EPS, "contactA=" + contactA);
    }
  }

  @Test
  void GenerateContactManifold_ExcludesNonOverlappingFacePoints_CubeTiltedOnEdgeOnGroundBlock() {
    var cube = cubeTiltedOnEdgeOnGround();
    var block = wideFloor();

    var contact = NarrowPhase.findContactCandidate(cube, block).orElseThrow();
    var manifold = ContactManifoldPhase.generateContactManifold(contact);

    double floorTopY = floorTopY(block);
    for (Vec3 contactA : manifold.contactsOnA()) {
      assertTrue(
          contactA.y >= floorTopY - LOOSE_EPS,
          "contact should overlap floor, contactA=" + contactA);
    }
  }

  private static void assertRestingEdgeContacts(
      java.util.List<Vec3> contacts, double floorTopY, double edgeZ) {
    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    int onEdge = 0;
    for (Vec3 contact : contacts) {
      if (contact.y < floorTopY - LOOSE_EPS || Math.abs(contact.z - edgeZ) > LOOSE_EPS) {
        continue;
      }
      onEdge++;
      minX = Math.min(minX, contact.x);
      maxX = Math.max(maxX, contact.x);
    }
    assertTrue(onEdge >= 2, "contacts=" + contacts);
    assertTrue(maxX - minX > 0.75D, "contacts=" + contacts);
  }

  private static double floorTopY(RigidBody floor) {
    return floor.position().y + floor.collider().halfExtents().y;
  }

  private static RigidBody cubeTiltedOnEdgeOnGround() {
    return EdgeOnGroundPose.cubeOnGroundBlock(new Vec3(0.5D, 0.0D, 0.5D));
  }

  private static RigidBody groundBlock() {
    return RigidBody.frozenFromBlockAabb(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
  }

  private static RigidBody wideFloor() {
    RigidBody floor = new RigidBody(BoxCollider.box(4.5D, 0.05D, 4.5D), new Vec3(0.5D, 0.95D, 0.5D));
    floor.freeze(true);
    floor.gravityScale(0.0D);
    floor.friction(1.0D);
    return floor;
  }
}
