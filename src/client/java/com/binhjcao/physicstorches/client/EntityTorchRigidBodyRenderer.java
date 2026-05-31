package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityTorchRigidBody;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class EntityTorchRigidBodyRenderer
    extends EntityRenderer<EntityTorchRigidBody, EntityTorchRigidBodyRenderer.TorchRenderState> {
  public EntityTorchRigidBodyRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  public TorchRenderState createRenderState() {
    return new TorchRenderState();
  }

  @Override
  public void extractRenderState(
      EntityTorchRigidBody entity, TorchRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);

    Vec3 renderPos = entity.getPosition(partialTick);
    BlockPos blockPos = BlockPos.containing(renderPos);
    state.movingBlockRenderState.randomSeedPos = blockPos;
    state.movingBlockRenderState.blockPos = blockPos;
    state.movingBlockRenderState.blockState = entity.getBlockState();
    state.orientation.set(entity.getOrientation(partialTick));

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

    poseStack.pushPose();
    poseStack.mulPose(state.orientation);
    poseStack.translate(-0.5D, -EntityTorchRigidBody.HALF_HEIGHT, -0.5D);
    queue.submitMovingBlock(poseStack, state.movingBlockRenderState);
    poseStack.popPose();
    super.submit(state, poseStack, queue, cameraState);
  }

  public static final class TorchRenderState extends EntityRenderState {
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public Quaternionf orientation = new Quaternionf();
  }
}
