package com.binhjcao.physicstorches.physics;

import net.minecraft.world.phys.Vec3;

public record NarrowPhaseContact(
  RigidBody a,
  RigidBody b,
  Vec3 point,
  Vec3 normal, // Points from body A to body B.
  double penetration) {}
