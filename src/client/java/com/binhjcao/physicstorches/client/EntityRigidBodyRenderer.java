package com.binhjcao.physicstorches.client;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class EntityRigidBodyRenderer
    extends EntityRenderer<EntityRigidBody, EntityRigidBodyRenderer.CubeRenderState> {
  private static final BlockState CUBE_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();

  public EntityRigidBodyRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  public CubeRenderState createRenderState() {
    return new CubeRenderState();
  }

  @Override
  public void extractRenderState(
      EntityRigidBody entity, CubeRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);

    BlockPos blockPos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
    state.movingBlockRenderState.randomSeedPos = blockPos;
    state.movingBlockRenderState.blockPos = blockPos;
    state.movingBlockRenderState.blockState = CUBE_BLOCK;
    state.orientation.set(entity.getOrientation(partialTick));

    if (entity.level() instanceof ClientLevel clientLevel) {
      state.movingBlockRenderState.biome = clientLevel.getBiome(blockPos);
      state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
      state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
    }
  }

  @Override
  public void submit(
      CubeRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector queue,
      CameraRenderState cameraState) {
    BlockState blockState = state.movingBlockRenderState.blockState;
    if (blockState == null || blockState.getRenderShape() != RenderShape.MODEL) {
      return;
    }

    poseStack.pushPose();
    poseStack.translate(-0.5D, 0.0D, -0.5D);
    poseStack.translate(0.5D, EntityRigidBody.HALF_SIZE, 0.5D);
    poseStack.mulPose(state.orientation);
    poseStack.translate(-0.5D, -EntityRigidBody.HALF_SIZE, -0.5D);
    queue.submitMovingBlock(poseStack, state.movingBlockRenderState);
    poseStack.popPose();
    super.submit(state, poseStack, queue, cameraState);
  }

  public static final class CubeRenderState extends EntityRenderState {
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public Quaternionf orientation = new Quaternionf();
  }
}
