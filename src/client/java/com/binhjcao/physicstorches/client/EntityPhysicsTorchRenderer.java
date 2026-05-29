package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityPhysicsTorch;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class EntityPhysicsTorchRenderer
    extends EntityRenderer<EntityPhysicsTorch, EntityPhysicsTorchRenderer.TorchRenderState> {
  public EntityPhysicsTorchRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  public TorchRenderState createRenderState() {
    return new TorchRenderState();
  }

  @Override
  public void extractRenderState(
      EntityPhysicsTorch entity, TorchRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);

    BlockPos blockPos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
    state.movingBlockRenderState.randomSeedPos = blockPos;
    state.movingBlockRenderState.blockPos = blockPos;
    state.movingBlockRenderState.blockState = entity.getBlockState();
    state.tilt = entity.getXRot(partialTick);
    state.tiltYaw = entity.getYRot(partialTick);
    state.roll = entity.getRoll(partialTick);

    if (entity.level() instanceof ClientLevel clientLevel) {
      state.movingBlockRenderState.biome = clientLevel.getBiome(blockPos);
      state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
      state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
    }
  }

  @Override
  public void submit(
      TorchRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector queue,
      CameraRenderState cameraState) {
    BlockState blockState = state.movingBlockRenderState.blockState;
    if (blockState == null || blockState.getRenderShape() != RenderShape.MODEL) {
      return;
    }

    float pivotY = EntityPhysicsTorch.pivotYOffset(state.tilt);

    poseStack.pushPose();
    poseStack.translate(-0.5D, 0.0D, -0.5D);
    poseStack.translate(0.5D, pivotY, 0.5D);
    poseStack.mulPose(
        EntityPhysicsTorch.torchRotation(state.tilt, state.tiltYaw, state.roll));
    poseStack.translate(-0.5D, -pivotY, -0.5D);
    queue.submitMovingBlock(poseStack, state.movingBlockRenderState);
    poseStack.popPose();
    super.submit(state, poseStack, queue, cameraState);
  }

  public static final class TorchRenderState extends EntityRenderState {
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public float tilt;
    public float tiltYaw;
    public float roll;
  }
}
