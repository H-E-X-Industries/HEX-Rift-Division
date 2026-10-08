package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.fluids.WaterPumpBlock;
import com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class WaterPumpRenderer implements BlockEntityRenderer<WaterPumpBlockEntity> {

    public WaterPumpRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WaterPumpBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(WaterPumpBlock.FACING) ? state.getValue(WaterPumpBlock.FACING) : Direction.NORTH;
        Direction.Axis axis = facing.getAxis();

        float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);

        // 1. Корпус помпы
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.translate(-0.5f + (0.6f / 16.0f), -1.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.WATER_PUMP, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Вал
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.ZP.rotation(currentAngle));
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.SHAFT_MODELS.get("shaft_light_iron"), poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WaterPumpBlockEntity be) {
        return true;
    }
}
