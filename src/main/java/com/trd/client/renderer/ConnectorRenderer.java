package com.trd.client.renderer;

import com.trd.block.entity.industrial.energy.ConnectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class ConnectorRenderer implements BlockEntityRenderer<ConnectorBlockEntity> {

    private static final int SEGMENTS = 24; // увеличено для плавности
    private static final double SLACK = 1.002;

    // NEW: Цвет провода #141f2e
    private static final float R = 20f / 255f;
    private static final float G = 31f / 255f;
    private static final float B = 46f / 255f;
    private static final float A = 1.0f;

    public ConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ConnectorBlockEntity animatable, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (animatable.getConnections().isEmpty())
            return;

        Level level = animatable.getLevel();
        if (level == null)
            return;

        Vec3 startWorld = animatable.getWireAttachmentPoint();
        Vec3 renderOrigin = Vec3.atLowerCornerOf(animatable.getBlockPos());
        Vec3 start = startWorld.subtract(renderOrigin);

        poseStack.pushPose();

        for (BlockPos otherPos : animatable.getConnections()) {
            if (animatable.getBlockPos().compareTo(otherPos) > 0)
                continue;

            BlockEntity otherBe = level.getBlockEntity(otherPos);
            if (!(otherBe instanceof ConnectorBlockEntity otherConnector))
                continue;

            Vec3 endWorld = otherConnector.getWireAttachmentPoint();
            Vec3 end = endWorld.subtract(renderOrigin);

            // FIX: используем радиус из tier
            renderWire(poseStack, bufferSource, start, end, packedLight, packedOverlay,
                    animatable.getTier().wireRadius());
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ConnectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private void renderWire(PoseStack poseStack, MultiBufferSource bufferSource,
            Vec3 start, Vec3 end, int light, int overlay, float wireRadius) {

        com.trd.util.CatenaryHelper.CatenaryData catenary = com.trd.util.CatenaryHelper.compute(start, end);

        // 1. Получаем все точки кривой
        List<Vec3> points = new ArrayList<>(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            points.add(catenary.getPoint(t));
        }

        // 2. Вычисляем направления (касательные векторы) в каждой точке
        List<Vec3> tangents = new ArrayList<>(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            if (i == 0) {
                // первая точка: направление к следующей
                tangents.add(points.get(1).subtract(points.get(0)).normalize());
            } else if (i == SEGMENTS) {
                // последняя точка: направление от предыдущей
                tangents.add(points.get(SEGMENTS).subtract(points.get(SEGMENTS - 1)).normalize());
            } else {
                // внутренняя точка: центральная разность
                Vec3 prev = points.get(i - 1);
                Vec3 next = points.get(i + 1);
                tangents.add(next.subtract(prev).normalize());
            }
        }

        // 3. Вычисляем базисные векторы (side, upDir)
        List<Vec3> sideVectors = new ArrayList<>(SEGMENTS + 1);
        List<Vec3> upVectors = new ArrayList<>(SEGMENTS + 1);

        Vec3 tangent0 = tangents.get(0);
        Vec3 up0 = new Vec3(0, 1, 0);
        if (Math.abs(tangent0.y) > 0.99) {
            up0 = new Vec3(1, 0, 0);
        }
        Vec3 side0 = tangent0.cross(up0).normalize();
        Vec3 upDir0 = side0.cross(tangent0).normalize(); // Правильное векторное произведение для up
        sideVectors.add(side0);
        upVectors.add(upDir0);

        // Строим фреймы вдоль кривой
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 tPrev = tangents.get(i - 1);
            Vec3 tCurr = tangents.get(i);

            Vec3 rotAxis = tPrev.cross(tCurr);
            double angle = Math.acos(tPrev.dot(tCurr) / (tPrev.length() * tCurr.length()));

            if (rotAxis.lengthSqr() < 1e-6) {
                sideVectors.add(sideVectors.get(i - 1));
                upVectors.add(upVectors.get(i - 1));
            } else {
                rotAxis = rotAxis.normalize();
                Vec3 sidePrev = sideVectors.get(i - 1);
                Vec3 upPrev = upVectors.get(i - 1);

                Vec3 sideCurr = rotate(sidePrev, rotAxis, angle).normalize();
                Vec3 upCurr = rotate(upPrev, rotAxis, angle).normalize();

                sideVectors.add(sideCurr);
                upVectors.add(upCurr);
            }
        }

        // 4. Рендер полигонов
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(
                        new ResourceLocation("minecraft", "textures/block/white_concrete.png")));

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);

            Vec3 s1 = sideVectors.get(i).scale(wireRadius);
            Vec3 u1 = upVectors.get(i).scale(wireRadius);
            Vec3 s2 = sideVectors.get(i + 1).scale(wireRadius);
            Vec3 u2 = upVectors.get(i + 1).scale(wireRadius);

            // Четыре грани: +side, -side, +up, -up
            // Рендерим 3D сегмент (труба из 4 граней)
            emitBoxSegment(matrix, normalMatrix, consumer, p1, p2, s1, u1, s2, u2, light, overlay);
        }
    }

    // Вращение вектора вокруг оси
    private Vec3 rotate(Vec3 vec, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = vec.dot(axis);
        Vec3 cross = axis.cross(vec);
        return vec.scale(cos)
                .add(axis.scale(dot * (1 - cos)))
                .add(cross.scale(sin));
    }

    // Генерация 3D сегмента провода (кубик между p1 и p2)
    private void emitBoxSegment(Matrix4f mat, Matrix3f norm, VertexConsumer consumer,
            Vec3 p1, Vec3 p2, Vec3 s1, Vec3 u1, Vec3 s2, Vec3 u2,
            int light, int overlay) {
        
        // Вершины в начале (p1)
        Vec3 tr1 = p1.add(s1).add(u1);
        Vec3 tl1 = p1.subtract(s1).add(u1);
        Vec3 bl1 = p1.subtract(s1).subtract(u1);
        Vec3 br1 = p1.add(s1).subtract(u1);

        // Вершины в конце (p2)
        Vec3 tr2 = p2.add(s2).add(u2);
        Vec3 tl2 = p2.subtract(s2).add(u2);
        Vec3 bl2 = p2.subtract(s2).subtract(u2);
        Vec3 br2 = p2.add(s2).subtract(u2);

        // Верхняя грань (+u)
        Vec3 nTop = u1.normalize();
        emitFace(mat, norm, consumer, tl1, tr1, tr2, tl2, nTop, light, overlay);

        // Нижняя грань (-u)
        Vec3 nBot = u1.scale(-1).normalize();
        emitFace(mat, norm, consumer, br1, bl1, bl2, br2, nBot, light, overlay);

        // Правая грань (+s)
        Vec3 nRight = s1.normalize();
        emitFace(mat, norm, consumer, tr1, br1, br2, tr2, nRight, light, overlay);

        // Левая грань (-s)
        Vec3 nLeft = s1.scale(-1).normalize();
        emitFace(mat, norm, consumer, bl1, tl1, tl2, bl2, nLeft, light, overlay);
    }

    private void emitFace(Matrix4f mat, Matrix3f norm, VertexConsumer consumer,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, int light, int overlay) {
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;
        vert(mat, norm, consumer, a, nx, ny, nz, 0, 0, light, overlay);
        vert(mat, norm, consumer, b, nx, ny, nz, 0, 1, light, overlay);
        vert(mat, norm, consumer, c, nx, ny, nz, 1, 1, light, overlay);
        vert(mat, norm, consumer, d, nx, ny, nz, 1, 0, light, overlay);
    }

    private void vert(Matrix4f mat, Matrix3f norm, VertexConsumer consumer,
            Vec3 pos, float nx, float ny, float nz,
            float u, float v, int light, int overlay) {
        consumer.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(R, G, B, A)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(norm, nx, ny, nz)
                .endVertex();
    }
}