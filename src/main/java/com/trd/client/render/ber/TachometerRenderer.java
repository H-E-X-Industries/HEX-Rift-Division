package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.rotation.TachometerBlock;
import com.trd.block.entity.industrial.rotation.TachometerBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class TachometerRenderer implements BlockEntityRenderer<TachometerBlockEntity> {

    public TachometerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TachometerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(TachometerBlock.FACING)) return;
        Direction facing = state.getValue(TachometerBlock.FACING);
        Direction.Axis axis = facing.getAxis();

        // 1. Статичный корпус
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.TACHOMETER, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Вращающийся вал
        if (be.hasShaft() && be.getShaftMaterial() != null && be.getShaftDiameter() != null) {
            float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);
            String matName = be.getShaftMaterial().name().toLowerCase();
            String diaName = be.getShaftDiameter().name().toLowerCase();
            String shaftName = "shaft_" + diaName + "_" + matName;

            PartialModel shaftModel = ModModels.SHAFT_MODELS.get(shaftName);
            if (shaftModel == null) {
                shaftModel = ModModels.HALF_SHAFT;
            }

            poseStack.pushPose();
            poseStack.translate(0.5f, 0.5f, 0.5f);
            if (axis == Direction.Axis.X) {
                poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
            } else if (axis == Direction.Axis.Y) {
                poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
            } else if (facing == Direction.SOUTH) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            poseStack.mulPose(Axis.ZP.rotation(currentAngle));
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            FlywheelFallbackHelper.renderPartialModel(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TachometerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
