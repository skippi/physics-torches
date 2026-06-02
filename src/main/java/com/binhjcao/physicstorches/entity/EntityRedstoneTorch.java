package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class EntityRedstoneTorch extends EntityTorch {
  private static final BlockState BLOCK_STATE = Blocks.REDSTONE_TORCH.defaultBlockState();
  private static final ItemStack PICKUP = new ItemStack(Items.REDSTONE_TORCH);

  public EntityRedstoneTorch(EntityType<? extends EntityRedstoneTorch> type, Level level) {
    super(type, level);
    setBlockState(BLOCK_STATE);
  }

  public EntityRedstoneTorch(Level level, Vec3 position) {
    this(ModEntityTypes.REDSTONE_TORCH, level);
    setCollisionCenter(position);
  }

  @Override
  protected ItemStack pickupItemStack() {
    return PICKUP.copy();
  }
}
