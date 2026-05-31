package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityTorch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TorchParticles {
  private static final int SPAWN_CHANCE = 40;
  private static final int SMOKE_PER_SPAWN = 3;
  private static final int FLAME_PER_SPAWN = 2;
  private static final double SPAWN_SPREAD = 0.1D;
  private static ClientLevel lastLevel;
  private static long lastParticleGameTime = -1L;

  private TorchParticles() {}

  public static void tick(ClientLevel level, Minecraft client) {
    if (client.isPaused() || client.player == null) {
      return;
    }

    if (level != lastLevel) {
      lastLevel = level;
      lastParticleGameTime = -1L;
    }

    long gameTime = level.getGameTime();
    if (gameTime == lastParticleGameTime) {
      return;
    }
    lastParticleGameTime = gameTime;

    AABB search = client.player.getBoundingBox().inflate(48.0D);
    for (EntityTorch torch : level.getEntitiesOfClass(EntityTorch.class, search)) {
      BlockState blockState = torch.getBlockState();
      if (!shouldSpawn(blockState)) {
        continue;
      }

      if (torch.getRandom().nextInt(SPAWN_CHANCE) != 0) {
        continue;
      }

      Vec3 flame = torch.flamePosition(1.0F);
      RandomSource random = torch.getRandom();
      ParticleOptions flameType = flameParticle(blockState);

      for (int i = 0; i < SMOKE_PER_SPAWN; i++) {
        spawnAt(level, ParticleTypes.SMOKE, flame.x, flame.y, flame.z, random);
      }
      if (flameType != null) {
        for (int i = 0; i < FLAME_PER_SPAWN; i++) {
          spawnAt(level, flameType, flame.x, flame.y, flame.z, random);
        }
      }
    }
  }

  private static void spawnAt(
      ClientLevel level,
      ParticleOptions particle,
      double x,
      double y,
      double z,
      RandomSource random) {
    double spread = SPAWN_SPREAD;
    level.addParticle(
        particle,
        x + (random.nextDouble() - 0.5D) * spread,
        y + (random.nextDouble() - 0.5D) * spread * 0.5D,
        z + (random.nextDouble() - 0.5D) * spread,
        0.0D,
        0.0D,
        0.0D);
  }

  private static boolean shouldSpawn(BlockState state) {
    if (state.hasProperty(RedstoneTorchBlock.LIT) && !state.getValue(RedstoneTorchBlock.LIT)) {
      return false;
    }

    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("torch");
  }

  private static ParticleOptions flameParticle(BlockState state) {
    if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
      return ParticleTypes.SOUL_FIRE_FLAME;
    }

    return ParticleTypes.FLAME;
  }
}
