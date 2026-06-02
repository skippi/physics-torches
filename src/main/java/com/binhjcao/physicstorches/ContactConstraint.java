package com.binhjcao.physicstorches;

import java.util.ArrayList;

import net.minecraft.world.phys.Vec3;

public record ContactConstraint(
    RigidBody a,
    RigidBody b,
    Vec3 normal, // Points from body A to body B.
    Vec3 tangent1, // cached for performance
    Vec3 tangent2, // cached for performance
    ArrayList<ContactConstraintPoint> points) {}
