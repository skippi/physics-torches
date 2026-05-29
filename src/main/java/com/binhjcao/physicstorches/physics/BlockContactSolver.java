package com.binhjcao.physicstorches.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public final class BlockContactSolver {
  private static final double POSITION_SLOP = 0.55D;
  private static final double RESTITUTION = 0.15D;
  private static final double CONTACT_FRICTION = 2.2D;
  private static final double GROUND_WEIGHT = 0.12D;

  private BlockContactSolver() {}

  public static void resolve(Level level, RigidBodyState body, RigidBodyCollider collider) {
    Vector3f[] corners = new Vector3f[8];
    for (int i = 0; i < 8; i++) {
      corners[i] = new Vector3f();
    }
    collider.worldCorners(body, corners);

    body.onGround = false;
    double totalLift = 0.0D;
    int groundHits = 0;

    for (Vector3f corner : corners) {
      Contact contact = findBlockContact(level, corner);
      if (contact == null) {
        contact = findGroundContact(level, corner);
      }
      if (contact == null) {
        continue;
      }

      if (contact.normal.y > 0.5D) {
        body.onGround = true;
        groundHits++;
        totalLift = Math.max(totalLift, contact.penetration);
      }

      boolean ground = contact.normal.y > 0.5D;
      if (!RigidBodyOrientation.isMostlyFlat(body)) {
        applyContactImpulse(body, contact, corner);
        applyContactFriction(body, contact, corner);
      } else if (ground) {
        applyContactFriction(body, contact, corner);
      }
      pushOutOfBlock(body, contact);
    }

    if (groundHits > 0 && totalLift > 1.0E-4D) {
      body.py += totalLift * POSITION_SLOP;
      if (body.vy < 0.0D) {
        body.vy = 0.0D;
      }
    }
  }

  private static Contact findBlockContact(Level level, Vector3f point) {
    int blockX = (int) Math.floor(point.x);
    int blockY = (int) Math.floor(point.y);
    int blockZ = (int) Math.floor(point.z);

    Contact best = null;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
          BlockPos pos = new BlockPos(blockX + dx, blockY + dy, blockZ + dz);
          BlockState state = level.getBlockState(pos);
          VoxelShape shape = state.getCollisionShape(level, pos);
          if (shape.isEmpty()) {
            continue;
          }
          AABB blockBox = shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
          Contact contact = penetration(point, blockBox);
          if (contact != null && (best == null || contact.penetration > best.penetration)) {
            best = contact;
          }
        }
      }
    }
    return best;
  }

  private static Contact penetration(Vector3f point, AABB blockBox) {
    if (point.x < blockBox.minX
        || point.x > blockBox.maxX
        || point.y < blockBox.minY
        || point.y > blockBox.maxY
        || point.z < blockBox.minZ
        || point.z > blockBox.maxZ) {
      return null;
    }

    double penX = Math.min(point.x - blockBox.minX, blockBox.maxX - point.x);
    double penY = Math.min(point.y - blockBox.minY, blockBox.maxY - point.y);
    double penZ = Math.min(point.z - blockBox.minZ, blockBox.maxZ - point.z);

    if (penX <= penY && penX <= penZ) {
      double sign = point.x - (blockBox.minX + blockBox.maxX) * 0.5D < 0.0D ? -1.0D : 1.0D;
      return new Contact(new Vec3(sign, 0.0D, 0.0D), penX);
    }
    if (penZ <= penY) {
      double sign = point.z - (blockBox.minZ + blockBox.maxZ) * 0.5D < 0.0D ? -1.0D : 1.0D;
      return new Contact(new Vec3(0.0D, 0.0D, sign), penZ);
    }
    double sign = point.y - (blockBox.minY + blockBox.maxY) * 0.5D < 0.0D ? -1.0D : 1.0D;
    return new Contact(new Vec3(0.0D, sign, 0.0D), penY);
  }

  private static void applyContactImpulse(RigidBodyState body, Contact contact, Vector3f point) {
    Vec3 normal = contact.normal;
    double rx = point.x - body.px;
    double ry = point.y - body.py;
    double rz = point.z - body.pz;
    double velocityAtPointX = body.vx + (body.wy * rz - body.wz * ry);
    double velocityAtPointY = body.vy + (body.wz * rx - body.wx * rz);
    double velocityAtPointZ = body.vz + (body.wx * ry - body.wy * rx);
    double relNormalVel =
        velocityAtPointX * normal.x + velocityAtPointY * normal.y + velocityAtPointZ * normal.z;

    if (relNormalVel >= 0.0D) {
      return;
    }

    double impulse = -(1.0D + RESTITUTION) * relNormalVel * body.mass;
    body.vx += impulse * normal.x * body.invMass;
    body.vy += impulse * normal.y * body.invMass;
    body.vz += impulse * normal.z * body.invMass;

    double torqueX = ry * normal.z - rz * normal.y;
    double torqueY = rz * normal.x - rx * normal.z;
    double torqueZ = rx * normal.y - ry * normal.x;
    body.wx += torqueX * impulse * body.invIx;
    body.wy += torqueY * impulse * body.invIy;
    body.wz += torqueZ * impulse * body.invIz;
  }

  private static void applyContactFriction(RigidBodyState body, Contact contact, Vector3f point) {
    Vec3 normal = contact.normal;
    double rx = point.x - body.px;
    double ry = point.y - body.py;
    double rz = point.z - body.pz;
    double vpx = body.vx + (body.wy * rz - body.wz * ry);
    double vpy = body.vy + (body.wz * rx - body.wx * rz);
    double vpz = body.vz + (body.wx * ry - body.wy * rx);

    double vn = vpx * normal.x + vpy * normal.y + vpz * normal.z;
    double tvx = vpx - normal.x * vn;
    double tvy = vpy - normal.y * vn;
    double tvz = vpz - normal.z * vn;
    double tSpeed = Math.sqrt(tvx * tvx + tvy * tvy + tvz * tvz);
    if (tSpeed < 1.0E-6D) {
      return;
    }

    double invTSpeed = 1.0D / tSpeed;
    double tx = tvx * invTSpeed;
    double ty = tvy * invTSpeed;
    double tz = tvz * invTSpeed;

    double normalLoad = body.mass * GROUND_WEIGHT;
    if (normal.y > 0.5D) {
      normalLoad += body.mass * contact.penetration * 12.0D;
    }
    double maxFrictionImpulse = CONTACT_FRICTION * normalLoad;
    double frictionImpulse = Math.min(tSpeed * body.mass, maxFrictionImpulse);

    body.vx -= tx * frictionImpulse * body.invMass;
    body.vy -= ty * frictionImpulse * body.invMass;
    body.vz -= tz * frictionImpulse * body.invMass;

    double torqueX = ry * tz - rz * ty;
    double torqueY = rz * tx - rx * tz;
    double torqueZ = rx * ty - ry * tx;
    body.wx -= torqueX * frictionImpulse * body.invIx;
    body.wy -= torqueY * frictionImpulse * body.invIy;
    body.wz -= torqueZ * frictionImpulse * body.invIz;
  }

  private static void pushOutOfBlock(RigidBodyState body, Contact contact) {
    body.px += contact.normal.x * contact.penetration * POSITION_SLOP;
    body.py += contact.normal.y * contact.penetration * POSITION_SLOP;
    body.pz += contact.normal.z * contact.penetration * POSITION_SLOP;
  }

  private static Contact findGroundContact(Level level, Vector3f point) {
    BlockPos pos = BlockPos.containing(point.x, Mth.floor(point.y - 0.02D), point.z);
    BlockState state = level.getBlockState(pos);
    VoxelShape shape = state.getCollisionShape(level, pos);
    if (shape.isEmpty()) {
      return null;
    }

    double top = pos.getY() + shape.max(Direction.Axis.Y);
    double gap = top - point.y;
    if (gap < -0.05D || gap > 0.12D) {
      return null;
    }

    double penetration = Math.max(0.0D, top - point.y);
    return new Contact(new Vec3(0.0D, 1.0D, 0.0D), Math.max(penetration, 0.001D));
  }

  private record Contact(Vec3 normal, double penetration) {}
}
