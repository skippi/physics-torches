package com.binhjcao.physicstorches;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class OrientedTransform {
  private OrientedTransform() {}

  static Vec3 toLocalPoint(Vec3 worldPoint, Vec3 center, Quaternionf orientation) {
    Vector3f local =
        new Vector3f(
            (float) (worldPoint.x - center.x),
            (float) (worldPoint.y - center.y),
            (float) (worldPoint.z - center.z));
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  static Vec3 toLocalDirection(Vec3 worldDirection, Quaternionf orientation) {
    Vector3f local =
        new Vector3f(
            (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
    new Quaternionf(orientation).invert().transform(local);
    return new Vec3(local.x, local.y, local.z);
  }

  static Vec3 toWorldPoint(Vec3 localPoint, Vec3 center, Quaternionf orientation) {
    Vector3f world =
        new Vector3f((float) localPoint.x, (float) localPoint.y, (float) localPoint.z);
    orientation.transform(world);
    return new Vec3(world.x + center.x, world.y + center.y, world.z + center.z);
  }

  static Vec3 toWorldDirection(Vec3 localDirection, Quaternionf orientation) {
    Vector3f world =
        new Vector3f(
            (float) localDirection.x, (float) localDirection.y, (float) localDirection.z);
    orientation.transform(world);
    return new Vec3(world.x, world.y, world.z);
  }
}
