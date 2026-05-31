package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import net.minecraft.world.phys.Vec3;

public record RaycastHit(EntityRigidBody body, Vec3 point, Vec3 normal) {}
