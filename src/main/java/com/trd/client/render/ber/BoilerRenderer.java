package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.boiler.BoilerBlock;
import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerRenderer implements BlockEntityRenderer<BoilerBlockEntity> {

    public BoilerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BoilerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(BoilerBlock.FACING) ? state.getValue(BoilerBlock.FACING) : Direction.NORTH;

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
        poseStack.translate(-1.0f, 0.0f, -1.0f);

        FlywheelFallbackHelper.renderPartialModel(ModModels.BOILER, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BoilerBlockEntity be) {
        return true;
    }
}
