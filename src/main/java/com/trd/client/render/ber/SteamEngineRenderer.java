package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import com.trd.multiblock.industrial.steam_engine.SteamEngineBlock;
import com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity> {

    public SteamEngineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SteamEngineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(SteamEngineBlock.FACING) ? state.getValue(SteamEngineBlock.FACING) : Direction.NORTH;
        Direction.Axis axis = facing.getAxis();

        float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);

        // 1. Статическая база
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.translate(0.0f, -0.5f, 0.0f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.STEAM_ENGINE_BASE, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Коленвал (crankshaft)
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.ZP.rotation(currentAngle));
        poseStack.translate(0.0f, -0.5625f, 0.03125f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.STEAM_ENGINE_CRANKSHAFT, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 3. Шатун (connecting rod)
        float R = 0.41f;
        float L = 1.225f;
        float xCrank = R * (float) Math.sin(currentAngle);
        float yCrank = -R * (float) Math.cos(currentAngle);
        float sinBeta = xCrank / L;
        float beta = (float) Math.asin(sinBeta);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.translate(0.0f, 0.0f, -0.0625f);
        poseStack.translate(xCrank, yCrank, 0);
        poseStack.mulPose(Axis.ZP.rotation(beta));
        FlywheelFallbackHelper.renderPartialModel(ModModels.STEAM_ENGINE_ROD, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SteamEngineBlockEntity be) {
        return true;
    }
}
