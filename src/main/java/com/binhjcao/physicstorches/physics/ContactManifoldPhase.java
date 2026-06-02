package com.binhjcao.physicstorches.physics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.OrientedTransform;

public class ContactManifoldPhase {
  private static final Vec3[] LOCAL_FACE_NORMALS = {
    new Vec3(1, 0, 0),
    new Vec3(-1, 0, 0),
    new Vec3(0, 1, 0),
    new Vec3(0, -1, 0),
    new Vec3(0, 0, 1),
    new Vec3(0, 0, -1)
  };

  private static final int[][] FACE_CORNER_SIGNS = {
    {1, 1, 1}, {1, 1, -1}, {1, -1, -1}, {1, -1, 1},
    {-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}, {-1, -1, 1},
    {1, 1, 1}, {-1, 1, 1}, {-1, 1, -1}, {1, 1, -1},
    {1, -1, 1}, {-1, -1, 1}, {-1, -1, -1}, {1, -1, -1},
    {1, 1, 1}, {1, -1, 1}, {-1, -1, 1}, {-1, 1, 1},
    {1, 1, -1}, {1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}
  };

  public static ContactManifold generateContactManifold(NarrowPhaseContact contact) {
    RigidBody a = contact.a();
    RigidBody b = contact.b();
    Vec3 normal = contact.normal();
    Vec3 centerA = a.position();
    Vec3 centerB = b.position();
    Quaternionf orientA = a.orientation();
    Quaternionf orientB = b.orientation();
    Collider colliderA = a.collider();
    Collider colliderB = b.collider();

    int refFaceA = mostAlignedFace(orientA, normal);
    int refFaceB = leastAlignedFace(orientB, normal);
    Vec3 faceNormalA = faceNormalWorld(orientA, refFaceA);
    Vec3 faceNormalB = faceNormalWorld(orientB, refFaceB);
    boolean aIsReference = faceNormalA.dot(normal) >= -faceNormalB.dot(normal);

    RigidBody reference = aIsReference ? a : b;
    RigidBody incident = aIsReference ? b : a;
    Vec3 refCenter = aIsReference ? centerA : centerB;
    Vec3 incCenter = aIsReference ? centerB : centerA;
    Quaternionf refOrient = aIsReference ? orientA : orientB;
    Quaternionf incOrient = aIsReference ? orientB : orientA;
    Collider refCollider = aIsReference ? colliderA : colliderB;
    Collider incCollider = aIsReference ? colliderB : colliderA;
    int refFace = aIsReference ? refFaceA : refFaceB;
    int incFace = aIsReference ? leastAlignedFace(orientB, normal) : mostAlignedFace(orientA, normal);

    Vec3[] refVerts = faceVertices(refCenter, refOrient, refCollider, refFace);
    Vec3[] incVerts = faceVertices(incCenter, incOrient, incCollider, incFace);
    Vec3 refFaceNormal = faceNormalWorld(refOrient, refFace);

    List<Vec3> clipped = clipIncidentFace(incVerts, refVerts, refFaceNormal);
    if (clipped.isEmpty()) {
      clipped = List.of(contact.point());
    }
    clipped = filterPointsOverlappingReference(clipped, refCenter, refOrient, refCollider);
    if (clipped.isEmpty()) {
      clipped = List.of(contact.point());
    }
    clipped = trimContacts(clipped, refCenter, normal, ContactManifold.MAX_CONTACTS);

    var contactsOnA = new ArrayList<Vec3>();
    var contactsOnB = new ArrayList<Vec3>();
    for (Vec3 incidentPoint : clipped) {
      Vec3 referencePoint = projectOntoFacePlane(incidentPoint, refVerts[0], refFaceNormal);
      if (aIsReference) {
        contactsOnA.add(referencePoint);
        contactsOnB.add(incidentPoint);
      } else {
        contactsOnA.add(incidentPoint);
        contactsOnB.add(referencePoint);
      }
    }

    return new ContactManifold(
        a,
        b,
        contact.point(),
        normal,
        contact.penetration(),
        contactsOnA,
        contactsOnB);
  }

  private static List<Vec3> clipIncidentFace(Vec3[] incident, Vec3[] reference, Vec3 refNormal) {
    List<Vec3> polygon = new ArrayList<>(List.of(incident));
    for (int i = 0; i < 4; i++) {
      Vec3 edgeStart = reference[i];
      Vec3 edge = reference[(i + 1) % 4].subtract(edgeStart);
      Vec3 planeNormal = refNormal.cross(edge);
      if (planeNormal.lengthSqr() < 1.0E-8D) {
        continue;
      }
      planeNormal = planeNormal.normalize();
      if (planeNormal.dot(reference[(i + 2) % 4]) > planeNormal.dot(edgeStart)) {
        planeNormal = planeNormal.scale(-1.0D);
      }
      polygon = clipPolygonAgainstPlane(polygon, edgeStart, planeNormal);
      if (polygon.isEmpty()) {
        return polygon;
      }
    }
    return polygon;
  }

  private static List<Vec3> clipPolygonAgainstPlane(
      List<Vec3> polygon, Vec3 planePoint, Vec3 planeNormal) {
    if (polygon.isEmpty()) {
      return polygon;
    }

    List<Vec3> output = new ArrayList<>();
    Vec3 previous = polygon.getLast();
    double previousDistance = planeNormal.dot(previous.subtract(planePoint));

    for (Vec3 current : polygon) {
      double currentDistance = planeNormal.dot(current.subtract(planePoint));
      if (currentDistance <= 1.0E-4D) {
        if (previousDistance > 1.0E-4D) {
          output.add(intersectSegmentWithPlane(previous, current, planePoint, planeNormal));
        }
        output.add(current);
      } else if (previousDistance <= 1.0E-4D) {
        output.add(intersectSegmentWithPlane(previous, current, planePoint, planeNormal));
      }
      previous = current;
      previousDistance = currentDistance;
    }
    return output;
  }

  private static Vec3 intersectSegmentWithPlane(
      Vec3 start, Vec3 end, Vec3 planePoint, Vec3 planeNormal) {
    Vec3 segment = end.subtract(start);
    double denominator = planeNormal.dot(segment);
    if (Math.abs(denominator) < 1.0E-8D) {
      return start;
    }
    double t = planeNormal.dot(planePoint.subtract(start)) / denominator;
    t = Math.clamp(t, 0.0D, 1.0D);
    return start.add(segment.scale(t));
  }

  private static List<Vec3> filterPointsOverlappingReference(
      List<Vec3> points, Vec3 refCenter, Quaternionf refOrient, Collider refCollider) {
    var filtered = new ArrayList<Vec3>();
    double tolerance = Physics.SURFACE_TOLERANCE;
    for (Vec3 point : points) {
      if (overlapsReference(point, refCenter, refOrient, refCollider, tolerance)) {
        filtered.add(point);
      }
    }
    return filtered;
  }

  private static boolean overlapsReference(
      Vec3 point, Vec3 refCenter, Quaternionf refOrient, Collider refCollider, double tolerance) {
    Vec3 local = OrientedTransform.toLocalPoint(point, refCenter, refOrient);
    Vec3 half = refCollider.halfExtents();
    return Math.abs(local.x) <= half.x + tolerance
        && Math.abs(local.y) <= half.y + tolerance
        && Math.abs(local.z) <= half.z + tolerance;
  }

  private static Vec3 projectOntoFacePlane(Vec3 point, Vec3 planePoint, Vec3 planeNormal) {
    double distance = planeNormal.dot(point.subtract(planePoint));
    return point.subtract(planeNormal.scale(distance));
  }

  private static List<Vec3> trimContacts(
      List<Vec3> contacts, Vec3 refCenter, Vec3 normal, int maxContacts) {
    if (contacts.size() <= maxContacts) {
      return contacts;
    }

    List<Vec3> sorted = new ArrayList<>(contacts);
    sorted.sort(
        (left, right) ->
            Double.compare(
                left.subtract(refCenter).dot(normal), right.subtract(refCenter).dot(normal)));
    return sorted.subList(0, maxContacts);
  }

  private static int mostAlignedFace(Quaternionf orientation, Vec3 normal) {
    int bestFace = 0;
    double bestDot = Double.NEGATIVE_INFINITY;
    for (int face = 0; face < LOCAL_FACE_NORMALS.length; face++) {
      double dot = faceNormalWorld(orientation, face).dot(normal);
      if (dot > bestDot) {
        bestDot = dot;
        bestFace = face;
      }
    }
    return bestFace;
  }

  private static int leastAlignedFace(Quaternionf orientation, Vec3 normal) {
    int bestFace = 0;
    double bestDot = Double.POSITIVE_INFINITY;
    for (int face = 0; face < LOCAL_FACE_NORMALS.length; face++) {
      double dot = faceNormalWorld(orientation, face).dot(normal);
      if (dot < bestDot) {
        bestDot = dot;
        bestFace = face;
      }
    }
    return bestFace;
  }

  private static Vec3[] faceVertices(
      Vec3 center, Quaternionf orientation, Collider collider, int face) {
    Vec3 half = collider.halfExtents();
    Vec3[] vertices = new Vec3[4];
    for (int i = 0; i < 4; i++) {
      int[] signs = FACE_CORNER_SIGNS[face * 4 + i];
      Vec3 local =
          new Vec3(signs[0] * half.x, signs[1] * half.y, signs[2] * half.z);
      vertices[i] = OrientedTransform.toWorldPoint(local, center, orientation);
    }
    return vertices;
  }

  private static Vec3 faceNormalWorld(Quaternionf orientation, int face) {
    return OrientedTransform.toWorldDirection(LOCAL_FACE_NORMALS[face], orientation).normalize();
  }

  private ContactManifoldPhase() {}
}
