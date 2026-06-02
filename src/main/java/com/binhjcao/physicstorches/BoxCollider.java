package com.binhjcao.physicstorches;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class BoxCollider extends Collider {
  private static final int[][] CORNER_SIGNS = {
    {-1, -1, -1}, {1, -1, -1}, {-1, -1, 1}, {1, -1, 1},
    {-1, 1, -1}, {1, 1, -1}, {-1, 1, 1}, {1, 1, 1}
  };

  private final double halfX;
  private final double halfY;
  private final double halfZ;

  private BoxCollider(double halfX, double halfY, double halfZ) {
    this.halfX = halfX;
    this.halfY = halfY;
    this.halfZ = halfZ;
  }

  public static BoxCollider cube(double halfSize) {
    return box(halfSize, halfSize, halfSize);
  }

  public static BoxCollider box(double halfX, double halfY, double halfZ) {
    return new BoxCollider(halfX, halfY, halfZ);
  }

  @Override
  public Vec3 halfExtents() {
    return new Vec3(halfX, halfY, halfZ);
  }

  @Override
  public Vec3[] worldCorners(Vec3 center, Quaternionf orientation, double halfExtentInflate) {
    Vec3[] corners = new Vec3[CORNER_SIGNS.length];
    double hx = halfX + halfExtentInflate;
    double hy = halfY + halfExtentInflate;
    double hz = halfZ + halfExtentInflate;
    for (int i = 0; i < CORNER_SIGNS.length; i++) {
      int[] signs = CORNER_SIGNS[i];
      corners[i] =
          OrientedTransform.toWorldPoint(
              new Vec3(signs[0] * hx, signs[1] * hy, signs[2] * hz), center, orientation);
    }
    return corners;
  }

  @Override
  public AABB orientedBounds(Vec3 center, Quaternionf orientation, double inflate) {
    return boundsFromCorners(worldCorners(center, orientation), inflate);
  }
}
