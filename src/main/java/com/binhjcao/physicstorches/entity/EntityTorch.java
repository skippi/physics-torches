package com.binhjcao.physicstorches.entity;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.binhjcao.physicstorches.BoxCollider;
import com.binhjcao.physicstorches.Collider;
import com.binhjcao.physicstorches.ModEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class EntityTorch extends EntityRigidBody {
  public static final double HALF_WIDTH = 0.0625D;
  public static final double HEIGHT = 0.625D;
  public static final double HALF_HEIGHT = HEIGHT * 0.5D;
  private static final double FLAME_HEIGHT = 0.7D;
  private static final double WALL_FLAME_OFFSET = 0.27D;
  private static final double THROW_SPEED = 25;
  private static final double THROW_LIFT = 1D;
  private static final double THROW_SPAWN_FORWARD = 0.25D;
  private static final double THROW_SPAWN_SIDE = 0.36D;
  private static final double THROW_SPIN_IMPULSE = 0.75D;

  private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
      SynchedEntityData.defineId(EntityTorch.class, EntityDataSerializers.BLOCK_STATE);

  private @Nullable BlockPos lightBlockPos;

  public EntityTorch(EntityType<? extends EntityTorch> type, Level level) {
    super(type, level);
    rigidBody().mass(0.5D);
    rigidBody().linearDamp(0.7D);
    rigidBody().angularDamp(0.7D);
    rigidBody().friction(0.7D);
    rigidBody().bounce(0.2D);
    inputRayPickable(true);
  }

  public EntityTorch(Level level, BlockState blockState, Vec3 position) {
    this(ModEntityTypes.TORCH, level);
    setBlockState(blockState);
    setCollisionCenter(position);
  }

  public static boolean throwFromPlayer(
      ServerPlayer player, ItemStack stack, InteractionHand hand, Vec3 playerDeltaMovement) {
    player.swing(hand, true);
    BlockState blockState = blockStateForTorch(stack);
    Vec3 look = player.getLookAngle();
    Vec3 side = throwSideOffset(player, hand);
    Vec3 spawn =
        player
            .getEyePosition()
            .add(look.scale(THROW_SPAWN_FORWARD))
            .add(side.scale(THROW_SPAWN_SIDE));
    Vec3 position = new Vec3(spawn.x, spawn.y - HALF_HEIGHT, spawn.z);

    EntityTorch torch = create(player.level(), blockState, position);
    player.level().addFreshEntity(torch);
    torch.fling(look);
    torch
        .rigidBody()
        .linearVelocity(
            torch
                .rigidBody()
                .linearVelocity()
                .add(
                    playerDeltaMovement.scale(player.level().tickRateManager().tickrate())));
    if (!player.isCreative()) {
      stack.shrink(1);
    }
    return true;
  }

  private static Vec3 throwSideOffset(ServerPlayer player, InteractionHand hand) {
    Vec3 look = player.getLookAngle();
    Vec3 up = player.getUpVector(1.0F);
    Vec3 right = look.cross(up).normalize();
    return hand == InteractionHand.MAIN_HAND ? right : right.scale(-1.0D);
  }

  public void fling(Vec3 direction) {
    Vec3 dir = direction.normalize();
    wake();
    rigidBody().linearVelocity(dir.scale(THROW_SPEED).add(0.0D, THROW_LIFT, 0.0D));
    Vec3 leverArm = rigidBody().toWorldDirection(new Vec3(0.0D, -HALF_HEIGHT, 0.0D));
    var random = level().getRandom();
    var variance = new Vec3(random.nextDouble(), random.nextDouble(), random.nextDouble()).scale(0.2).subtract(0.4);
    rigidBody().applyAngularImpulse(leverArm.cross(dir.add(variance).scale(-THROW_SPIN_IMPULSE)));
  }

  public Vec3 flamePosition(float partialTick) {
    Vec3 local = flameOffsetLocal(getBlockState());
    Vector3f world = new Vector3f((float) local.x, (float) local.y, (float) local.z);
    getOrientation(partialTick).transform(world);
    Vec3 pos = getPosition(partialTick);
    return new Vec3(pos.x + world.x, pos.y + world.y, pos.z + world.z);
  }

  private static Vec3 flameOffsetLocal(BlockState state) {
    double y = FLAME_HEIGHT - HALF_HEIGHT;
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      Direction out = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
      return new Vec3(
          out.getStepX() * WALL_FLAME_OFFSET, y, out.getStepZ() * WALL_FLAME_OFFSET);
    }

    return new Vec3(0.0D, y, 0.0D);
  }

  public static EntityTorch create(Level level, BlockState blockState, Vec3 position) {
    var block = blockState.getBlock();
    if (block == Blocks.REDSTONE_TORCH || block == Blocks.REDSTONE_WALL_TORCH) {
      return new EntityRedstoneTorch(level, position);
    }
    if (block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH) {
      return new EntitySoulTorch(level, position);
    }
    if (block == Blocks.COPPER_TORCH || block == Blocks.COPPER_WALL_TORCH) {
      return new EntityCopperTorch(level, position);
    }

    return new EntityTorch(level, blockState, position);
  }

  protected ItemStack pickupItemStack() {
    return itemStackForBlockState(getBlockState());
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

  @Override
  protected Collider createCollider() {
    return BoxCollider.box(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
  }

  public BlockState getBlockState() {
    return entityData.get(DATA_BLOCK_STATE);
  }

  public void setBlockState(BlockState blockState) {
    entityData.set(DATA_BLOCK_STATE, blockState);
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
      TorchLight.update(this);
    }
  }

  @Override
  public void remove(RemovalReason reason) {
    TorchLight.clear(this);
    super.remove(reason);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_BLOCK_STATE, Blocks.TORCH.defaultBlockState());
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    super.readAdditionalSaveData(input);
    input.read("BlockState", BlockState.CODEC).ifPresent(this::setBlockState);
    input.read("LightBlockPos", BlockPos.CODEC).ifPresent(this::setLightBlockPos);
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    super.addAdditionalSaveData(output);
    output.store("BlockState", BlockState.CODEC, getBlockState());
    output.storeNullable("LightBlockPos", BlockPos.CODEC, lightBlockPos);
  }

  @Override
  protected void onSurfaceInput(Player player, Vec3 surfacePosition, Vec3 surfaceNormal) {
    if (player instanceof ServerPlayer serverPlayer) {
      tryPickUp(serverPlayer);
    }
  }

  private boolean tryPickUp(ServerPlayer player) {
    if (!isAlive() || !(level() instanceof ServerLevel serverLevel)) {
      return false;
    }

    var soundType = getBlockState().getSoundType();
    serverLevel.playSound(null, getX(), getY(), getZ(), soundType.getBreakSound(), SoundSource.NEUTRAL, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
    if (!player.isCreative()) {
      ItemStack stack = pickupItemStack();
      stack.setCount(1);

      ItemEntity itemEntity = new ItemEntity(serverLevel, getX(), getY(), getZ(), stack);
      itemEntity.setNoPickUpDelay();
      itemEntity.setTarget(player.getUUID());
      itemEntity.setThrower(player);
      serverLevel.addFreshEntity(itemEntity);
      itemEntity.playerTouch(player);
    }

    serverLevel.sendParticles(
      new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
      getX(),
      getY(),
      getZ(),
      10,
      getBbWidth() / 4.0F,
      getBbHeight() / 4.0F,
      getBbWidth() / 4.0F,
      0.05
    );

    discard();
    return true;
  }
}
