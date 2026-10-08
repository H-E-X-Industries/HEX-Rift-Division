package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.entity.industrial.rotation.MillstoneBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class MillstoneRenderer implements BlockEntityRenderer<MillstoneBlockEntity> {

    public MillstoneRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MillstoneBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();

        // 1. Статичная база
        poseStack.pushPose();
        FlywheelFallbackHelper.renderPartialModel(ModModels.JERNOVA_BASE, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Вращающийся жернов (верхняя часть)
        float angle;
        if (be.isGrinding() && be.getLevel() != null) {
            angle = (be.getLevel().getGameTime() + partialTick) * (360.0f / MillstoneBlockEntity.GRIND_COOLDOWN);
        } else {
            angle = be.getRotationAngle();
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
        poseStack.translate(-0.5f, 0.0f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.JERNOVA_TOP, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(MillstoneBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
