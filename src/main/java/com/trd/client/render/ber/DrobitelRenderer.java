package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import com.trd.multiblock.industrial.drobitel.DrobitelBlock;
import com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class DrobitelRenderer implements BlockEntityRenderer<DrobitelBlockEntity> {

    public DrobitelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DrobitelBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(DrobitelBlock.FACING) ? state.getValue(DrobitelBlock.FACING) : Direction.NORTH;

        float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);
        float bladeAngle = Math.abs(ClientKineticAngleTracker.getAngle(be, null, partialTick));

        PartialModel shaftModel = ModModels.SHAFT_MODELS.get("shaft_light_iron");
        PartialModel bladeModel = ModModels.CRUSHER_BLADES;

        // Внешние сетевые валы
        renderShaft(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, facing.getStepX(), facing.getStepZ(), currentAngle);
        renderShaft(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, -facing.getStepX(), -facing.getStepZ(), currentAngle);

        // Правые части (вращаются по часовой: -bladeAngle)
        if (be.getHasBlade2() == 1) {
            renderPart(bladeModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, -0.1f, 1.3225f, 0.525f, -bladeAngle, false, 180f);
        }
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, -0.1f, 1.3225f, -0.275f, -bladeAngle, true, 0f);
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, -0.1f, 1.3225f, 0.525f, -bladeAngle, true, 0f);
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, -0.1f, 1.3225f, 1.325f, -bladeAngle, true, 0f);

        // Левые части (вращаются против часовой: bladeAngle)
        if (be.getHasBlade1() == 1) {
            renderPart(bladeModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, 1.1f, 1.3225f, 0.525f, bladeAngle, false, 0f);
        }
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, 1.1f, 1.3225f, -0.275f, bladeAngle, true, 0f);
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, 1.1f, 1.3225f, 0.525f, bladeAngle, true, 0f);
        renderPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, 1.1f, 1.3225f, 1.325f, bladeAngle, true, 0f);
    }

    private void renderShaft(PartialModel model, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay, Direction facing, int offsetX, int offsetZ, float angle) {
        poseStack.pushPose();
        poseStack.translate(offsetX, 0, offsetZ);
        poseStack.translate(0.5f, 0.5f, 0.5f);

        if (facing.getAxis() == Direction.Axis.Z) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        }

        poseStack.mulPose(Axis.XP.rotation(angle));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        FlywheelFallbackHelper.renderPartialModel(model, poseStack, buffer, state, light, overlay);
        poseStack.popPose();
    }

    private void renderPart(PartialModel model, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay, Direction facing, float x, float y, float z, float angle, boolean isBlockModel, float extraRotY) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.ZP.rotation(angle));

        if (extraRotY != 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(extraRotY));
        }

        if (isBlockModel) {
            poseStack.translate(-0.5f, -0.5f, -0.5f);
        }

        FlywheelFallbackHelper.renderPartialModel(model, poseStack, buffer, state, light, overlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DrobitelBlockEntity be) {
        return true;
    }
}
