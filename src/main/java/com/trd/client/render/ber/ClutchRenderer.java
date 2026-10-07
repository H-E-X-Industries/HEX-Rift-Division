package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.block.basic.industrial.rotation.BearingBlock;
import com.trd.block.basic.industrial.rotation.ClutchBlock;
import com.trd.block.basic.industrial.rotation.MotorElectroBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.basic.industrial.rotation.TachometerBlock;
import com.trd.block.entity.industrial.rotation.BearingBlockEntity;
import com.trd.block.entity.industrial.rotation.ClutchBlockEntity;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.block.entity.industrial.rotation.TachometerBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ClutchRenderer implements BlockEntityRenderer<ClutchBlockEntity> {

    public ClutchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ClutchBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(ClutchBlock.FACING)) return;
        Direction facing = state.getValue(ClutchBlock.FACING);
        Direction.Axis axis = facing.getAxis();

        // 1. Корпус муфты (static body)
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
        FlywheelFallbackHelper.renderPartialModel(ModModels.CLUTCH_BLOCK, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Валы (front & back)
        if (!be.hasShaft()) return;

        ShaftMaterial currentMaterial = be.getShaftMaterial() != null ? be.getShaftMaterial() : ShaftMaterial.IRON;
        ShaftDiameter currentDiameter = be.getShaftDiameter() != null ? be.getShaftDiameter() : ShaftDiameter.LIGHT;

        String matName = currentMaterial.name().toLowerCase();
        String diaName = currentDiameter.name().toLowerCase();
        String shaftName = "shaft_" + diaName + "_" + matName;

        PartialModel shaftModel = ModModels.SHAFT_MODELS.get(shaftName);
        if (shaftModel == null) {
            shaftModel = ModModels.HALF_SHAFT;
        }

        boolean powered = state.getValue(ClutchBlock.POWERED);
        float currentAngleFront;
        float currentAngleBack;

        if (powered) {
            float angle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);
            currentAngleFront = angle;
            currentAngleBack = angle;
        } else {
            Level level = be.getLevel();
            BlockPos posFront = be.getBlockPos().relative(facing.getOpposite());
            BlockPos posBack = be.getBlockPos().relative(facing);

            BlockEntity beFront = level != null ? level.getBlockEntity(posFront) : null;
            BlockEntity beBack = level != null ? level.getBlockEntity(posBack) : null;

            if (isHalfShaftConnected(be, posFront, beFront) && beFront instanceof KineticNodeBlockEntity rotFront) {
                currentAngleFront = ClientKineticAngleTracker.getAngle(rotFront, facing, partialTick);
            } else {
                currentAngleFront = 0f;
            }

            if (isHalfShaftConnected(be, posBack, beBack) && beBack instanceof KineticNodeBlockEntity rotBack) {
                currentAngleBack = ClientKineticAngleTracker.getAngle(rotBack, facing, partialTick);
            } else {
                currentAngleBack = 0f;
            }
        }

        // Render Front Shaft
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.ZP.rotation(currentAngleFront));
        poseStack.scale(1f, 1f, 0.5f);
        poseStack.translate(0f, 0f, 0.5f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // Render Back Shaft
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.ZP.rotation(currentAngleBack));
        poseStack.scale(1f, 1f, 0.5f);
        poseStack.translate(0f, 0f, -0.5f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private boolean isHalfShaftConnected(ClutchBlockEntity clutchBE, BlockPos neighborPos, BlockEntity be) {
        if (be == null || be.isRemoved()) return false;
        if (!clutchBE.hasShaft()) return false;
        Level level = clutchBE.getLevel();
        if (level == null || !level.isLoaded(neighborPos)) return false;

        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.isAir()) return false;

        if (!(be instanceof Rotational)) return false;

        Direction.Axis myAxis = clutchBE.getBlockState().getValue(ClutchBlock.FACING).getAxis();
        ShaftDiameter myDia = clutchBE.getShaftDiameter();

        if (be instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == myDia &&
                        shaftBE.getBlockState().getValue(ShaftBlock.FACING).getAxis() == myAxis;
            }
        } else if (be instanceof BearingBlockEntity bearing) {
            return bearing.hasShaft() && bearing.getShaftDiameter() == myDia &&
                    bearing.getBlockState().getValue(BearingBlock.FACING).getAxis() == myAxis;
        } else if (be instanceof ClutchBlockEntity otherClutch) {
            return otherClutch.hasShaft() && otherClutch.getShaftDiameter() == myDia &&
                    otherClutch.getBlockState().getValue(ClutchBlock.FACING).getAxis() == myAxis;
        } else if (be instanceof TachometerBlockEntity tach) {
            return tach.hasShaft() && tach.getShaftDiameter() == myDia &&
                    tach.getBlockState().getValue(TachometerBlock.FACING).getAxis() == myAxis;
        } else if (be instanceof MotorElectroBlockEntity motor) {
            return myDia == ShaftDiameter.LIGHT &&
                    motor.getBlockState().getValue(MotorElectroBlock.FACING).getAxis() == myAxis;
        }
        return false;
    }

    @Override
    public boolean shouldRenderOffScreen(ClutchBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
