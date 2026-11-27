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
import net.minecraft.core.Direction;
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
        if (be.isHorizontal()) {
            BlockPos end = p.relative(be.getFacing(), 3);
            return new AABB(
                    Math.min(p.getX(), end.getX()),
                    p.getY(),
                    Math.min(p.getZ(), end.getZ()),
                    Math.max(p.getX(), end.getX()) + 1.0,
                    p.getY() + 1.0,
                    Math.max(p.getZ(), end.getZ()) + 1.0
            );
        }
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
        Direction facing = be.getFacing();
        boolean horizontal = be.isHorizontal();
        BlockPos lightPos = horizontal ? be.getBlockPos().relative(facing, 1) : be.getBlockPos().above(3);
        int topLight = level != null ? LevelRenderer.getLightColor(level, lightPos) : packedLight;

        poseStack.pushPose();
        if (horizontal) {
            poseStack.translate(0.5f - 0.5f * facing.getStepX(), 0.5f, 0.5f - 0.5f * facing.getStepZ());
            switch (facing) {
                case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
                case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
                default -> {}
            }
        } else {
            float baseYawDeg = 180.0f - facing.toYRot();
            float spinRad = be.getRenderSpinAngleRad(partialTick);
            poseStack.translate(0.5f, 0.0f, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(baseYawDeg));
            poseStack.mulPose(Axis.YP.rotation(spinRad));
        }
        FlywheelFallbackHelper.renderPartialModel(ModModels.DRILL_PIPE, poseStack, bufferSource, be.getBlockState(), topLight, packedOverlay);
        poseStack.popPose();
    }
}
