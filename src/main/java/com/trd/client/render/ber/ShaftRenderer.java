package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.rotation.ClientKineticAngleTracker;
import com.trd.item.industrial.rotation.GearItem;
import com.trd.item.industrial.rotation.PulleyItem;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ShaftRenderer implements BlockEntityRenderer<ShaftBlockEntity> {

    private static final float TRACK_LENGTH = 3.0f / 16.0f;

    public ShaftRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShaftBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING)) return;
        Direction facing = state.getValue(ShaftBlock.FACING);

        float currentAngle = ClientKineticAngleTracker.getAngle(be, facing, partialTick);

        // 1. Вал
        ResourceLocation shaftId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String shaftName = shaftId != null ? shaftId.getPath() : "";
        PartialModel shaftModel = ModModels.SHAFT_MODELS.getOrDefault(shaftName, ModModels.HALF_SHAFT);

        renderShaftPart(shaftModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle);

        // 2. Шестерня (с фазовым смещением для правильного зацепления зубьев)
        ItemStack gearStack = be.getAttachedGear();
        int gearSize = state.getValue(ShaftBlock.GEAR_SIZE);
        if (gearSize > 0 && !gearStack.isEmpty() && gearStack.getItem() instanceof GearItem) {
            ResourceLocation gearId = BuiltInRegistries.ITEM.getKey(gearStack.getItem());
            String gearName = gearId != null ? gearId.getPath() : "";
            PartialModel gearModel = ModModels.GEAR_MODELS.get(gearName);

            if (gearModel != null) {
                int x = be.getBlockPos().getX();
                int y = be.getBlockPos().getY();
                int z = be.getBlockPos().getZ();

                int axisCoord = 0;
                if (facing.getAxis() == Direction.Axis.X) axisCoord = x;
                else if (facing.getAxis() == Direction.Axis.Y) axisCoord = y;
                else if (facing.getAxis() == Direction.Axis.Z) axisCoord = z;

                int parity = Math.abs(x + y + z + axisCoord + (gearSize == 2 ? 1 : 0)) % 2;
                float halfToothAngle = gearSize == 2 ? 11.25f : 22.5f;
                float phaseOffset = (float) Math.toRadians(parity == 0 ? halfToothAngle : 0);

                renderShaftPart(gearModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle + phaseOffset);
            }
        }

        // 3. Шкив (pulley)
        int pulleySize = state.getValue(ShaftBlock.PULLEY_SIZE);
        if (pulleySize > 0 && be.hasPulley()) {
            PartialModel pulleyModel = ModModels.PULLEY_MODELS.get("pulley");
            if (pulleyModel != null) {
                renderShaftPart(pulleyModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle);
            }
        }

        // 4. Конические шестерни (Bevel Gears)
        if (state.getValue(ShaftBlock.HAS_BEVEL_START) && be.hasBevelStart()) {
            PartialModel bevelModel = ModModels.BEVEL_GEAR;
            if (bevelModel != null) {
                renderBevelPart(bevelModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle, true);
            }
        }

        if (state.getValue(ShaftBlock.HAS_BEVEL_END) && be.hasBevelEnd()) {
            PartialModel bevelModel = ModModels.BEVEL_GEAR;
            if (bevelModel != null) {
                renderBevelPart(bevelModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle, false);
            }
        }

        // 5. Ротор (copper rotor)
        if (be.hasRotor()) {
            PartialModel rotorModel = ModModels.COPPER_ROTOR;
            if (rotorModel != null) {
                renderShaftPart(rotorModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle);
            }
        }

        // 6. Маховик (flywheel)
        if (be.hasFlywheel()) {
            PartialModel flywheelModel = ModModels.FLYWHEEL;
            if (flywheelModel != null) {
                renderShaftPart(flywheelModel, poseStack, bufferSource, state, packedLight, packedOverlay, facing, currentAngle);
            }
        }

        // 7. Ремень (3D траки)
        renderBelt(be, partialTick, poseStack, bufferSource, state, packedLight, packedOverlay, facing);
    }

    private void renderShaftPart(PartialModel model, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay, Direction facing, float angle) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }

        if (angle != 0) {
            poseStack.mulPose(Axis.ZP.rotation(angle));
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(model, poseStack, buffer, state, light, overlay);
        poseStack.popPose();
    }

    private void renderBevelPart(PartialModel model, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay, Direction facing, float angle, boolean isStart) {
        float shiftAmount = isStart ? -0.5f : 0.5f;
        float shiftX = 0, shiftY = 0, shiftZ = 0;
        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) shiftX = shiftAmount;
        if (axis == Direction.Axis.Y) shiftY = shiftAmount;
        if (axis == Direction.Axis.Z) shiftZ = shiftAmount;

        poseStack.pushPose();
        poseStack.translate(0.5f + shiftX, 0.5f + shiftY, 0.5f + shiftZ);

        Direction gearFacing;
        if (axis == Direction.Axis.X) gearFacing = isStart ? Direction.WEST : Direction.EAST;
        else if (axis == Direction.Axis.Y) gearFacing = isStart ? Direction.DOWN : Direction.UP;
        else gearFacing = isStart ? Direction.NORTH : Direction.SOUTH;

        if (gearFacing == Direction.EAST) {
            poseStack.mulPose(Axis.YP.rotationDegrees(270));
        } else if (gearFacing == Direction.WEST) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        } else if (gearFacing == Direction.UP) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        } else if (gearFacing == Direction.DOWN) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        } else if (gearFacing == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }

        float rotZ = (gearFacing != facing) ? -angle : angle;
        if (rotZ != 0) {
            poseStack.mulPose(Axis.ZP.rotation(rotZ));
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(model, poseStack, buffer, state, light, overlay);
        poseStack.popPose();
    }

    private float getPulleyRadius(ShaftBlockEntity be) {
        if (be.hasPulley() && be.getAttachedPulley().getItem() instanceof PulleyItem pulley) {
            return (pulley.getDiameterPixels() / 2.0f + 1.0f) / 16.0f;
        }
        return 0f;
    }

    private void renderBelt(ShaftBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay, Direction facing) {
        BlockPos connectedPos = be.getConnectedPulley();
        if (connectedPos == null) return;
        Level level = be.getLevel();
        if (level == null || !level.isLoaded(connectedPos)) return;
        if (!(level.getBlockEntity(connectedPos) instanceof ShaftBlockEntity otherBE)) return;
        if (!be.hasPulley() || !otherBE.hasPulley()) return;

        float r1 = getPulleyRadius(be);
        float r2 = getPulleyRadius(otherBE);
        if (r1 == 0 || r2 == 0) return;

        Direction.Axis axis = facing.getAxis();
        BlockPos pos = be.getBlockPos();
        float dx = connectedPos.getX() - pos.getX();
        float dy = connectedPos.getY() - pos.getY();
        float dz = connectedPos.getZ() - pos.getZ();

        float du = 0, dv = 0;
        if (axis == Direction.Axis.X) { du = dz; dv = dy; }
        else if (axis == Direction.Axis.Y) { du = dx; dv = dz; }
        else if (axis == Direction.Axis.Z) { du = dx; dv = dy; }

        float distance = (float) Math.sqrt(du * du + dv * dv);
        if (distance == 0) return;

        float baseAngle = (float) Math.atan2(dv, du);
        float alpha = (float) Math.asin((r1 - r2) / distance);
        float straightLength = (float) Math.sqrt(distance * distance - (r1 - r2) * (r1 - r2));

        float dirAngle1 = baseAngle - alpha;
        float touchAngle1 = dirAngle1 + (float) Math.PI / 2f;

        float dirAngle2 = baseAngle + alpha;
        float touchAngle2 = dirAngle2 - (float) Math.PI / 2f;

        float arcBStart = touchAngle1;
        float arcBEnd = touchAngle2;
        while (arcBEnd >= arcBStart) arcBEnd -= (float) (2 * Math.PI);
        float arcLengthB = (arcBStart - arcBEnd) * r2;

        float arcAStart = touchAngle2;
        float arcAEnd = touchAngle1;
        while (arcAEnd >= arcAStart) arcAEnd -= (float) (2 * Math.PI);
        float arcLengthA = (arcAStart - arcAEnd) * r1;

        float orbitTotalLength = straightLength * 2 + arcLengthB + arcLengthA;
        int trackCount = (int) Math.ceil(orbitTotalLength / TRACK_LENGTH);
        if (trackCount <= 0) return;
        float trackSpacing = orbitTotalLength / trackCount;

        float beltAngle = ClientKineticAngleTracker.getAngle(be, null, partialTick);
        float beltOffset = (-beltAngle * r1) % orbitTotalLength;
        if (beltOffset < 0) beltOffset += orbitTotalLength;

        PartialModel beltModel = ModModels.BELT_SEGMENT;
        if (beltModel == null) return;

        for (int i = 0; i < trackCount; i++) {
            float D = (beltOffset + i * trackSpacing) % orbitTotalLength;
            Point2D ptA = getPointOnPath(D, straightLength, arcLengthB, r1, r2, du, dv, touchAngle1, touchAngle2, arcBStart, arcBEnd, arcAStart, arcAEnd, orbitTotalLength);
            float dB = (D - TRACK_LENGTH + orbitTotalLength) % orbitTotalLength;
            Point2D ptB = getPointOnPath(dB, straightLength, arcLengthB, r1, r2, du, dv, touchAngle1, touchAngle2, arcBStart, arcBEnd, arcAStart, arcAEnd, orbitTotalLength);

            float cx = (ptA.u + ptB.u) / 2.0f;
            float cy = (ptA.v + ptB.v) / 2.0f;
            float angle = (float) Math.atan2(ptA.v - ptB.v, ptA.u - ptB.u);

            poseStack.pushPose();
            poseStack.translate(0.5f, 0.5f, 0.5f);

            if (axis == Direction.Axis.X) {
                poseStack.translate(0, cy, cx);
                poseStack.mulPose(Axis.XP.rotation(-angle));
            } else if (axis == Direction.Axis.Y) {
                poseStack.translate(cx, 0, cy);
                poseStack.mulPose(Axis.YP.rotation(-angle + (float) Math.PI / 2f));
                poseStack.mulPose(Axis.ZP.rotation((float) Math.PI / 2f));
            } else if (axis == Direction.Axis.Z) {
                poseStack.translate(cx, cy, 0);
                poseStack.mulPose(Axis.ZP.rotation(angle));
                poseStack.mulPose(Axis.YP.rotation((float) Math.PI / 2f));
            }

            poseStack.translate(-0.5f, -0.5f, -1.5f / 16.0f);
            FlywheelFallbackHelper.renderPartialModel(beltModel, poseStack, buffer, state, light, overlay);
            poseStack.popPose();
        }
    }

    private static class Point2D {
        final float u, v;
        Point2D(float u, float v) { this.u = u; this.v = v; }
    }

    private Point2D getPointOnPath(float d, float straightLength, float arcLengthB, float r1, float r2, float du, float dv, float touchAngle1, float touchAngle2, float arcBStart, float arcBEnd, float arcAStart, float arcAEnd, float orbitTotalLength) {
        if (d < straightLength) {
            float fraction = d / straightLength;
            float uA = r1 * (float) Math.cos(touchAngle1);
            float vA = r1 * (float) Math.sin(touchAngle1);
            float uB = du + r2 * (float) Math.cos(touchAngle1);
            float vB = dv + r2 * (float) Math.sin(touchAngle1);
            return new Point2D(uA + fraction * (uB - uA), vA + fraction * (vB - vA));
        }

        d -= straightLength;
        if (d < arcLengthB) {
            float fraction = d / arcLengthB;
            float angle = arcBStart - fraction * (arcBStart - arcBEnd);
            return new Point2D(du + r2 * (float) Math.cos(angle), dv + r2 * (float) Math.sin(angle));
        }

        d -= arcLengthB;
        if (d < straightLength) {
            float fraction = d / straightLength;
            float uB = du + r2 * (float) Math.cos(touchAngle2);
            float vB = dv + r2 * (float) Math.sin(touchAngle2);
            float uA = r1 * (float) Math.cos(touchAngle2);
            float vA = r1 * (float) Math.sin(touchAngle2);
            return new Point2D(uB + fraction * (uA - uB), vB + fraction * (vA - vB));
        }

        d -= straightLength;
        float fraction = d / Math.max(0.0001f, (orbitTotalLength - straightLength * 2 - arcLengthB));
        float angle = arcAStart - fraction * (arcAStart - arcAEnd);
        return new Point2D(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle));
    }

    @Override
    public boolean shouldRenderOffScreen(ShaftBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
