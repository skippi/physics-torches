package com.binhjcao.physicstorches;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import com.binhjcao.physicstorches.Collider.SurfaceHit;

import java.util.Optional;

public final class BlockCollider extends Collider {
  private final AABB bounds;

  private BlockCollider(AABB bounds) {
    this.bounds = bounds;
  }

  public static BlockCollider from(AABB block) {
    return new BlockCollider(block);
  }

  public AABB bounds() {
    return bounds;
  }

  public Vec3 center() {
    return new Vec3(
        (bounds.minX + bounds.maxX) * 0.5D,
        (bounds.minY + bounds.maxY) * 0.5D,
        (bounds.minZ + bounds.maxZ) * 0.5D);
  }

  @Override
  public Vec3 halfExtents() {
    return new Vec3(
        (bounds.maxX - bounds.minX) * 0.5D,
        (bounds.maxY - bounds.minY) * 0.5D,
        (bounds.maxZ - bounds.minZ) * 0.5D);
  }

  @Override
  public Vec3[] worldCorners(Vec3 center, Quaternionf orientation, double halfExtentInflate) {
    AABB aabb = halfExtentInflate > 0.0D ? bounds.inflate(halfExtentInflate) : bounds;
    return new Vec3[] {
      new Vec3(aabb.minX, aabb.minY, aabb.minZ),
      new Vec3(aabb.maxX, aabb.minY, aabb.minZ),
      new Vec3(aabb.minX, aabb.minY, aabb.maxZ),
      new Vec3(aabb.maxX, aabb.minY, aabb.maxZ),
      new Vec3(aabb.minX, aabb.maxY, aabb.minZ),
      new Vec3(aabb.maxX, aabb.maxY, aabb.minZ),
      new Vec3(aabb.minX, aabb.maxY, aabb.maxZ),
      new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ)
    };
  }

  @Override
  public AABB orientedBounds(Vec3 center, Quaternionf orientation, double inflate) {
    return inflate > 0.0D ? bounds.inflate(inflate) : bounds;
  }

  @Override
  public Optional<SurfaceHit> raycast(
      Quaternionf orientation,
      Vec3 center,
      Vec3 rayOrigin,
      Vec3 rayDirection,
      double maxReach,
      double surfaceTolerance) {
    return super.raycast(
        new Quaternionf(), center(), rayOrigin, rayDirection, maxReach, surfaceTolerance);
  }
}
