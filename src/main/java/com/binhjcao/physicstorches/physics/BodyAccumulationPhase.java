package com.binhjcao.physicstorches.physics;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.LevelRigidBodyRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BodyAccumulationPhase {
  private static final AABB LEVEL_ENTITY_BOUNDS =
      new AABB(-3.0E7D, -64.0D, -3.0E7D, 3.0E7D, 320.0D, 3.0E7D);

  private record BlockBoundsKey(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static BlockBoundsKey from(AABB block) {
      return new BlockBoundsKey(
          block.minX, block.minY, block.minZ, block.maxX, block.maxY, block.maxZ);
    }
  }

  public static List<EntityRigidBody> findEntities(Level level) {
    if (level instanceof ServerLevel serverLevel) {
      return LevelRigidBodyRegistry.all(serverLevel);
    }
    return level.getEntitiesOfClass(EntityRigidBody.class, LEVEL_ENTITY_BOUNDS);
  }

  public static ArrayList<RigidBody> findBodies(Level level, double dt) {
    var bodies = new ArrayList<RigidBody>();
    Set<BlockBoundsKey> seenBlocks = new HashSet<>();

    for (EntityRigidBody entity : findEntities(level)) {
      bodies.add(entity.rigidBody());
      AABB searchBounds = sweptBlockSearchBounds(entity, dt);
      for (VoxelShape shape : level.getBlockCollisions(entity, searchBounds)) {
        for (AABB block : shape.toAabbs()) {
          if (!seenBlocks.add(BlockBoundsKey.from(block))) {
            continue;
          }
          bodies.add(RigidBody.frozenFromBlockAabb(block));
        }
      }
    }

    return bodies;
  }

  private static AABB sweptBlockSearchBounds(EntityRigidBody entity, double dt) {
    RigidBody body = entity.rigidBody();
    AABB bounds = entity.getBoundingBox();
    if (body.freeze() || body.isSleeping() || dt <= 1.0E-8D) {
      return bounds;
    }

    double expand = body.maxPointVelocity() * dt + Physics.SURFACE_TOLERANCE;
    return bounds.inflate(expand);
  }

  private BodyAccumulationPhase() {}
}
