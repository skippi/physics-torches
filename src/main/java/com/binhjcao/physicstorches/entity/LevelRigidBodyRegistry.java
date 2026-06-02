package com.binhjcao.physicstorches.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LevelRigidBodyRegistry {
  private static final AABB LEVEL_ENTITY_BOUNDS =
      new AABB(-3.0E7D, -64.0D, -3.0E7D, 3.0E7D, 320.0D, 3.0E7D);

  private static final Map<ServerLevel, Set<EntityRigidBody>> BY_LEVEL = new HashMap<>();

  public static List<EntityRigidBody> all(Level level) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return List.of();
    }

    Set<EntityRigidBody> bodies = BY_LEVEL.get(serverLevel);
    if (bodies == null || bodies.isEmpty()) {
      return List.of();
    }

    return List.copyOf(bodies);
  }

  public static void add(EntityRigidBody body) {
    if (!(body.level() instanceof ServerLevel serverLevel)) {
      return;
    }

    BY_LEVEL.computeIfAbsent(serverLevel, ignored -> new HashSet<>()).add(body);
  }

  public static void remove(EntityRigidBody body) {
    if (!(body.level() instanceof ServerLevel serverLevel)) {
      return;
    }

    Set<EntityRigidBody> bodies = BY_LEVEL.get(serverLevel);
    if (bodies == null) {
      return;
    }

    bodies.remove(body);
    if (bodies.isEmpty()) {
      BY_LEVEL.remove(serverLevel);
    }
  }

  public static void reconcile(ServerLevel level) {
    Set<EntityRigidBody> bodies = new HashSet<>();
    for (EntityRigidBody body : level.getEntitiesOfClass(EntityRigidBody.class, LEVEL_ENTITY_BOUNDS)) {
      bodies.add(body);
    }

    if (bodies.isEmpty()) {
      BY_LEVEL.remove(level);
    } else {
      BY_LEVEL.put(level, bodies);
    }
  }

  public static void clearLevel(ServerLevel level) {
    BY_LEVEL.remove(level);
  }

  private LevelRigidBodyRegistry() {}
}
