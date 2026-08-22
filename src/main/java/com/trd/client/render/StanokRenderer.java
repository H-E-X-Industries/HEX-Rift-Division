package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

public class StanokRenderer implements BlockEntityRenderer<StanokBlockEntity> {

    public StanokRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StanokBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        CarriageType carriage = be.getCurrentCarriageType();
        if (carriage != CarriageType.PRESS && carriage != CarriageType.FREZA) return;

        StanokRecipe recipe = be.getCurrentRecipe();
        if (recipe == null) return;

        if (recipe.getInputs().isEmpty() || recipe.getOutputs().isEmpty()) return;

        int prog = be.getData().get(0);
        int maxProg = be.getData().get(1);

        float phase = 0f;
        if (maxProg > 0) {
            float interp = prog;
            if (prog > 0 && be.getSpeed() != 0) interp += partialTick;
            phase = Math.min(1f, interp / maxProg);
        }

        if (prog == 0 && !be.hasRequiredInputsPublic(recipe)) return;

        ItemStack toRender = (phase < 0.5f) ? recipe.getInputs().get(0) : recipe.getOutputs().get(0);

        Direction facing = be.getBlockState().hasProperty(StanokBlock.FACING) ?
                be.getBlockState().getValue(StanokBlock.FACING) : Direction.NORTH;
        float facingRot = 0;
        if (facing == Direction.NORTH) facingRot = 180;
        else if (facing == Direction.EAST) facingRot = 90;
        else if (facing == Direction.SOUTH) facingRot = 0;
        else if (facing == Direction.WEST) facingRot = -90;

        poseStack.pushPose();

        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRot));
        poseStack.translate(-0.5, 0, -0.5);

        // Координаты из Blockbench: -23.2269, 18.2554, -15.4183
        // Сдвиг на 1 пиксель вправо (вычитаем -1.0f по оси X станка)
        float bbX = (-23.2269f - 1.0f) / 16f;
        float bbY = 18.2554f / 16f;
        float bbZ = (-15.4183f - 3.0f) / 16f;

        if (carriage == CarriageType.PRESS) {
            // === РЕНДЕР ДЛЯ ПРЕССА (2D ПРЕДМЕТЫ / СЛИТКИ) ===
            poseStack.pushPose();
            poseStack.translate(2.0f + bbX, bbY, 2.0f + bbZ);
            
            // В Майнкрафте предметы типа GROUND по умолчанию стоят вертикально!
            // Поэтому нам ОБЯЗАТЕЛЬНО нужен поворот по X на 90, чтобы слиток "упал" на стол.
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(0.5f, 0.5f, 0.5f);
            
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    toRender, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), 0
            );
            poseStack.popPose();
            
        } else if (carriage == CarriageType.FREZA) {
            // === РЕНДЕР ДЛЯ ФРЕЗЫ ===
            poseStack.pushPose();
            poseStack.translate(2.0f + bbX, bbY, 2.0f + bbZ);
            
            if (phase < 0.5f) {
                // В первой половине (сырьё/слиток) — рендерим В ТОЧНОСТИ как под прессом
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
                poseStack.scale(0.5f, 0.5f, 0.5f);
            } else {
                // Во второй половине (готовый вал) — рендерим как 3D модель вала
                poseStack.translate(0, -0.15f, 0); 
                poseStack.mulPose(Axis.YP.rotationDegrees(90)); 
                poseStack.scale(1.0f, 1.0f, 1.0f);
            }
            
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    toRender, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), 0
            );
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(StanokBlockEntity pBlockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
