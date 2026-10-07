package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.rotation.HandCrankBlock;
import com.trd.block.entity.industrial.rotation.HandCrankBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class HandCrankRenderer implements BlockEntityRenderer<HandCrankBlockEntity> {

    public HandCrankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HandCrankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(HandCrankBlock.FACING) ? state.getValue(HandCrankBlock.FACING) : Direction.NORTH;
        Direction.Axis axis = facing.getAxis();

        float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Z) {
            if (facing == Direction.SOUTH) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
        } else if (axis == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 270 : 90));
        }

        poseStack.mulPose(Axis.ZP.rotation(currentAngle));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        FlywheelFallbackHelper.renderPartialModel(ModModels.HAND_CRANK, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(HandCrankBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
