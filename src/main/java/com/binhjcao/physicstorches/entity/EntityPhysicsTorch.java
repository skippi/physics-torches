package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public class EntityPhysicsTorch extends Entity {
  public static final double HALF_WIDTH = 0.0625D;
  public static final double HEIGHT = 0.625D;
  private static final double GRAVITY = 0.08D;

  private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
      SynchedEntityData.defineId(EntityPhysicsTorch.class, EntityDataSerializers.BLOCK_STATE);

  private @Nullable BlockPos lightBlockPos;

  public EntityPhysicsTorch(EntityType<? extends EntityPhysicsTorch> type, Level level) {
    super(type, level);
    setNoGravity(true);
  }

  public EntityPhysicsTorch(Level level, BlockState blockState, Vec3 position, Vec3 velocity) {
    this(PhysicsTorchesEntities.PHYSICS_TORCH, level);
    setBlockState(blockState);
    setPos(position.x, position.y, position.z);
    setDeltaMovement(velocity);
  }

  public static boolean throwFromPlayer(ServerPlayer player, ItemStack stack) {
    BlockState blockState = blockStateForTorch(stack);
    Vec3 look = player.getLookAngle();
    Vec3 spawn = player.getEyePosition().add(look.scale(0.2D));
    Vec3 position = new Vec3(spawn.x, spawn.y - HEIGHT, spawn.z);
    Vec3 velocity = look.scale(0.72D).add(0.0D, 0.14D, 0.0D);

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

  public static ItemStack itemStackForBlockState(BlockState blockState) {
    var item = blockState.getBlock().asItem();
    if (item == Items.AIR) {
      return new ItemStack(Items.TORCH);
    }

    return new ItemStack(item);
  }

  public BlockState getBlockState() {
    return entityData.get(DATA_BLOCK_STATE);
  }

  public void setBlockState(BlockState blockState) {
    entityData.set(DATA_BLOCK_STATE, blockState);
  }

  public Quaternionf getOrientation(float partialTick) {
    return new Quaternionf();
  }

  @Nullable BlockPos lightBlockPos() {
    return lightBlockPos;
  }

  void setLightBlockPos(@Nullable BlockPos pos) {
    lightBlockPos = pos;
  }

  @Override
  public void tick() {
    super.tick();

    if (!level().isClientSide()) {
      Vec3 velocity = getDeltaMovement().subtract(0.0D, GRAVITY, 0.0D);
      setDeltaMovement(velocity);
      move(MoverType.SELF, velocity);

      if (onGround()) {
        setDeltaMovement(velocity.x * 0.55D, 0.0D, velocity.z * 0.55D);
      }

      PhysicsTorchLight.update(this);
    }
  }

  @Override
  public void remove(RemovalReason reason) {
    PhysicsTorchLight.clear(this);
    super.remove(reason);
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
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean skipAttackInteraction(Entity attacker) {
    if (!level().isClientSide() && attacker instanceof ServerPlayer player) {
      return tryPickUp(player);
    }

    return false;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    Entity attacker = source.getEntity();
    if (attacker instanceof ServerPlayer player && source.is(DamageTypes.PLAYER_ATTACK)) {
      return tryPickUp(player);
    }

    return false;
  }

  private boolean tryPickUp(ServerPlayer player) {
    if (!isAlive()) {
      return false;
    }

    ItemStack stack = itemStackForBlockState(getBlockState());
    if (!player.getInventory().add(stack)) {
      player.drop(stack, false);
    }

    discard();
    return true;
  }
}
