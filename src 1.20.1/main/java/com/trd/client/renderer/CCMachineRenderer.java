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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Рендер кубов жидкого металла в машине непрерывного литья.
 * Все координаты заданы в ПИКСЕЛЯХ относительно центра машины (контроллера),
 * и переводятся в блоки делением на 16. Текстура жидкого металла окрашивается
 * в цвет залитого металла — та же логика, что у котла (CastingPotRenderer).
 */
public class CCMachineRenderer implements BlockEntityRenderer<CCMachineBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE =
            new ResourceLocation("trd", "textures/machine/liquid_metal.png");

    // ==== Куб 1: холодильный желоб металла ====
    // размер (px): ширина 12, высота 0.8..4.8 (по полноте буфера), длина 44
    private static final float C1_CX = 16f;
    private static final float C1_CY = 20.5f;
    private static final float C1_CZ = 0f;
    private static final float C1_WIDTH = 12f;
    private static final float C1_HEIGHT_MIN = 0.8f;
    private static final float C1_HEIGHT_MAX = 4.8f;
    private static final float C1_DEPTH = 44f;

    // ==== Куб 2: выходной канал металла ====
    // размер (px): ширина 1..17 (по полноте буфера), высота 1, длина 30
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
        float c2BackPx = C2_CX + C2_WIDTH_MAX / 2f; // фиксированная задняя грань (при полном заполнении)
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

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float hx = w / 2f;
        float hy = h / 2f;
        float hz = d / 2f;

        // Верх (+Y): U вдоль X (0..w), V вдоль Z (0..d)
        builder.vertex(matrix, -hx, hy, -hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, -hx, hy, hz).color(r, g, b, a).uv(0, d).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, hx, hy, hz).color(r, g, b, a).uv(w, d).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, hx, hy, -hz).color(r, g, b, a).uv(w, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 1, 0).endVertex();

        // Низ (-Y)
        builder.vertex(matrix, hx, -hy, -hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, hx, -hy, hz).color(r, g, b, a).uv(0, d).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, -hx, -hy, hz).color(r, g, b, a).uv(w, d).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, -hx, -hy, -hz).color(r, g, b, a).uv(w, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, -1, 0).endVertex();

        // Стороны (+Z): U вдоль X (0..w), V вдоль Y (0..h)
        builder.vertex(matrix, -hx, -hy, hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, hx, -hy, hz).color(r, g, b, a).uv(w, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, hx, hy, hz).color(r, g, b, a).uv(w, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, -hx, hy, hz).color(r, g, b, a).uv(0, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, 1).endVertex();

        // Стороны (-Z): U вдоль X (0..w), V вдоль Y (0..h)
        builder.vertex(matrix, hx, -hy, -hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, -hx, -hy, -hz).color(r, g, b, a).uv(w, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, -hx, hy, -hz).color(r, g, b, a).uv(w, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, hx, hy, -hz).color(r, g, b, a).uv(0, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();

        // Стороны (-X): U вдоль Z (0..d), V вдоль Y (0..h)
        builder.vertex(matrix, -hx, -hy, -hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -hx, -hy, hz).color(r, g, b, a).uv(d, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -hx, hy, hz).color(r, g, b, a).uv(d, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -hx, hy, -hz).color(r, g, b, a).uv(0, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, -1, 0, 0).endVertex();

        // Стороны (+X): U вдоль Z (0..d), V вдоль Y (0..h)
        builder.vertex(matrix, hx, -hy, hz).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, hx, -hy, -hz).color(r, g, b, a).uv(d, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, hx, hy, -hz).color(r, g, b, a).uv(d, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, hx, hy, hz).color(r, g, b, a).uv(0, h).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 1, 0, 0).endVertex();

        poseStack.popPose();
    }
}
