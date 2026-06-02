package com.binhjcao.physicstorches;

public record BroadPhasePair(
  RigidBody a,
  RigidBody b
) {
  public static BroadPhasePair of(RigidBody a, RigidBody b) {
    return new BroadPhasePair(a, b);
  }
}
