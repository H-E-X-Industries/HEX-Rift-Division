package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CentrifugeCylinderRenderer implements BlockEntityRenderer<CentrifugeCylinderBlockEntity> {

    // Радиус цилиндра (9.55 пикселей / 2)
    private static final float RADIUS = 5f / 16.0f;
    
    // Y-координаты жидкостного буфера (от дна стакана до крышки)
    // Банка начинается чуть выше нуля. Оставил 0.5 пикселя запаса от дна.
    private static final float MIN_Y = 0.5f / 16.0f;
    private static final float MAX_Y = MIN_Y + (23.0f / 16.0f); // Высота 23 пикселя

    private static final int SIDES = 12; // 12 граней - отлично подходит под стиль майнкрафта

    public CentrifugeCylinderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CentrifugeCylinderBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        FluidStack fluidStack = blockEntity.getInputTank().getFluid();
        if (fluidStack.isEmpty()) return;

        float amount = fluidStack.getAmount();
        float capacity = blockEntity.getInputTank().getCapacity();
        if (amount <= 0 || capacity <= 0) return;

        float renderY = MIN_Y + (MAX_Y - MIN_Y) * (amount / capacity);

        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation texture = fluidExt.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        int color = fluidExt.getTintColor(fluidStack);

        VertexConsumer builder = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        // Центр блока по X и Z
        poseStack.translate(0.5f, 0, 0.5f);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        int a = (color >> 24 & 255);
        if (a == 0) a = 255;

        // Предрасчет координат вершин
        float[] xCoords = new float[SIDES + 1];
        float[] zCoords = new float[SIDES + 1];
        for (int i = 0; i <= SIDES; i++) {
            float angle = (float) (i * 2 * Math.PI / SIDES);
            xCoords[i] = (float) Math.cos(angle) * RADIUS;
            zCoords[i] = (float) Math.sin(angle) * RADIUS;
        }

        // РИСУЕМ БОКОВЫЕ ГРАНИ
        // Окружность цилиндра ~1.875 блоков. Мы обернем текстуру ровно 2 раза (по 6 граней на 1 оборот),
        // чтобы размер пикселя по горизонтали был практически 1:1.
        
        float currentY = MIN_Y;
        while (currentY < renderY) {
            float nextY = Math.min(currentY + 1.0f, renderY);
            float heightChunk = nextY - currentY; // Максимум 1.0

            // Тайлинг по вертикали (идеально 1:1, обрезаем текстуру если кусок меньше блока)
            float vTop = sprite.getV0();
            float vBottom = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * heightChunk;

            for (int i = 0; i < SIDES; i++) {
                float x1 = xCoords[i];
                float z1 = zCoords[i];
                float x2 = xCoords[i + 1];
                float z2 = zCoords[i + 1];

                // Оборачиваем текстуру 2 раза за 12 граней (каждые 6 граней = 1 полная текстура 0..1)
                float u1Fraction = (i % (SIDES / 2)) / (float) (SIDES / 2);
                float u2Fraction = ((i % (SIDES / 2)) + 1) / (float) (SIDES / 2);
                
                float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u1Fraction;
                float u2 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u2Fraction;

                float nx = (x1 + x2) / 2.0f;
                float nz = (z1 + z2) / 2.0f;
                float len = (float) Math.sqrt(nx * nx + nz * nz);
                nx /= len;
                nz /= len;

                builder.vertex(poseMatrix, x2, currentY, z2).color(r, g, b, a).uv(u2, vBottom).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, nx, 0, nz).endVertex();
                builder.vertex(poseMatrix, x1, currentY, z1).color(r, g, b, a).uv(u1, vBottom).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, nx, 0, nz).endVertex();
                builder.vertex(poseMatrix, x1, nextY, z1).color(r, g, b, a).uv(u1, vTop).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, nx, 0, nz).endVertex();
                builder.vertex(poseMatrix, x2, nextY, z2).color(r, g, b, a).uv(u2, vTop).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, nx, 0, nz).endVertex();
            }
            currentY = nextY;
        }

        // РИСУЕМ ВЕРХНЮЮ ГРАНЬ
        float uCenter = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * 0.5f;
        float vCenter = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * 0.5f;
        
        for (int i = 0; i < SIDES; i++) {
            float x1 = xCoords[i];
            float z1 = zCoords[i];
            float x2 = xCoords[i + 1];
            float z2 = zCoords[i + 1];

            float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (0.5f + x1 / (2 * RADIUS));
            float v1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (0.5f + z1 / (2 * RADIUS));
            float u2 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (0.5f + x2 / (2 * RADIUS));
            float v2 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (0.5f + z2 / (2 * RADIUS));

            // Инвертированный обход веера (чтобы смотрел вверх)
            builder.vertex(poseMatrix, 0, renderY, 0).color(r, g, b, a).uv(uCenter, vCenter).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, 1, 0).endVertex();
            builder.vertex(poseMatrix, x2, renderY, z2).color(r, g, b, a).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, 1, 0).endVertex();
            builder.vertex(poseMatrix, x1, renderY, z1).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, 1, 0).endVertex();
            builder.vertex(poseMatrix, x1, renderY, z1).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, 1, 0).endVertex();
        }

        // РИСУЕМ НИЖНЮЮ ГРАНЬ (если банку видно снизу)
        for (int i = 0; i < SIDES; i++) {
            float x1 = xCoords[i];
            float z1 = zCoords[i];
            float x2 = xCoords[i + 1];
            float z2 = zCoords[i + 1];

            float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (0.5f + x1 / (2 * RADIUS));
            float v1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (0.5f + z1 / (2 * RADIUS));
            float u2 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (0.5f + x2 / (2 * RADIUS));
            float v2 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (0.5f + z2 / (2 * RADIUS));

            // Инвертированный обход веера относительно верха = смотрит вниз
            builder.vertex(poseMatrix, 0, MIN_Y, 0).color(r, g, b, a).uv(uCenter, vCenter).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, -1, 0).endVertex();
            builder.vertex(poseMatrix, x1, MIN_Y, z1).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, -1, 0).endVertex();
            builder.vertex(poseMatrix, x2, MIN_Y, z2).color(r, g, b, a).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, -1, 0).endVertex();
            builder.vertex(poseMatrix, x2, MIN_Y, z2).color(r, g, b, a).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normalMatrix, 0, -1, 0).endVertex();
        }

        poseStack.popPose();
    }
}
