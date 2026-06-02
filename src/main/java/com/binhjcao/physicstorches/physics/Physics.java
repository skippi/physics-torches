package com.binhjcao.physicstorches.physics;

import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.RaycastHit;
import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.binhjcao.physicstorches.entity.LevelRigidBodyRegistry;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class Physics {
  public static final double GRAVITY = 9.81D;
  public static final double SURFACE_TOLERANCE = 0.05D;
  public static final double CONTACT_MAX_ALLOWED_PENETRATION = 0.01D;
  public static final double PENETRATION_SLOP = 0.02D;
  public static final double SLEEP_THRESHOLD = 0.03D;
  public static final double TIME_BEFORE_SLEEP = 0.5D;
  public static final double BAUMGARTE_STABILIZATION_FACTOR = 0.2D;
  public static final int VELOCITY_SOLVER_ITERATIONS = 16;
  public static final int POSITION_SOLVER_ITERATIONS = 4;

  public static ArrayList<ContactManifold> findContactManifoldsForEntity(
      EntityRigidBody entity, Level level) {
    var manifolds = new ArrayList<ContactManifold>();
    RigidBody body = entity.rigidBody();
    if (body.isSleeping()) {
      return manifolds;
    }

    var seenBlocks = new java.util.HashSet<BlockBoundsKey>();
    AABB searchBounds = entity.getBoundingBox().inflate(SURFACE_TOLERANCE);
    for (var shape : level.getBlockCollisions(entity, searchBounds)) {
      for (AABB block : shape.toAabbs()) {
        if (!seenBlocks.add(BlockBoundsKey.from(block))) {
          continue;
        }
        RigidBody blockBody = RigidBody.frozenFromBlockAabb(block);
        if (!body.bounds().intersects(blockBody.bounds())) {
          continue;
        }
        NarrowPhase.findContactCandidate(body, blockBody)
            .map(ContactManifoldPhase::generateContactManifold)
            .ifPresent(manifolds::add);
      }
    }
    return manifolds;
  }

  private record BlockBoundsKey(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static BlockBoundsKey from(AABB block) {
      return new BlockBoundsKey(
          block.minX, block.minY, block.minZ, block.maxX, block.maxY, block.maxZ);
    }
  }

  public static ArrayList<ContactManifold> findContactManifolds(List<RigidBody> bodies) {
    var pairs = BroadPhaseSequential.findActiveBodies(bodies);
    var manifolds = new ArrayList<ContactManifold>();
    for (var pair : pairs) {
      var candidate = NarrowPhase.findContactCandidate(pair.a(), pair.b());
      if (candidate.isEmpty()) {
        continue;
      }
      manifolds.add(ContactManifoldPhase.generateContactManifold(candidate.get()));
    }
    return manifolds;
  }

  public static void step(Level level) {
    if (level.isClientSide()) {
      return;
    }
    for (EntityRigidBody entity : LevelRigidBodyRegistry.all(level)) {
      entity.rigidBody().snapshotPrevOrientation();
    }
    float dt = (float) (1.0D / (double) level.tickRateManager().tickrate());
    List<RigidBody> bodies = BodyAccumulationPhase.findBodies(level, dt);
    for (var body : bodies) {
      integrateGravity(body, dt);
    }
    var constraints = new ArrayList<ContactConstraint>();
    for (var manifold : findContactManifolds(bodies)) {
      constraints.add(ContactConstraintPhase.setupContactConstraint(manifold, dt));
    }
    VelocitySolverPhase.solve(constraints);
    for (var body : bodies) {
      if (!body.freeze() && !body.isSleeping()) {
        body.snapshotPrevPosition();
      }
    }
    for (var body : bodies) {
      BodyIntegrationPhase.integrateVelocity(body, dt);
    }
    ContinuousCollisionPhase.solve(bodies, constraints, dt);
    PositionSolverPhase.solve(constraints);
    for (var body : bodies) {
      BodyIntegrationPhase.integrateSleep(body, dt);
    }
    for (EntityRigidBody entity : LevelRigidBodyRegistry.all(level)) {
      entity.syncFromRigidBody();
    }
  }

  private static void integrateGravity(RigidBody body, double dt) {
    if (body.isSleeping() || body.freeze()) {
      return;
    }

    if (body.gravityScale() <= 1.0E-8D) {
      return;
    }

    Vec3 gravityAccel = new Vec3(0.0D, -GRAVITY * body.gravityScale(), 0.0D);
    body.applyImpulse(gravityAccel.scale(body.mass() * dt));
  }

  private Physics() {}

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance) {
    return raycast(level, searchBox, origin, direction, partialTick, maxDistance, body -> true);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      EntityRigidBody.PlayerLookRay ray,
      float partialTick,
      double maxDistance) {
    return raycast(level, searchBox, ray.origin(), ray.direction(), partialTick, maxDistance);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      EntityRigidBody.PlayerLookRay ray,
      float partialTick,
      double maxDistance,
      Predicate<EntityRigidBody> filter) {
    return raycast(
        level, searchBox, ray.origin(), ray.direction(), partialTick, maxDistance, filter);
  }

  public static Optional<RaycastHit> raycast(
      Level level,
      AABB searchBox,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance,
      Predicate<EntityRigidBody> filter) {
    RaycastHit closestHit = null;
    for (EntityRigidBody body : level.getEntitiesOfClass(EntityRigidBody.class, searchBox)) {
      if (!filter.test(body)) {
        continue;
      }

      Optional<RaycastHit> hit = raycastBody(body, origin, direction, partialTick, maxDistance);
      if (hit.isEmpty()) {
        continue;
      }

      if (closestHit == null
          || hit.get().point().distanceToSqr(origin) < closestHit.point().distanceToSqr(origin)) {
        closestHit = hit.get();
      }
    }

    return Optional.ofNullable(closestHit);
  }

  private record ColliderHit(Vec3 point, Vec3 normal) {}

  private static Optional<RaycastHit> raycastBody(
      EntityRigidBody body,
      Vec3 origin,
      Vec3 direction,
      float partialTick,
      double maxDistance) {
    Vec3 center = new Vec3(body.getX(), body.getY(), body.getZ());
    Quaternionf orientation = body.getOrientation(partialTick);
    Collider collider = body.rigidBody().collider();
    Optional<ColliderHit> hit =
        raycastCollider(collider, orientation, center, origin, direction, maxDistance);
    if (hit.isEmpty() && SURFACE_TOLERANCE > 0.0D) {
      hit =
          raycastCollider(
              collider,
              orientation,
              center,
              origin,
              direction,
              maxDistance,
              SURFACE_TOLERANCE);
    }
    return hit.map(result -> new RaycastHit(body, result.point(), result.normal()));
  }

  private static Optional<ColliderHit> raycastCollider(
      Collider collider,
      Quaternionf orientation,
      Vec3 center,
      Vec3 origin,
      Vec3 direction,
      double maxDistance) {
    return raycastCollider(
        collider, orientation, center, origin, direction, maxDistance, 0.0D);
  }

  private static Optional<ColliderHit> raycastCollider(
      Collider collider,
      Quaternionf orientation,
      Vec3 center,
      Vec3 origin,
      Vec3 direction,
      double maxDistance,
      double surfaceTolerance) {
    return collider
        .raycast(orientation, center, origin, direction, maxDistance, surfaceTolerance)
        .map(hit -> new ColliderHit(hit.worldPoint(), hit.worldNormal()));
  }
}
