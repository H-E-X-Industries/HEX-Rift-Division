package com.trd.client.renderer;

import com.trd.api.metallurgy.system.Metal;
import com.trd.multiblock.industrial.ccmachine.CCMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Рендер кубов жидкого металла в машине непрерывного литья.
 * Все координаты заданы в ПИКСЕЛЯХ относительно центра машины (контроллера),
 * и переводятся в блоки делением на 16. Текстура жидкого металла окрашивается
 * в цвет залитого металла — та же логика, что у котла (CastingPotRenderer).
 */
public class CCMachineRenderer implements BlockEntityRenderer<CCMachineBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/machine/liquid_metal.png");

    // ==== Куб 1: холодильный желоб металла ====
    private static final float C1_CX = 16f;
    private static final float C1_CY = 20.5f;
    private static final float C1_CZ = 0f;
    private static final float C1_WIDTH = 12f;
    private static final float C1_HEIGHT_MIN = 0.8f;
    private static final float C1_HEIGHT_MAX = 4.8f;
    private static final float C1_DEPTH = 44f;

    // ==== Куб 2: выходной канал металла ====
    private static final float C2_CX = -13.5f;
    private static final float C2_CY = 3.5f;
    private static final float C2_CZ = 0f;
    private static final float C2_WIDTH_MIN = 1f;
    private static final float C2_WIDTH_MAX = 17f;
    private static final float C2_HEIGHT = 1f;
    private static final float C2_DEPTH = 30f;

    public CCMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CCMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getStoredUnits() <= 0) return;
        Metal metal = be.getStoredMetal();
        if (metal == null) return;

        int color = metal.getColor();
        float fill = be.getFillLevel(); // 0..1

        poseStack.pushPose();

        // Центр машины = центр контроллерного блока
        poseStack.translate(0.5, 0.5, 0.5);

        // Крутим вместе с машиной, как в рендере котла/спуска
        if (be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        }

        // Поворот на 90° влево (если смотреть на фронт машины) вокруг центра машины
        poseStack.mulPose(Axis.YP.rotationDegrees(-270f));

        // Куб 1: высота зависит от полноты буфера
        float c1H = (C1_HEIGHT_MIN + (C1_HEIGHT_MAX - C1_HEIGHT_MIN) * fill) / 16f;
        renderBox(poseStack, buffer, color,
                C1_CX / 16f, C1_CY / 16f, C1_CZ / 16f,
                C1_WIDTH / 16f, c1H, C1_DEPTH / 16f);

        // Куб 2: ширина зависит от полноты буфера. Задняя (+X) грань закреплена у тыла машины,
        // а расширение идёт ОТ тыла К фронту (-X), т.е. не из центра в обе стороны.
        float c2wPx = C2_WIDTH_MIN + (C2_WIDTH_MAX - C2_WIDTH_MIN) * fill;
        float c2BackPx = C2_CX + C2_WIDTH_MAX / 2f;
        float c2Cx = (c2BackPx - c2wPx / 2f) / 16f;
        renderBox(poseStack, buffer, color,
                c2Cx, C2_CY / 16f, C2_CZ / 16f,
                c2wPx / 16f, C2_HEIGHT / 16f, C2_DEPTH / 16f);

        poseStack.popPose();
    }

    /**
     * Рисует куб (w,h,d) в блоках с центром в (cx,cy,cz).
     * Текстура жидкого металла накладывается СЕГМЕНТАМИ (тайлится, по одному повтору на блок),
     * а не растягивается на весь куб — так выглядит натуральнее.
     */
    private void renderBox(PoseStack poseStack, MultiBufferSource buffer, int color,
                           float cx, float cy, float cz, float w, float h, float d) {
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);

        VertexConsumer builder = buffer.getBuffer(RenderType.entitySolid(LIQUID_METAL_TEXTURE));

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f;

        PoseStack.Pose pose = poseStack.last();

        float hx = w / 2f;
        float hy = h / 2f;
        float hz = d / 2f;
        int light = 15728880;

        // Верх (+Y): U вдоль X (0..w), V вдоль Z (0..d)
        quad(builder, pose,
                -hx, hy, -hz, 0, 0,
                -hx, hy, hz, 0, d,
                hx, hy, hz, w, d,
                hx, hy, -hz, w, 0,
                r, g, b, a, 0, 1, 0, light);

        // Низ (-Y)
        quad(builder, pose,
                hx, -hy, -hz, 0, 0,
                hx, -hy, hz, 0, d,
                -hx, -hy, hz, w, d,
                -hx, -hy, -hz, w, 0,
                r, g, b, a, 0, -1, 0, light);

        // Стороны (+Z): U вдоль X (0..w), V вдоль Y (0..h)
        quad(builder, pose,
                -hx, -hy, hz, 0, 0,
                hx, -hy, hz, w, 0,
                hx, hy, hz, w, h,
                -hx, hy, hz, 0, h,
                r, g, b, a, 0, 0, 1, light);

        // Стороны (-Z): U вдоль X (0..w), V вдоль Y (0..h)
        quad(builder, pose,
                hx, -hy, -hz, 0, 0,
                -hx, -hy, -hz, w, 0,
                -hx, hy, -hz, w, h,
                hx, hy, -hz, 0, h,
                r, g, b, a, 0, 0, -1, light);

        // Стороны (-X)
        quad(builder, pose,
                -hx, -hy, -hz, 0, 0,
                -hx, -hy, hz, 0, h,
                -hx, hy, hz, w, h,
                -hx, hy, -hz, w, 0,
                r, g, b, a, -1, 0, 0, light);

        // Стороны (+X)
        quad(builder, pose,
                hx, -hy, hz, 0, 0,
                hx, -hy, -hz, 0, h,
                hx, hy, -hz, w, h,
                hx, hy, hz, w, 0,
                r, g, b, a, 1, 0, 0, light);

        poseStack.popPose();
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
