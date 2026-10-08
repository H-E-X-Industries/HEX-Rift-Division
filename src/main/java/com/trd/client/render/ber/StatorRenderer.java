package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.rotation.StatorBlock;
import com.trd.block.entity.industrial.rotation.StatorBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.item.industrial.energy.StatorCoilItem;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class StatorRenderer implements BlockEntityRenderer<StatorBlockEntity> {

    public StatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StatorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (FlywheelFallbackHelper.isFlywheelActive(be.getLevel())) return;

        BlockState state = be.getBlockState();
        Direction.Axis axis = state.hasProperty(StatorBlock.AXIS) ? state.getValue(StatorBlock.AXIS) : Direction.Axis.Z;
        Direction facing = state.hasProperty(StatorBlock.FACING) ? state.getValue(StatorBlock.FACING) : Direction.NORTH;

        // 1. Корпус статора
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        } else if (axis == Direction.Axis.Z) {
            // default
        } else if (axis == Direction.Axis.Y) {
            if (facing == Direction.WEST) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            } else if (facing == Direction.SOUTH) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            } else if (facing == Direction.EAST) {
                poseStack.mulPose(Axis.YP.rotationDegrees(270));
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.STATOR, poseStack, bufferSource, state, packedLight, packedOverlay);
        poseStack.popPose();

        // 2. Катушки статора (до 12 штук)
        IItemHandler handler = be.getCoilsInventory();
        int slots = Math.min(12, handler.getSlots());
        for (int i = 0; i < slots; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof StatorCoilItem coilItem) {
                String material = coilItem.getMaterialName();
                PartialModel model = ModModels.STATOR_COILS.getOrDefault(material, ModModels.STATOR_COILS.get("copper"));
                if (model == null) continue;

                poseStack.pushPose();
                poseStack.translate(0.5f, 0.5f, 0.5f);

                if (axis == Direction.Axis.X) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(90));
                } else if (axis == Direction.Axis.Z) {
                    // default
                } else if (axis == Direction.Axis.Y) {
                    if (facing == Direction.NORTH) {
                        poseStack.mulPose(Axis.YP.rotationDegrees(180));
                    } else if (facing == Direction.EAST) {
                        poseStack.mulPose(Axis.YP.rotationDegrees(90));
                    } else if (facing == Direction.WEST) {
                        poseStack.mulPose(Axis.YP.rotationDegrees(270));
                    }
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                }

                // Смещаем в центр кольца мультиблока (на 1 блок вверх)
                poseStack.translate(0f, 1f, 0f);

                // Поворачиваем радиально по кругу для текущего слота (30 градусов на слот)
                poseStack.mulPose(Axis.ZP.rotationDegrees(i * 30));

                // Сдвигаем наружу от центра на 1.1 блока
                poseStack.translate(0f, 1.1f, 0f);

                // Центрируем модель катушки
                poseStack.translate(-0.5f, -0.5f, -0.5f);

                FlywheelFallbackHelper.renderPartialModel(model, poseStack, bufferSource, state, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StatorBlockEntity be) {
        return true;
    }
}
