package com.binhjcao.physicstorches;

import java.util.ArrayList;

import net.minecraft.world.phys.Vec3;

public record ContactManifold(
  RigidBody a,
  RigidBody b,
  Vec3 offset,
  Vec3 normal, // Points from body A to body B.
  double penetration,
  ArrayList<Vec3> contactsOnA,
  ArrayList<Vec3> contactsOnB
) {
  public static int MAX_CONTACTS = 8;
}
