package com.binhjcao.physicstorches.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

final class PhysicsTorchLight {
  private record Cell(BlockState original, int refs, int level) {}

  private static final Map<ServerLevel, Map<BlockPos, Cell>> CELLS = new WeakHashMap<>();

  private PhysicsTorchLight() {}

  static int luminance(EntityPhysicsTorch torch) {
    if (torch.isInWater()) {
      return 0;
    }

    BlockState state = torch.getBlockState();
    if (state.hasProperty(RedstoneTorchBlock.LIT) && !state.getValue(RedstoneTorchBlock.LIT)) {
      return 0;
    }

    return state.getLightEmission();
  }

  static void update(EntityPhysicsTorch torch) {
    if (torch.level().isClientSide()) {
      return;
    }

    BlockPos target = lightPos(torch);
    int luminance = luminance(torch);
    BlockPos attached = torch.lightBlockPos();

    if (luminance <= 0) {
      if (attached != null) {
        clear(torch);
      }
      return;
    }

    if (target.equals(attached)) {
      raiseLevel((ServerLevel) torch.level(), target, luminance);
      return;
    }

    clear(torch);
    attach(torch, target, luminance);
  }

  static void clear(EntityPhysicsTorch torch) {
    BlockPos pos = torch.lightBlockPos();
    if (pos == null || torch.level().isClientSide()) {
      torch.setLightBlockPos(null);
      return;
    }

    ServerLevel level = (ServerLevel) torch.level();
    Map<BlockPos, Cell> cells = cells(level);
    Cell cell = cells.get(pos);
    if (cell == null) {
      torch.setLightBlockPos(null);
      return;
    }

    if (cell.refs <= 1) {
      cells.remove(pos);
      BlockState restore = cell.original;
      if (restore.isAir()) {
        level.removeBlock(pos, false);
      } else {
        level.setBlockAndUpdate(pos, restore);
      }
    } else {
      cells.put(pos, new Cell(cell.original, cell.refs - 1, cell.level));
    }

    torch.setLightBlockPos(null);
  }

  private static void attach(EntityPhysicsTorch torch, BlockPos target, int luminance) {
    ServerLevel level = (ServerLevel) torch.level();
    BlockState current = level.getBlockState(target);
    if (!canReplace(current)) {
      return;
    }

    Map<BlockPos, Cell> cells = cells(level);
    Cell cell = cells.get(target);
    if (cell == null) {
      BlockState light = lightState(level, target, luminance);
      if (!level.setBlockAndUpdate(target, light)) {
        return;
      }

      cells.put(target, new Cell(current, 1, luminance));
      torch.setLightBlockPos(target);
      return;
    }

    int levelValue = Math.max(cell.level, luminance);
    if (levelValue != cell.level) {
      level.setBlockAndUpdate(target, lightState(level, target, levelValue));
    }

    cells.put(target, new Cell(cell.original, cell.refs + 1, levelValue));
    torch.setLightBlockPos(target);
  }

  private static void raiseLevel(ServerLevel level, BlockPos target, int luminance) {
    Cell cell = cells(level).get(target);
    if (cell == null || luminance <= cell.level) {
      return;
    }

    level.setBlockAndUpdate(target, lightState(level, target, luminance));
    cells(level).put(target, new Cell(cell.original, cell.refs, luminance));
  }

  private static BlockPos lightPos(EntityPhysicsTorch torch) {
    return BlockPos.containing(torch.getX(), torch.getY() + EntityPhysicsTorch.HEIGHT * 0.5D, torch.getZ());
  }

  private static boolean canReplace(BlockState state) {
    return state.isAir() || state.is(Blocks.LIGHT) || state.canBeReplaced();
  }

  private static BlockState lightState(ServerLevel level, BlockPos pos, int luminance) {
    BlockState state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, luminance);
    if (level.getFluidState(pos).is(Fluids.WATER)) {
      return state.setValue(BlockStateProperties.WATERLOGGED, true);
    }

    return state;
  }

  private static Map<BlockPos, Cell> cells(ServerLevel level) {
    return CELLS.computeIfAbsent(level, ignored -> new HashMap<>());
  }
}
