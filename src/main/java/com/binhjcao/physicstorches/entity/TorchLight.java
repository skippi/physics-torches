package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class TorchLight {
  record Cell(BlockState original, int refs, int level) {
    static final Codec<Cell> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        BlockState.CODEC.fieldOf("original").forGetter(Cell::original),
                        Codec.INT.fieldOf("refs").forGetter(Cell::refs),
                        Codec.INT.fieldOf("level").forGetter(Cell::level))
                    .apply(instance, Cell::new));
  }

  private static final AABB LEVEL_ENTITY_BOUNDS =
      new AABB(-3.0E7D, -64.0D, -3.0E7D, 3.0E7D, 320.0D, 3.0E7D);
  private static final Set<ResourceKey<Level>> PENDING_RECONCILE = new HashSet<>();

  private TorchLight() {}

  public static void onLevelLoad(ServerLevel level) {
    PENDING_RECONCILE.add(level.dimension());
  }

  static int luminance(EntityTorch torch) {
    if (torch.isInWater()) {
      return 0;
    }

    BlockState state = torch.getBlockState();
    if (state.hasProperty(RedstoneTorchBlock.LIT) && !state.getValue(RedstoneTorchBlock.LIT)) {
      return 0;
    }

    return state.getLightEmission();
  }

  static void update(EntityTorch torch) {
    if (torch.level().isClientSide()) {
      return;
    }

    ServerLevel level = (ServerLevel) torch.level();
    if (PENDING_RECONCILE.remove(level.dimension())) {
      reconcileLevel(level);
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
      raiseLevel(level, target, luminance);
      return;
    }

    clear(torch);
    attach(torch, target, luminance);
  }

  static void clear(EntityTorch torch) {
    BlockPos pos = torch.lightBlockPos();
    if (pos == null || torch.level().isClientSide()) {
      torch.setLightBlockPos(null);
      return;
    }

    ServerLevel level = (ServerLevel) torch.level();
    LevelCells data = LevelCells.get(level);
    Cell cell = data.cells.get(pos);
    if (cell == null) {
      torch.setLightBlockPos(null);
      return;
    }

    if (cell.refs <= 1) {
      data.cells.remove(pos);
      BlockState restore = cell.original;
      if (restore.isAir()) {
        level.removeBlock(pos, false);
      } else {
        level.setBlockAndUpdate(pos, restore);
      }
    } else {
      data.cells.put(pos, new Cell(cell.original, cell.refs - 1, cell.level));
    }

    data.setDirty();
    torch.setLightBlockPos(null);
  }

  private static void attach(EntityTorch torch, BlockPos target, int luminance) {
    ServerLevel level = (ServerLevel) torch.level();
    BlockState current = level.getBlockState(target);
    if (!canReplace(current)) {
      return;
    }

    LevelCells data = LevelCells.get(level);
    Cell cell = data.cells.get(target);
    if (cell == null) {
      BlockState light = lightState(level, target, luminance);
      if (!level.setBlockAndUpdate(target, light)) {
        return;
      }

      data.cells.put(target, new Cell(current, 1, luminance));
      data.setDirty();
      torch.setLightBlockPos(target);
      return;
    }

    int levelValue = Math.max(cell.level, luminance);
    if (levelValue != cell.level) {
      level.setBlockAndUpdate(target, lightState(level, target, levelValue));
    }

    data.cells.put(target, new Cell(cell.original, cell.refs + 1, levelValue));
    data.setDirty();
    torch.setLightBlockPos(target);
  }

  private static void raiseLevel(ServerLevel level, BlockPos target, int luminance) {
    LevelCells data = LevelCells.get(level);
    Cell cell = data.cells.get(target);
    if (cell == null || luminance <= cell.level) {
      return;
    }

    level.setBlockAndUpdate(target, lightState(level, target, luminance));
    data.cells.put(target, new Cell(cell.original, cell.refs, luminance));
    data.setDirty();
  }

  private static void reconcileLevel(ServerLevel level) {
    LevelCells data = LevelCells.get(level);
    Map<BlockPos, Cell> rebuilt = new HashMap<>();

    for (EntityTorch torch : level.getEntitiesOfClass(EntityTorch.class, LEVEL_ENTITY_BOUNDS)) {
      int luminance = luminance(torch);
      if (luminance <= 0) {
        torch.setLightBlockPos(null);
        continue;
      }

      BlockPos target = lightPos(torch);
      BlockPos attached = torch.lightBlockPos();
      if (attached == null || !attached.equals(target)) {
        torch.setLightBlockPos(target);
        attached = target;
      }

      Cell existing = rebuilt.get(attached);
      if (existing == null) {
        Cell saved = data.cells.get(attached);
        BlockState original =
            saved != null ? saved.original() : originalBeforeLight(level, attached);
        rebuilt.put(attached, new Cell(original, 1, luminance));
      } else {
        rebuilt.put(
            attached,
            new Cell(
                existing.original(),
                existing.refs() + 1,
                Math.max(existing.level(), luminance)));
      }
    }

    for (Map.Entry<BlockPos, Cell> entry : rebuilt.entrySet()) {
      BlockPos pos = entry.getKey();
      Cell cell = entry.getValue();
      BlockState at = level.getBlockState(pos);
      if (!at.is(Blocks.LIGHT)) {
        if (!canReplace(at)) {
          continue;
        }

        level.setBlockAndUpdate(pos, lightState(level, pos, cell.level()));
      } else if (cell.level() != at.getValue(LightBlock.LEVEL)) {
        level.setBlockAndUpdate(pos, lightState(level, pos, cell.level()));
      }
    }

    for (BlockPos pos : new HashSet<>(data.cells.keySet())) {
      if (!rebuilt.containsKey(pos)) {
        Cell orphan = data.cells.get(pos);
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
          if (orphan.original().isAir()) {
            level.removeBlock(pos, false);
          } else {
            level.setBlockAndUpdate(pos, orphan.original());
          }
        }
      }
    }

    data.cells.clear();
    data.cells.putAll(rebuilt);
    data.setDirty();
  }

  private static BlockState originalBeforeLight(ServerLevel level, BlockPos pos) {
    BlockState at = level.getBlockState(pos);
    if (at.is(Blocks.LIGHT)) {
      return Blocks.AIR.defaultBlockState();
    }

    return at;
  }

  private static BlockPos lightPos(EntityTorch torch) {
    return BlockPos.containing(torch.getX(), torch.getY(), torch.getZ());
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

  static final class LevelCells extends SavedData {
    private static final Codec<BlockPos> BLOCK_POS_KEY_CODEC =
        Codec.STRING.xmap(
            str -> BlockPos.of(Long.parseLong(str)), pos -> Long.toString(pos.asLong()));

    private static final Codec<Map<BlockPos, Cell>> CELLS_CODEC =
        Codec.unboundedMap(BLOCK_POS_KEY_CODEC, Cell.CODEC);

    private static final SavedDataType<LevelCells> TYPE =
        new SavedDataType<>(
            Identifier.fromNamespaceAndPath(PhysicsTorchesMod.MOD_ID, "torch_light"),
            LevelCells::new,
            CELLS_CODEC.xmap(LevelCells::new, LevelCells::cellsForSave),
            null);

    final Map<BlockPos, Cell> cells = new HashMap<>();

    LevelCells() {}

    LevelCells(Map<BlockPos, Cell> cells) {
      this.cells.putAll(cells);
    }

    static LevelCells get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<BlockPos, Cell> cellsForSave() {
      return Map.copyOf(cells);
    }
  }
}
