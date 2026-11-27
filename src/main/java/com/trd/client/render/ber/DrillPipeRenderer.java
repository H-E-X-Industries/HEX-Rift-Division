package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.drilling.DrillPipeBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Fallback BER для буровой трубы (когда Flywheel отключён или активен shadow-pass Iris).
 */
public class DrillPipeRenderer implements BlockEntityRenderer<DrillPipeBlockEntity> {

    public DrillPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(DrillPipeBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 4.0, p.getZ() + 1.0);
    }

    @Override
    public boolean shouldRenderOffScreen(DrillPipeBlockEntity be) {
        return true;
    }

    @Override
    public void render(DrillPipeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.isRemoved() || FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;
        if (be.isHiddenForColumnDescent()) return;

        Level level = be.getLevel();
        int topLight = level != null ? LevelRenderer.getLightColor(level, be.getBlockPos().above(3)) : packedLight;

        float baseYawDeg = 180.0f - be.getFacing().toYRot();
        float spinRad = be.getRenderSpinAngleRad(partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseYawDeg));
        poseStack.mulPose(Axis.YP.rotation(spinRad));
        FlywheelFallbackHelper.renderPartialModel(ModModels.DRILL_PIPE, poseStack, bufferSource, be.getBlockState(), topLight, packedOverlay);
        poseStack.popPose();
    }
}
