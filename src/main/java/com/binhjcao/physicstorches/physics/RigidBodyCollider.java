package com.binhjcao.physicstorches.physics;

import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RigidBodyCollider {
  private final float[] localCorners;
  private final float centerY;

  public RigidBodyCollider(double halfX, double halfY, double halfZ, double centerYOffset) {
    centerY = (float) centerYOffset;
    localCorners =
        new float[] {
          (float) -halfX, (float) -halfY, (float) -halfZ,
          (float) halfX, (float) -halfY, (float) -halfZ,
          (float) -halfX, (float) halfY, (float) -halfZ,
          (float) halfX, (float) halfY, (float) -halfZ,
          (float) -halfX, (float) -halfY, (float) halfZ,
          (float) halfX, (float) -halfY, (float) halfZ,
          (float) -halfX, (float) halfY, (float) halfZ,
          (float) halfX, (float) halfY, (float) halfZ
        };
  }

  public float centerYOffset() {
    return centerY;
  }

  public void worldCorners(RigidBodyState body, Vector3f[] out) {
    Quaternionf rotation = body.orientation;
    Vector3f point = new Vector3f();
    for (int i = 0; i < 8; i++) {
      int corner = i * 3;
      rotation.transform(
          localCorners[corner],
          localCorners[corner + 1] + centerY,
          localCorners[corner + 2],
          point);
      out[i].set((float) body.px + point.x, (float) body.py + point.y, (float) body.pz + point.z);
    }
  }

  public AABB worldBounds(RigidBodyState body) {
    Vector3f[] corners = new Vector3f[8];
    for (int i = 0; i < 8; i++) {
      corners[i] = new Vector3f();
    }
    worldCorners(body, corners);
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    for (Vector3f corner : corners) {
      minX = Math.min(minX, corner.x);
      minY = Math.min(minY, corner.y);
      minZ = Math.min(minZ, corner.z);
      maxX = Math.max(maxX, corner.x);
      maxY = Math.max(maxY, corner.y);
      maxZ = Math.max(maxZ, corner.z);
    }
    return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
  }

  public static RigidBodyCollider box(double halfX, double halfY, double halfZ, double centerYOffset) {
    return new RigidBodyCollider(halfX, halfY, halfZ, centerYOffset);
  }
}
