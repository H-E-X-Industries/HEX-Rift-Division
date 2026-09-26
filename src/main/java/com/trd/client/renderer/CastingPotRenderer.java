package com.trd.client.renderer;

import com.trd.api.metallurgy.system.Metal;
import com.trd.block.basic.industrial.casting.CastingPotBlock;
import com.trd.block.entity.industrial.casting.CastingPotBlockEntity;
import com.trd.event.HotItemHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CastingPotRenderer implements BlockEntityRenderer<CastingPotBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE = ResourceLocation.fromNamespaceAndPath("trd", "textures/machine/liquid_metal.png");
    private static final ResourceLocation HOT_GLOW_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private final ItemRenderer itemRenderer;

    public CastingPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(CastingPotBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack mold = blockEntity.getMold();
        ItemStack output = blockEntity.getOutputItem();

        Direction facing = blockEntity.getBlockState().getValue(CastingPotBlock.FACING);

        // === ПРОВЕРКА НА EAST-WEST ДЛЯ ИНВЕРСИИ ===
        boolean needsInverse = (facing == Direction.EAST || facing == Direction.WEST);

        // 1. ФОРМА
        if (!mold.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.25f, 0.5f);

            float rotationY = getRotationFromFacing(facing);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));

            if (needsInverse) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            }

            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);

            itemRenderer.renderStatic(mold, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }

        // 2. ЖИДКИЙ МЕТАЛЛ (рендерим только если нет готового предмета)
        if (blockEntity.getStoredUnits() > 0 && output.isEmpty()) {
            float fillLevel = blockEntity.getFillLevel();
            float heightPixels = 0.1f + 1.9f * fillLevel;
            float yCenter = (4.35f + heightPixels / 2.0f) / 16.0f;
            int color = blockEntity.getCurrentMetal() != null ? blockEntity.getCurrentMetal().getColor() : 0xFFFFFF;

            poseStack.pushPose();
            poseStack.translate(0.5f, yCenter, 0.5f);
            poseStack.scale(0.75f, heightPixels / 16.0f, 0.75f);
            renderLiquidCube(poseStack, buffer, 15728880, color);
            poseStack.popPose();
        }

        // 3. ГОТОВЫЙ ПРЕДМЕТ (или БЛОК)
        if (!output.isEmpty()) {
            if (blockEntity.isBlockMold()) {
                renderHotBlock(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse);
            } else {
                renderOutputItem(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse, output);
            }
        }

        // 4. ШЛАК
        if (blockEntity.hasSlag()) {
            renderSlag(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse);
        }
    }

    private void renderHotBlock(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                                MultiBufferSource buffer, int packedLight, int packedOverlay,
                                Direction facing, boolean needsInverse) {

        Metal metal = blockEntity.getBlockRenderMetal();
        if (metal == null || !metal.hasBlock()) return;

        Block block = metal.getBlock();
        BlockState blockState = block.defaultBlockState();

        ItemStack output = blockEntity.getOutputItem();
        boolean isHot = HotItemHandler.isHot(output);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(output) : 0f;

        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.0f, 0.5f);

        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));
        if (needsInverse) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        }

        poseStack.translate(0.0f, 0.25f, 0.0f);

        float scaleXZ = 9.0f / 16.0f;
        float scaleY = 4.0f / 16.0f;
        poseStack.scale(scaleXZ, scaleY, scaleXZ);

        poseStack.translate(-0.5f, 0.0f, -0.5f);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState, poseStack, buffer, renderLight, packedOverlay
        );

        poseStack.popPose();
    }

    private void renderOutputItem(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay,
                                  Direction facing, boolean needsInverse, ItemStack output) {

        poseStack.pushPose();
        poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));

        if (needsInverse) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        float scale = 0.75f;
        poseStack.scale(scale, scale, scale);

        boolean isHot = HotItemHandler.isHot(output);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(output) : 0f;
        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        itemRenderer.renderStatic(output, ItemDisplayContext.FIXED, renderLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);

        if (isHot && heatRatio > 0.05f) {
            renderHotGlowEffect(poseStack, buffer, heatRatio);
            renderTemperatureTint(poseStack, buffer, heatRatio);
        }

        poseStack.popPose();
    }

    private void renderSlag(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                            MultiBufferSource buffer, int packedLight, int packedOverlay,
                            Direction facing, boolean needsInverse) {

        ItemStack slagStack = blockEntity.getSlagStackForRender();
        if (slagStack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));

        if (needsInverse) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(90));

        float scale = 0.75f;
        poseStack.scale(scale, scale, scale);

        boolean isHot = HotItemHandler.isHot(slagStack);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(slagStack) : 0f;

        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        itemRenderer.renderStatic(slagStack, ItemDisplayContext.FIXED,
                renderLight, packedOverlay, poseStack, buffer,
                blockEntity.getLevel(), 0);

        if (isHot && heatRatio > 0.05f) {
            renderHotGlowEffect(poseStack, buffer, heatRatio);
        }

        poseStack.popPose();
    }

    private void renderHotGlowEffect(PoseStack poseStack, MultiBufferSource buffer, float heatRatio) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(HOT_GLOW_TEXTURE));

        float alpha = 0.6f * heatRatio;
        if (alpha < 0.05f) return;

        float r = 1.0f;
        float g = 0.4f + (0.4f * heatRatio);
        float b = 0.1f * heatRatio;

        PoseStack.Pose pose = poseStack.last();
        float s = 0.4f;
        float zOffset = 0.001f;

        addVertex(builder, pose, -s, -s, zOffset, r, g, b, alpha, 0, 0);
        addVertex(builder, pose, -s,  s, zOffset, r, g, b, alpha, 0, 1);
        addVertex(builder, pose,  s,  s, zOffset, r, g, b, alpha, 1, 1);
        addVertex(builder, pose,  s, -s, zOffset, r, g, b, alpha, 1, 0);
    }

    private void renderTemperatureTint(PoseStack poseStack, MultiBufferSource buffer, float heatRatio) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(HOT_GLOW_TEXTURE));

        float alpha = 0.25f * heatRatio;
        if (alpha < 0.05f) return;

        float r = 1.0f;
        float g = 0.6f;
        float b = 0.2f;

        PoseStack.Pose pose = poseStack.last();
        float s = 0.38f;
        float zOffset = 0.002f;

        addVertex(builder, pose, -s, -s, zOffset, r, g, b, alpha, 0, 0);
        addVertex(builder, pose, -s,  s, zOffset, r, g, b, alpha, 0, 1);
        addVertex(builder, pose,  s,  s, zOffset, r, g, b, alpha, 1, 1);
        addVertex(builder, pose,  s, -s, zOffset, r, g, b, alpha, 1, 0);
    }

    private float getRotationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    private void addVertex(VertexConsumer builder, PoseStack.Pose pose,
                           float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        builder.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0, 0, 1);
    }

    private void renderLiquidCube(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entitySolid(LIQUID_METAL_TEXTURE));

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f;

        PoseStack.Pose pose = poseStack.last();
        float half = 0.5f;

        // Верх (+Y)
        quad(builder, pose,
                -half, half, -half, 0, 0,
                -half, half, half, 0, 1,
                half, half, half, 1, 1,
                half, half, -half, 1, 0,
                r, g, b, a, 0, 1, 0, packedLight);

        // Низ (-Y)
        quad(builder, pose,
                half, -half, -half, 0, 0,
                half, -half, half, 0, 1,
                -half, -half, half, 1, 1,
                -half, -half, -half, 1, 0,
                r, g, b, a, 0, -1, 0, packedLight);

        // Стороны (+Z)
        quad(builder, pose,
                -half, -half, half, 0, 0,
                half, -half, half, 1, 0,
                half, half, half, 1, 1,
                -half, half, half, 0, 1,
                r, g, b, a, 0, 0, 1, packedLight);

        // Стороны (-Z)
        quad(builder, pose,
                half, -half, -half, 0, 0,
                -half, -half, -half, 1, 0,
                -half, half, -half, 1, 1,
                half, half, -half, 0, 1,
                r, g, b, a, 0, 0, -1, packedLight);

        // Стороны (-X)
        quad(builder, pose,
                -half, -half, -half, 0, 0,
                -half, -half, half, 1, 0,
                -half, half, half, 1, 1,
                -half, half, -half, 0, 1,
                r, g, b, a, -1, 0, 0, packedLight);

        // Стороны (+X)
        quad(builder, pose,
                half, -half, half, 0, 0,
                half, -half, -half, 1, 0,
                half, half, -half, 1, 1,
                half, half, half, 0, 1,
                r, g, b, a, 1, 0, 0, packedLight);
    }

    private void quad(VertexConsumer builder, PoseStack.Pose pose,
                      float x1, float y1, float z1, float u1, float v1,
                      float x2, float y2, float z2, float u2, float v2,
                      float x3, float y3, float z3, float u3, float v3,
                      float x4, float y4, float z4, float u4, float v4,
                      float r, float g, float b, float a,
                      float nx, float ny, float nz, int light) {
        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(u4, v4).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
