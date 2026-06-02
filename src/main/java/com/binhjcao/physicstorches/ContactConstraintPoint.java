package com.binhjcao.physicstorches;

import net.minecraft.world.phys.Vec3;

public record ContactConstraintPoint(
    Vec3 leverArmA,
    Vec3 leverArmB,
    Vec3 localContactA,
    Vec3 localContactB,
    float normalMass, // cached for performance
    float tangentMass1, // cached for performance
    float tangentMass2, // cached for performance
    float velocityBias,
    float positionBias,
    float accumulatedNormalImpulse,
    float accumulatedTangentImpulseU,
    float accumulatedTangentImpulseV) {}
