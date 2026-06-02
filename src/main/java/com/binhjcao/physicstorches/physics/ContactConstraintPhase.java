package com.binhjcao.physicstorches.physics;

import java.util.ArrayList;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import com.binhjcao.physicstorches.OrientedTransform;

public class ContactConstraintPhase {
  private static final float RESTITUTION_VELOCITY_THRESHOLD = 0.1F;

  public static ContactConstraint setupContactConstraint(
      ContactManifold manifold, double deltaTime) {
    RigidBody a = manifold.a();
    RigidBody b = manifold.b();
    Vec3 normal = manifold.normal();
    if (normal.lengthSqr() < 1.0E-8D) {
      normal = new Vec3(0.0D, 1.0D, 0.0D);
    } else {
      normal = normal.normalize();
    }

    Vec3 tangent1 = buildTangent(normal);
    Vec3 tangent2 = normal.cross(tangent1).normalize();
    float restitution = (float) Math.max(a.bounce(), b.bounce());

    var points = new ArrayList<ContactConstraintPoint>();
    int contactCount =
        Math.min(
            manifold.contactsOnA().size(),
            Math.min(manifold.contactsOnB().size(), ContactManifold.MAX_CONTACTS));
    Quaternionf orientA = a.orientation();
    Quaternionf orientB = b.orientation();
    for (int i = 0; i < contactCount; i++) {
      Vec3 contactA = manifold.contactsOnA().get(i);
      Vec3 contactB = manifold.contactsOnB().get(i);
      Vec3 leverArmA = contactA.subtract(a.position());
      Vec3 leverArmB = contactB.subtract(b.position());
      Vec3 localContactA = OrientedTransform.toLocalPoint(contactA, a.position(), orientA);
      Vec3 localContactB = OrientedTransform.toLocalPoint(contactB, b.position(), orientB);

      float normalMass =
          (float)
              (a.effectiveMassInvAtContact(leverArmA, normal)
                  + b.effectiveMassInvAtContact(leverArmB, normal));
      float tangentMass1 =
          (float)
              (a.effectiveMassInvAtContact(leverArmA, tangent1)
                  + b.effectiveMassInvAtContact(leverArmB, tangent1));
      float tangentMass2 =
          (float)
              (a.effectiveMassInvAtContact(leverArmA, tangent2)
                  + b.effectiveMassInvAtContact(leverArmB, tangent2));

      float separation = (float) contactB.subtract(contactA).dot(normal);
      float positionBias = positionBiasFromSeparation(separation);

      Vec3 relativeVelocity =
          b.velocityAtPoint(leverArmB).subtract(a.velocityAtPoint(leverArmA));
      float normalVelocity = (float) relativeVelocity.dot(normal);
      float velocityBias =
          normalVelocity < -RESTITUTION_VELOCITY_THRESHOLD
              ? -restitution * normalVelocity
              : 0.0F;

      points.add(
          new ContactConstraintPoint(
              leverArmA,
              leverArmB,
              localContactA,
              localContactB,
              normalMass,
              tangentMass1,
              tangentMass2,
              velocityBias,
              positionBias,
              0.0F,
              0.0F,
              0.0F));
    }

    return new ContactConstraint(a, b, normal, tangent1, tangent2, points);
  }

  public static float positionBiasFromSeparation(float separation) {
    float overlap =
        Math.max(-separation - (float) Physics.CONTACT_MAX_ALLOWED_PENETRATION, 0.0F);
    return (float) (Physics.BAUMGARTE_STABILIZATION_FACTOR * overlap);
  }

  private static Vec3 buildTangent(Vec3 normal) {
    Vec3 axis = Math.abs(normal.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
    return normal.cross(axis).normalize();
  }

  private ContactConstraintPhase() {}
}
