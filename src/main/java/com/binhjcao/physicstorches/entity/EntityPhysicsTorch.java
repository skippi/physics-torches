package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntityPhysicsTorch extends Entity {
  public static final double HALF_WIDTH = 0.125D;
  public static final double HEIGHT = 0.625D;
  private static final double GRAVITY = 0.04D;
  private static final double AIR_DRAG = 0.99D;
  private static final double GROUND_DRAG = 0.7D;

  private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
      SynchedEntityData.defineId(EntityPhysicsTorch.class, EntityDataSerializers.BLOCK_STATE);

  public EntityPhysicsTorch(EntityType<? extends EntityPhysicsTorch> type, Level level) {
    super(type, level);
    refreshHitbox();
  }

  public EntityPhysicsTorch(Level level, BlockState blockState, Vec3 position, Vec3 velocity) {
    this(PhysicsTorchesEntities.PHYSICS_TORCH, level);
    setBlockState(blockState);
    setPos(position.x, position.y, position.z);
    setDeltaMovement(velocity);
    refreshHitbox();
  }

  public static boolean throwFromPlayer(ServerPlayer player, ItemStack stack) {
    BlockState blockState = blockStateForTorch(stack);
    Vec3 look = player.getLookAngle();
    Vec3 spawn = player.getEyePosition().add(look.scale(0.2D));
    Vec3 position = new Vec3(spawn.x, spawn.y - HEIGHT * 0.5D, spawn.z);
    Vec3 velocity = look.scale(0.55D).add(0.0D, 0.1D, 0.0D);

    EntityPhysicsTorch torch = new EntityPhysicsTorch(player.level(), blockState, position, velocity);
    player.level().addFreshEntity(torch);
    stack.shrink(1);
    return true;
  }

  public static BlockState blockStateForTorch(ItemStack stack) {
    if (stack.getItem() instanceof BlockItem blockItem) {
      return blockItem.getBlock().defaultBlockState();
    }

    return Blocks.TORCH.defaultBlockState();
  }

  public BlockState getBlockState() {
    return entityData.get(DATA_BLOCK_STATE);
  }

  public void setBlockState(BlockState blockState) {
    entityData.set(DATA_BLOCK_STATE, blockState);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_BLOCK_STATE, Blocks.TORCH.defaultBlockState());
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    input.read("BlockState", BlockState.CODEC).ifPresent(this::setBlockState);
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.store("BlockState", BlockState.CODEC, getBlockState());
  }

  @Override
  public void tick() {
    super.tick();

    if (isInWater()) {
      setDeltaMovement(getDeltaMovement().scale(0.8D));
    }

    Vec3 motion = getDeltaMovement();
    motion = motion.add(0.0D, -GRAVITY, 0.0D);

    if (onGround()) {
      motion = new Vec3(motion.x * GROUND_DRAG, motion.y * -0.5D, motion.z * GROUND_DRAG);
    } else {
      motion = motion.scale(AIR_DRAG);
    }

    setDeltaMovement(motion);
    move(MoverType.SELF, motion);

    if (horizontalCollision) {
      setDeltaMovement(getDeltaMovement().multiply(-0.2D, 1.0D, -0.2D));
    }

    refreshHitbox();
  }

  @Override
  public void setPos(double x, double y, double z) {
    super.setPos(x, y, z);
    refreshHitbox();
  }

  private void refreshHitbox() {
    double x = getX();
    double y = getY() - HEIGHT * 0.5D;
    double z = getZ();
    setBoundingBox(
        new AABB(
            x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH));
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }
}
