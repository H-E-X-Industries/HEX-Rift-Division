package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VishelachivatelRenderer implements BlockEntityRenderer<VishelashivatelBlockEntity> {

    // Предварительно обрезанные полигоны для идеальной мозаики (тайлинга) 1x1 блок.
    private static final List<List<float[]>> TILED_POLYGONS = new ArrayList<>();
    private static final List<int[]> CELL_COORDS = new ArrayList<>();

    static {
        float[][] basePoly = {
                {0.351683125f, 1.313701923f},
                {0.960816875f, 0.962018798f},
                {1.3125f, 0.352885048f},
                {1.3125f, -0.350481202f},
                {0.960816875f, -0.959614952f},
                {0.351683125f, -1.311298077f},
                {-0.351683125f, -1.311298077f},
                {-0.960816875f, -0.959614952f},
                {-1.3125f, -0.350481202f},
                {-1.3125f, 0.352885048f},
                {-0.960816875f, 0.962018798f},
                {-0.351683125f, 1.313701923f}
        };

        // Разрезаем 12-гранник на сетку 3x3 блоков (от -1 до 1)
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                float minX = cx - 0.5f;
                float maxX = cx + 0.5f;
                float minZ = cz - 0.5f;
                float maxZ = cz + 0.5f;

                List<float[]> poly = new ArrayList<>(Arrays.asList(basePoly));
                poly = clipEdge(poly, true, 1, minX);
                poly = clipEdge(poly, true, -1, -maxX);
                poly = clipEdge(poly, false, 1, minZ);
                poly = clipEdge(poly, false, -1, -maxZ);

                if (poly.size() >= 3) {
                    TILED_POLYGONS.add(poly);
                    CELL_COORDS.add(new int[]{cx, cz});
                }
            }
        }
    }

    private static List<float[]> clipEdge(List<float[]> poly, boolean isX, float sign, float value) {
        if (poly.isEmpty()) return poly;
        List<float[]> out = new ArrayList<>();
        float[] prev = poly.get(poly.size() - 1);

        for (float[] curr : poly) {
            float prevVal = isX ? prev[0] : prev[1];
            float currVal = isX ? curr[0] : curr[1];

            boolean prevInside = (prevVal * sign) >= value;
            boolean currInside = (currVal * sign) >= value;

            if (currInside != prevInside) {
                float t = (value * sign - prevVal) / (currVal - prevVal);
                float ix = prev[0] + t * (curr[0] - prev[0]);
                float iz = prev[1] + t * (curr[1] - prev[1]);
                out.add(new float[]{ix, iz});
            }
            if (currInside) {
                out.add(curr);
            }
            prev = curr;
        }
        return out;
    }

    public VishelachivatelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VishelashivatelBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        FluidStack fluidStack = blockEntity.getFluidTank().getFluid();

        if (fluidStack.isEmpty()) return;

        float amount = fluidStack.getAmount();
        float capacity = blockEntity.getFluidTank().getCapacity();

        float minY = 0.05f;
        float maxY = 0.85f;
        float renderY = minY + (maxY - minY) * (amount / capacity);

        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation texture = fluidExt.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        int color = fluidExt.getTintColor(fluidStack);

        VertexConsumer builder = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);

        PoseStack.Pose pose = poseStack.last();

        // Рисуем обрезанные полигоны. Теперь порядок вершин правильный (смотрит вверх).
        for (int p = 0; p < TILED_POLYGONS.size(); p++) {
            List<float[]> poly = TILED_POLYGONS.get(p);
            int cx = CELL_COORDS.get(p)[0];
            int cz = CELL_COORDS.get(p)[1];

            // Триангуляция полигона (N-угольник превращаем в треугольники веером от первой вершины)
            for (int i = 1; i < poly.size() - 1; i++) {
                // Порядок V[0] -> V[i] -> V[i+1] формирует нормаль вверх (изнанки не будет)
                putVertex(builder, pose, poly.get(0)[0], renderY, poly.get(0)[1], color, packedLight, sprite, cx, cz);
                putVertex(builder, pose, poly.get(i)[0], renderY, poly.get(i)[1], color, packedLight, sprite, cx, cz);
                putVertex(builder, pose, poly.get(i + 1)[0], renderY, poly.get(i + 1)[1], color, packedLight, sprite, cx, cz);
                putVertex(builder, pose, poly.get(i + 1)[0], renderY, poly.get(i + 1)[1], color, packedLight, sprite, cx, cz); // 4-я точка для Quad'а
            }
        }

        poseStack.popPose();
    }

    private void putVertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, int color, int light, TextureAtlasSprite sprite, int cx, int cz) {
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        // Идеальный бесшовный тайлинг текстуры внутри КАЖДОГО отдельного 1x1 блока
        float u_fraction = x - cx + 0.5f;
        float v_fraction = z - cz + 0.5f;

        float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u_fraction;
        float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v_fraction;

        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        int a = (color >> 24 & 255);
        if (a == 0) a = 255;

        builder.vertex(poseMatrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normalMatrix, 0, 1, 0) // Точно задаем нормаль вверх
                .endVertex();
    }
}
