package com.binhjcao.physicstorches.physics;

import java.util.ArrayList;
import java.util.List;

public class BroadPhaseSequential {
  public static ArrayList<BroadPhasePair> findActiveBodies(
    List<RigidBody> bodies) {
    var result = new ArrayList<BroadPhasePair>();
    for (int i = 0; i < bodies.size(); i++) {
      for (int j = i + 1; j < bodies.size(); j++) {
        var body1 = bodies.get(i);
        var body2 = bodies.get(j);
        if (body1.freeze() && body2.freeze()) {
          continue;
        }
        if (body1.isSleeping() && body2.isSleeping()) {
          continue;
        }
        if (body1.bounds().inflate(Physics.MANIFOLD_TOLERANCE).intersects(body2.bounds())) {
          result.add(BroadPhasePair.of(body1, body2));
        }
      }
    }
    return result;
  }

  private BroadPhaseSequential() {}
}
