package com.binhjcao.physicstorches.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EntityContactSolver {
  private EntityContactSolver() {}

  public static void resolve(Level level, Entity host, RigidBodyState body, RigidBodyCollider collider) {
    AABB search = collider.worldBounds(body).inflate(0.35D, 0.2D, 0.35D);
    for (Entity other : level.getEntities(host, search, candidate -> candidate.isAlive() && !candidate.isSpectator())) {
      applyEntityContact(host, other, body, collider);
    }
  }

  private static void applyEntityContact(Entity host, Entity other, RigidBodyState body, RigidBodyCollider collider) {
    AABB otherBox = other.getBoundingBox();
    AABB bodyBox = collider.worldBounds(body);
    if (!otherBox.intersects(bodyBox)) {
      return;
    }

    body.wake();

    Vec3 closest = closestPointOnBox(otherBox, body.px, body.py, body.pz);
    double dx = body.px - closest.x;
    double dy = body.py - closest.y;
    double dz = body.pz - closest.z;
    double distSq = dx * dx + dy * dy + dz * dz;
    if (distSq < 1.0E-8D) {
      dx = body.px - other.getX();
      dz = body.pz - other.getZ();
      distSq = dx * dx + dz * dz;
      if (distSq < 1.0E-8D) {
        return;
      }
    }

    double invDist = 1.0D / Math.sqrt(distSq);
    Vec3 normal = new Vec3(dx * invDist, dy * invDist, dz * invDist);

    double otherSpeed =
        Math.hypot(other.getX() - other.xOld, other.getZ() - other.zOld);
    otherSpeed = Math.max(otherSpeed, other.getDeltaMovement().horizontalDistance());
    if (otherSpeed < 0.001D && !bodyBox.intersects(otherBox.inflate(0.02D))) {
      return;
    }
    if (otherSpeed < 0.001D) {
      otherSpeed = 0.15D;
    }

    double impulseStrength = Math.min(otherSpeed * 4.0D, 0.85D);
    Vec3 impulse = normal.scale(impulseStrength);

    body.vx += impulse.x * body.invMass;
    body.vy += impulse.y * body.invMass;
    body.vz += impulse.z * body.invMass;

    if (!RigidBodyOrientation.isMostlyFlat(body)) {
      double rx = closest.x - body.px;
      double ry = closest.y - body.py;
      double rz = closest.z - body.pz;
      double torqueX = ry * impulse.z - rz * impulse.y;
      double torqueY = rz * impulse.x - rx * impulse.z;
      double torqueZ = rx * impulse.y - ry * impulse.x;
      body.wx += torqueX * body.invIx * 5.5D;
      body.wy += torqueY * body.invIy * 5.5D;
      body.wz += torqueZ * body.invIz * 5.5D;
    }

    if (normal.y > 0.2D && body.vy < 0.0D) {
      body.vy *= 0.25D;
    }
  }

  private static Vec3 closestPointOnBox(AABB box, double x, double y, double z) {
    double closestX = Math.max(box.minX, Math.min(x, box.maxX));
    double closestY = Math.max(box.minY, Math.min(y, box.maxY));
    double closestZ = Math.max(box.minZ, Math.min(z, box.maxZ));
    return new Vec3(closestX, closestY, closestZ);
  }
}
