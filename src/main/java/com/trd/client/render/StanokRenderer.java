package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.client.render.flywheel.ModModels;
import com.trd.client.render.flywheel.StanokVisual;
import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlock;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StanokRenderer implements BlockEntityRenderer<StanokBlockEntity> {

    public StanokRenderer(BlockEntityRendererProvider.Context context) {}

    public static boolean isFlywheelActive(@Nullable Level level) {
        return FlywheelFallbackHelper.isFlywheelActive(level);
    }

    @Override
    public void render(StanokBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Direction facing = be.getBlockState().hasProperty(StanokBlock.FACING) ?
                be.getBlockState().getValue(StanokBlock.FACING) : Direction.NORTH;
        float facingRot = 0;
        if (facing == Direction.NORTH) facingRot = 180;
        else if (facing == Direction.EAST) facingRot = 90;
        else if (facing == Direction.SOUTH) facingRot = 0;
        else if (facing == Direction.WEST) facingRot = -90;

        // Если бэкенд Flywheel отключён (например, при шейдерах без мода совместимости flywheel:off),
        // рендерим станок и все его части через BlockEntityRenderer.
        if (!isFlywheelActive(be.getLevel())) {
            renderMachineFallback(be, partialTick, poseStack, buffer, packedLight, packedOverlay, facingRot);
        }

        // Рендер рецептурного предмета
        renderRecipeItem(be, partialTick, poseStack, buffer, packedLight, packedOverlay, facingRot);
    }

    private void renderMachineFallback(StanokBlockEntity be, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffer, int packedLight, int packedOverlay,
                                       float facingRot) {
        poseStack.pushPose();

        // Базовая трансформация станка (startTransform из StanokVisual)
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRot));
        poseStack.translate(-0.5, 0, -0.5);
        poseStack.translate(2.0, 0, 2.0);

        BlockState state = be.getBlockState();

        // 1. Станина станка
        renderPartialModel(ModModels.STANOK_BASE, poseStack, buffer, state, packedLight, packedOverlay);

        // 2. Валы (West & East)
        float timeInSeconds = (be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : 0f) / 20.0f;
        float speed = be.getVisualSpeed();
        float shaftAngle = (timeInSeconds * speed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
        if (shaftAngle < 0) shaftAngle += (float) Math.PI * 2;

        PartialModel shaftModel = ModModels.SHAFT_MODELS.get("shaft_light_titanium");
        if (shaftModel != null) {
            // West shaft (dx = -3.0, dy = 0, dz = -1.0)
            poseStack.pushPose();
            poseStack.translate(-3.0f, 0f, -1.0f);
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(Axis.ZP.rotation(-shaftAngle));
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            renderPartialModel(shaftModel, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

            // East shaft (dx = -1.0, dy = 0, dz = -1.0)
            poseStack.pushPose();
            poseStack.translate(-1.0f, 0f, -1.0f);
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(Axis.ZP.rotation(-shaftAngle));
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            renderPartialModel(shaftModel, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();
        }

        // 3. Насадки и инструменты
        CarriageType carriage = be.getCurrentCarriageType();
        int prog = be.getData().get(0);
        int maxProg = be.getData().get(1);
        float animPhase = 0f;
        if (maxProg > 0 && prog > 0) {
            float interp = prog;
            if (be.getSpeed() != 0) interp += partialTick;
            animPhase = Math.max(0f, Math.min(1f, interp / maxProg));
        }

        if (carriage == CarriageType.PRESS) {
            renderPartialModel(ModModels.STANOK_PRESS_CARRIAGE, poseStack, buffer, state, packedLight, packedOverlay);

            float headOffsetY;
            if (animPhase < 0.5f) {
                headOffsetY = -(animPhase / 0.5f) * 0.22f;
            } else {
                headOffsetY = -((1.0f - animPhase) / 0.5f) * 0.22f;
            }

            poseStack.pushPose();
            poseStack.translate(0, headOffsetY, 0);
            renderPartialModel(ModModels.STANOK_PRESS_HEAD, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

        } else if (carriage == CarriageType.WIRE) {
            renderPartialModel(ModModels.STANOK_WIRE_CARRIAGE, poseStack, buffer, state, packedLight, packedOverlay);

            // Левый барабан
            poseStack.pushPose();
            poseStack.translate(-1.66570625f, 1.33125f, -1.034375f);
            poseStack.mulPose(Axis.ZP.rotation(shaftAngle));
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            renderPartialModel(ModModels.STANOK_WIRE_DRUM, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

            // Правый барабан
            poseStack.pushPose();
            poseStack.translate(-1.29851875f, 1.33125f, -1.034375f);
            poseStack.mulPose(Axis.ZP.rotation(-shaftAngle));
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            renderPartialModel(ModModels.STANOK_WIRE_DRUM, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

        } else if (carriage == CarriageType.FREZA) {
            StanokRecipe recipe = be.getCurrentRecipe();
            int recipeTime = recipe != null ? recipe.getProcessTicks() : 80;
            float recipeSeconds = recipeTime / 20.0f;
            float opElapsed = animPhase * recipeSeconds;

            float shiftX = 0f, shiftZ = 0f, shiftY = 0f;
            if (animPhase > 0) {
                if (opElapsed < 4.0f) {
                    float t = opElapsed;
                    if (t < 1.0f) {
                        shiftX = t * 0.5f;
                        shiftZ = 0f;
                        shiftY = 0f;
                    } else if (t < 2.0f) {
                        shiftX = 0.5f;
                        shiftZ = (t - 1.0f) * 0.22f;
                        shiftY = 0f;
                    } else if (t < 3.0f) {
                        shiftX = 0.5f - (t - 2.0f) * 0.5f;
                        shiftZ = 0.22f;
                        shiftY = 0f;
                    } else {
                        shiftX = 0f;
                        shiftZ = 0.22f - (t - 3.0f) * 0.22f;
                        shiftY = 0f;
                    }
                } else if (opElapsed < 8.0f) {
                    float t2 = opElapsed - 4.0f;
                    float[] pos2 = StanokVisual.computeTrdPath(t2);
                    shiftX = pos2[0];
                    shiftZ = pos2[1];
                    shiftY = pos2[2];
                } else {
                    float loopTime = opElapsed % 8.0f;
                    if (loopTime < 4.0f) {
                        float t = loopTime;
                        if (t < 1.0f) {
                            shiftX = t * 0.5f; shiftZ = 0f; shiftY = 0f;
                        } else if (t < 2.0f) {
                            shiftX = 0.5f; shiftZ = (t - 1.0f) * 0.22f; shiftY = 0f;
                        } else if (t < 3.0f) {
                            shiftX = 0.5f - (t - 2.0f) * 0.5f; shiftZ = 0.22f; shiftY = 0f;
                        } else {
                            shiftX = 0f; shiftZ = 0.22f - (t - 3.0f) * 0.22f; shiftY = 0f;
                        }
                    } else {
                        float[] pos2 = StanokVisual.computeTrdPath(loopTime - 4.0f);
                        shiftX = pos2[0]; shiftZ = pos2[1]; shiftY = pos2[2];
                    }
                }
            }
            shiftX = -shiftX;
            shiftZ = -shiftZ;
            float frezaSpin = shaftAngle * 5.0f;

            // Каретка фрезы
            poseStack.pushPose();
            poseStack.translate(shiftX, 0, 0);
            renderPartialModel(ModModels.STANOK_FREZA_CARRIAGE, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

            // Крепление фрезы
            poseStack.pushPose();
            poseStack.translate(shiftX, shiftY, shiftZ);
            renderPartialModel(ModModels.STANOK_FREZA_ATTACHMENT, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();

            // Фреза
            poseStack.pushPose();
            poseStack.translate(shiftX, shiftY, shiftZ);
            poseStack.translate(-1.23125f, 1.225f, -0.9296875f);
            poseStack.mulPose(Axis.YP.rotation(frezaSpin));
            renderPartialModel(ModModels.STANOK_FREZA, poseStack, buffer, state, packedLight, packedOverlay);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderPartialModel(@Nullable PartialModel partialModel, PoseStack poseStack,
                                     MultiBufferSource buffer, BlockState state,
                                     int packedLight, int packedOverlay) {
        if (partialModel == null) return;
        BakedModel model = partialModel.get();
        if (model == null) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                state,
                model,
                1.0F, 1.0F, 1.0F,
                packedLight,
                packedOverlay
        );
    }

    private void renderRecipeItem(StanokBlockEntity be, float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay,
                                  float facingRot) {
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

        poseStack.pushPose();

        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRot));
        poseStack.translate(-0.5, 0, -0.5);

        // Координаты из Blockbench: -23.2269, 18.2554, -15.4183
        float bbX = (-23.2269f - 1.0f) / 16f;
        float bbY = 18.2554f / 16f;
        float bbZ = (-15.4183f - 3.0f) / 16f;

        if (carriage == CarriageType.PRESS) {
            poseStack.pushPose();
            poseStack.translate(2.0f + bbX, bbY, 2.0f + bbZ);
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(0.5f, 0.5f, 0.5f);

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    toRender, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), 0
            );
            poseStack.popPose();

        } else if (carriage == CarriageType.FREZA) {
            poseStack.pushPose();
            poseStack.translate(2.0f + bbX, bbY, 2.0f + bbZ);

            if (phase < 0.5f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
                poseStack.scale(0.5f, 0.5f, 0.5f);
            } else {
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
