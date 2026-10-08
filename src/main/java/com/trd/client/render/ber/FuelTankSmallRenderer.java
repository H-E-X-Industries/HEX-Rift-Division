package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlock;
import com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class FuelTankSmallRenderer implements BlockEntityRenderer<FuelTankSmallBlockEntity> {

    public FuelTankSmallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FuelTankSmallBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(FuelTankSmallBlock.FACING)) return;
        Direction facing = state.getValue(FuelTankSmallBlock.FACING);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        } else if (facing == Direction.WEST) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        } else if (facing == Direction.EAST) {
            poseStack.mulPose(Axis.YP.rotationDegrees(270));
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        poseStack.translate(0.5f, 0.0f, 0.5f);

        FlywheelFallbackHelper.renderPartialModel(ModModels.FUEL_TANK_SMALL, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(FuelTankSmallBlockEntity pBlockEntity) {
        return true;
    }
}
