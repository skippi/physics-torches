package com.binhjcao.physicstorches.entity;

import com.binhjcao.physicstorches.PhysicsTorchesEntities;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class EntityPhysicsTorch extends Entity {
  public static final double HALF_WIDTH = 0.0625D;
  public static final double HEIGHT = 0.625D;
  private static final double GRAVITY = 0.04D;
  private static final double AIR_DRAG = 0.99D;
  private static final double GROUND_DRAG = 0.7D;
  private static final double REST_VELOCITY_SQR = 1.0E-4D;
  public static final float FLAT_TILT_ANGLE = 75.0F;
  private static final float TOPPLE_IMPULSE = 2.8F;
  private static final float TILT_DAMPING = 0.93F;
  private static final float ROLL_DAMPING = 0.86F;
  private static final float GRAVITY_TORQUE = 0.55F;

  private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
      SynchedEntityData.defineId(EntityPhysicsTorch.class, EntityDataSerializers.BLOCK_STATE);
  private static final EntityDataAccessor<Float> DATA_ROLL =
      SynchedEntityData.defineId(EntityPhysicsTorch.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Boolean> DATA_FLAT_SETTLED =
      SynchedEntityData.defineId(EntityPhysicsTorch.class, EntityDataSerializers.BOOLEAN);

  private float tiltVel;
  private float rollVel;
  private float rollO;
  private boolean needsFlatGroundSnap;

  public EntityPhysicsTorch(EntityType<? extends EntityPhysicsTorch> type, Level level) {
    super(type, level);
  }

  public EntityPhysicsTorch(Level level, BlockState blockState, Vec3 position, Vec3 velocity) {
    this(PhysicsTorchesEntities.PHYSICS_TORCH, level);
    setBlockState(blockState);
    setPos(position.x, position.y, position.z);
    setDeltaMovement(velocity);
    refreshBoundingBox();
  }

  public static boolean throwFromPlayer(ServerPlayer player, ItemStack stack) {
    BlockState blockState = blockStateForTorch(stack);
    Vec3 look = player.getLookAngle();
    Vec3 spawn = player.getEyePosition().add(look.scale(0.2D));
    Vec3 position = new Vec3(spawn.x, spawn.y - HEIGHT, spawn.z);
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

  public float getTilt() {
    return getXRot();
  }

  public void setTilt(float tilt) {
    setXRot(Mth.clamp(tilt, 0.0F, 90.0F));
  }

  public float getTiltYaw() {
    return getYRot();
  }

  public void setTiltYaw(float yaw) {
    setYRot(yaw);
  }

  public float getRoll() {
    return entityData.get(DATA_ROLL);
  }

  public float getRoll(float partialTick) {
    return partialTick == 1.0F ? getRoll() : Mth.lerp(partialTick, rollO, getRoll());
  }

  public void setRoll(float roll) {
    rollO = getRoll();
    entityData.set(DATA_ROLL, roll);
  }

  public boolean isFlat() {
    return getTilt() >= FLAT_TILT_ANGLE || isFlatSettled();
  }

  public boolean isFlatSettled() {
    return entityData.get(DATA_FLAT_SETTLED);
  }

  private boolean isAtRest() {
    return getDeltaMovement().lengthSqr() < REST_VELOCITY_SQR
        && Math.abs(tiltVel) < 0.01F
        && Math.abs(rollVel) < 0.01F;
  }

  private void settleFlat() {
    entityData.set(DATA_FLAT_SETTLED, true);
    setTilt(90.0F);
    setRoll(0.0F);
    tiltVel = 0.0F;
    rollVel = 0.0F;
    setDeltaMovement(Vec3.ZERO);
  }

  private void wakeFlat() {
    entityData.set(DATA_FLAT_SETTLED, false);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_BLOCK_STATE, Blocks.TORCH.defaultBlockState());
    builder.define(DATA_ROLL, 0.0F);
    builder.define(DATA_FLAT_SETTLED, false);
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    input.read("BlockState", BlockState.CODEC).ifPresent(this::setBlockState);
    input.read("Roll", Codec.FLOAT).ifPresent(this::setRoll);
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    output.store("BlockState", BlockState.CODEC, getBlockState());
    output.store("Roll", Codec.FLOAT, getRoll());
  }

  @Override
  public void tick() {
    super.tick();

    if (isInWater()) {
      setDeltaMovement(getDeltaMovement().scale(0.8D));
    }

    refreshBoundingBox();
    if (!level().isClientSide()) {
      applyNearbyEntityPushes();
      tickOrientation();
      if (needsFlatGroundSnap) {
        snapFlatToGround();
        needsFlatGroundSnap = false;
      } else if (!isFlat()) {
        alignToGround();
      }
    }
    refreshBoundingBox();

    if (!level().isClientSide() && isFlatSettled() && isAtRest()) {
      correctFlatGroundPenetration();
      return;
    }

    Vec3 motion = getDeltaMovement();
    if (!onGround()) {
      motion = motion.add(0.0D, -GRAVITY, 0.0D).scale(AIR_DRAG);
    } else if (isFlat()) {
      motion = new Vec3(motion.x * GROUND_DRAG, 0.0D, motion.z * GROUND_DRAG);
    } else if (getTilt() < 0.5F && Math.abs(tiltVel) < 0.01F && motion.horizontalDistanceSqr() < REST_VELOCITY_SQR) {
      motion = Vec3.ZERO;
    } else {
      motion = new Vec3(motion.x * 0.35D, 0.0D, motion.z * 0.35D);
    }

    setDeltaMovement(motion);
    move(MoverType.SELF, motion);
    refreshBoundingBox();

    if (!level().isClientSide() && onGround()) {
      applyNearbyEntityPushes();
      tickOrientation();
      refreshBoundingBox();
    }

    if (horizontalCollision) {
      setDeltaMovement(getDeltaMovement().multiply(-0.2D, 1.0D, -0.2D));
      if (isFlat()) {
        rollVel *= -0.35F;
      }
    }

    if (onGround()) {
      Vec3 settled = getDeltaMovement();
      if (!isFlat() && getTilt() < 0.5F && settled.lengthSqr() < REST_VELOCITY_SQR) {
        setDeltaMovement(Vec3.ZERO);
      } else if (isFlat() && settled.lengthSqr() < REST_VELOCITY_SQR && Math.abs(rollVel) < 0.01F) {
        settleFlat();
      } else if (isFlat()) {
        setDeltaMovement(new Vec3(settled.x, 0.0D, settled.z));
      } else {
        setDeltaMovement(new Vec3(settled.x * 0.5D, 0.0D, settled.z * 0.5D));
      }
    }

    if (!level().isClientSide() && isFlat()) {
      correctFlatGroundPenetration();
    }
    refreshBoundingBox();
  }

  private void tickOrientation() {
    if (isFlatSettled()) {
      settleFlat();
      return;
    }

    if (isFlat()) {
      setTilt(90.0F);
      tiltVel = 0.0F;
      if (Math.abs(rollVel) > 0.02F) {
        setRoll(getRoll() + rollVel);
        rollVel *= ROLL_DAMPING;
      } else {
        rollVel = 0.0F;
        setRoll(0.0F);
        if (isAtRest()) {
          settleFlat();
        }
      }
      return;
    }

    wakeFlat();

    if (onGround() && getTilt() > 0.01F) {
      float tiltRadians = getTilt() * (float) (Math.PI / 180.0);
      tiltVel += GRAVITY_TORQUE * Mth.sin(tiltRadians);
    }

    tiltVel *= TILT_DAMPING;
    setTilt(getTilt() + tiltVel);

    if (getTilt() >= FLAT_TILT_ANGLE) {
      setTilt(90.0F);
      rollVel += tiltVel * 0.5F;
      tiltVel = 0.0F;
      needsFlatGroundSnap = true;
      wakeFlat();
    }
  }

  private void applyNearbyEntityPushes() {
    AABB area = getBoundingBox().inflate(0.35D, 0.25D, 0.35D);
    for (Entity entity : level().getEntities(this, area, candidate -> candidate.isAlive() && !candidate.isSpectator())) {
      applyPushFrom(entity);
    }
  }

  private void applyPushFrom(Entity entity) {
    double deltaX = entity.getX() - entity.xOld;
    double deltaZ = entity.getZ() - entity.zOld;
    double speed = Math.hypot(deltaX, deltaZ);
    speed = Math.max(speed, entity.getDeltaMovement().horizontalDistance());
    if (speed < 0.001D) {
      if (!getBoundingBox().intersects(entity.getBoundingBox())) {
        return;
      }
      speed = 0.12D;
    }

    double dx = getX() - entity.getX();
    double dz = getZ() - entity.getZ();
    double distSq = dx * dx + dz * dz;
    if (distSq < 1.0E-8D) {
      dx = deltaX;
      dz = deltaZ;
      distSq = dx * dx + dz * dz;
      if (distSq < 1.0E-8D) {
        return;
      }
    }

    double dist = Math.sqrt(distSq);
    double pushX = dx / dist;
    double pushZ = dz / dist;
    float pushStrength = (float) Math.min(speed * 8.0D, 2.0F);

    if (isFlatSettled()) {
      wakeFlat();
    }

    if (getTilt() >= FLAT_TILT_ANGLE) {
      setDeltaMovement(
          getDeltaMovement().add(pushX * pushStrength * 0.06D, 0.0D, pushZ * pushStrength * 0.06D));
      return;
    }

    setTiltYaw((float) (Math.toDegrees(Math.atan2(-pushX, pushZ))));
    float impulse = Math.max(pushStrength * TOPPLE_IMPULSE, 0.6F);
    tiltVel += impulse;
    setTilt(Math.min(getTilt() + impulse * 0.45F, FLAT_TILT_ANGLE - 0.5F));
  }

  @Override
  protected AABB makeBoundingBox(Vec3 position) {
    return computeRotatedBoundingBox(position.x, position.y, position.z, getTilt(), getTiltYaw(), getRoll());
  }

  private void refreshBoundingBox() {
    setBoundingBox(makeBoundingBox(position()));
  }

  private void alignToGround() {
    AABB box = getBoundingBox();
    double groundY = findSupportSurfaceY(box);
    if (groundY == Double.NEGATIVE_INFINITY) {
      return;
    }

    double lift = groundY - box.minY;
    if (lift > 1.0E-4D) {
      setPos(getX(), getY() + lift, getZ());
    }
  }

  private void snapFlatToGround() {
    AABB box = getBoundingBox();
    double groundY = findSupportSurfaceY(box);
    if (groundY == Double.NEGATIVE_INFINITY) {
      return;
    }

    double lift = groundY - box.minY;
    if (lift > 1.0E-4D) {
      setPos(getX(), getY() + lift, getZ());
      refreshBoundingBox();
    }
  }

  private void correctFlatGroundPenetration() {
    AABB box = getBoundingBox();
    double groundY = findSupportSurfaceY(box);
    if (groundY == Double.NEGATIVE_INFINITY) {
      return;
    }

    double penetration = groundY - box.minY;
    if (penetration > 1.0E-3D) {
      setPos(getX(), getY() + penetration, getZ());
      refreshBoundingBox();
    }
  }

  private double findSupportSurfaceY(AABB box) {
    int minBlockX = Mth.floor(box.minX);
    int maxBlockX = Mth.floor(box.maxX);
    int minBlockZ = Mth.floor(box.minZ);
    int maxBlockZ = Mth.floor(box.maxZ);
    double highestGround = Double.NEGATIVE_INFINITY;

    for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
      for (int blockZ = minBlockZ; blockZ <= maxBlockZ; blockZ++) {
        highestGround = Math.max(highestGround, findHighestCollisionTop(blockX, blockZ, box.minY));
      }
    }

    return highestGround;
  }

  private double findHighestCollisionTop(int blockX, int blockZ, double searchFromY) {
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(blockX, Mth.floor(searchFromY), blockZ);

    for (int depth = 0; depth <= 2; depth++) {
      pos.setY(Mth.floor(searchFromY) - depth);
      BlockState state = level().getBlockState(pos);
      VoxelShape shape = state.getCollisionShape(level(), pos);
      if (!shape.isEmpty()) {
        return pos.getY() + shape.max(Direction.Axis.Y);
      }
    }

    return Double.NEGATIVE_INFINITY;
  }

  public static float pivotYOffset(float tilt) {
    return tilt >= FLAT_TILT_ANGLE ? (float) HALF_WIDTH : 0.0F;
  }

  static AABB computeRotatedBoundingBox(
      double x, double y, double z, float tilt, float tiltYaw, float roll) {
    if (tilt < 0.5F) {
      return new AABB(
          x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
    }

    float pivotY = pivotYOffset(tilt);
    double pivotWorldY = y + pivotY;
    Quaternionf rotation = torchRotation(tilt, tiltYaw, roll);
    Vector3f point = new Vector3f();
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;

    for (float localX : new float[] {(float) -HALF_WIDTH, (float) HALF_WIDTH}) {
      for (float localY : new float[] {0.0F, (float) HEIGHT}) {
        for (float localZ : new float[] {(float) -HALF_WIDTH, (float) HALF_WIDTH}) {
          rotation.transform(localX, localY - pivotY, localZ, point);
          minX = Math.min(minX, x + point.x);
          minY = Math.min(minY, pivotWorldY + point.y);
          minZ = Math.min(minZ, z + point.z);
          maxX = Math.max(maxX, x + point.x);
          maxY = Math.max(maxY, pivotWorldY + point.y);
          maxZ = Math.max(maxZ, z + point.z);
        }
      }
    }

    return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
  }

  public static Quaternionf torchRotation(float tilt, float tiltYaw, float roll) {
    Quaternionf rotation =
        new Quaternionf()
            .rotateY((float) -Math.toRadians(tiltYaw))
            .rotateX((float) Math.toRadians(tilt));
    if (tilt >= FLAT_TILT_ANGLE && Math.abs(roll) > 0.5F) {
      rotation.rotateX((float) Math.toRadians(roll));
    }
    return rotation;
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean canBeCollidedWith(@Nullable Entity other) {
    return true;
  }

  @Override
  public boolean canCollideWith(Entity entity) {
    return !isPassengerOfSameVehicle(entity);
  }

  @Override
  public void push(Entity entity) {
    if (!level().isClientSide()) {
      applyPushFrom(entity);
    }
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }
}
