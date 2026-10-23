package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.lighting.FloodlightBlock;
import com.trd.block.entity.industrial.lighting.FloodlightBlockEntity;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 3D-рендер об'ємного поворотного прожектора (Floodlight).
 * Ієрархічний рендер шарнірів із 144 FPS partialTick інтерполяцією (Yaw + Pitch)
 * та Гауссовим об'ємним променем світла безпосередньо від лінзи.
 */
public class FloodlightBlockEntityRenderer implements BlockEntityRenderer<FloodlightBlockEntity> {

    // М'який теплий кремово-білий відтінок світла
    private static final int COLOR_R = 255;
    private static final int COLOR_G = 248;
    private static final int COLOR_B = 225;

    private static final int SIDES = 32;
    private static final int RINGS = 14;
    private static final int SHELLS = 6;

    private static final double[] COS_TABLE = new double[SIDES];
    private static final double[] SIN_TABLE = new double[SIDES];

    static {
        for (int i = 0; i < SIDES; i++) {
            double angle = (2.0 * Math.PI * i) / SIDES;
            COS_TABLE[i] = Math.cos(angle);
            SIN_TABLE[i] = Math.sin(angle);
        }
    }

    public FloodlightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FloodlightBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        Direction mountFace = state.hasProperty(FloodlightBlock.MOUNT_FACE)
                ? state.getValue(FloodlightBlock.MOUNT_FACE)
                : Direction.UP;

        // 1. Інтерполяція кутів обертання для плавності 144 FPS
        float renderYaw = Mth.rotLerp(partialTick, be.prevYaw, be.yaw);
        float renderPitch = Mth.lerp(partialTick, be.prevPitch, be.pitch);

        poseStack.pushPose();

        // 2. Орієнтація під поверхню кріплення MOUNT_FACE
        if (mountFace != Direction.UP) {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            switch (mountFace) {
                case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
                case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
                case SOUTH -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                }
                case WEST -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    poseStack.mulPose(Axis.YP.rotationDegrees(270));
                }
                case EAST -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    poseStack.mulPose(Axis.YP.rotationDegrees(90));
                }
                case UP -> {}
            }
            poseStack.translate(-0.5f, -0.5f, -0.5f);
        }

        // Крок А (Вилка / Yaw): перемістити у (0.5, 0.0, 0.5), повернути навколо осі Y, повернути назад
        poseStack.translate(0.5f, 0.0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - renderYaw));
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        FlywheelFallbackHelper.renderBakedModel(ModModels.FLOODLIGHT_YOKE.get(), poseStack, bufferSource, state, packedLight, packedOverlay);

        // Крок Б (Барабан / Pitch): перемістити у точку шарнірів вилки (0.5, 0.5625, 0.5), нахилити по осі X, повернути назад
        poseStack.translate(0.5f, 0.5625f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch));
        poseStack.translate(-0.5f, -0.5625f, -0.5f);

        boolean isLit = state.hasProperty(FloodlightBlock.LIT) && state.getValue(FloodlightBlock.LIT);
        BakedModel drumModel = isLit ? ModModels.FLOODLIGHT_DRUM_LIT.get() : ModModels.FLOODLIGHT_DRUM.get();
        FlywheelFallbackHelper.renderBakedModel(drumModel, poseStack, bufferSource, state, packedLight, packedOverlay);

        // Жива електрична дуга у фокусі барабана між вугільними електродами
        if (isLit) {
            renderElectricArc(poseStack, bufferSource, be);
        }

        // Крок В (Гауссів об'ємний промінь): рендериться безпосередньо в системі координат нахиленого барабана!
        int lightLevel = be.getEffectiveLightLevel();
        if (isLit && lightLevel > 0) {
            float fadeFactor = lightLevel / 15.0f;
            float beamDistance = be.getBeamDistance();
            if (fadeFactor > 0.01f && beamDistance > 0.5f) {
                renderGaussianBeamInDrumSpace(poseStack, bufferSource, beamDistance, fadeFactor);
            }
        }

        poseStack.popPose();
    }

    private void renderGaussianBeamInDrumSpace(PoseStack poseStack, MultiBufferSource bufferSource,
                                               float beamDistance, float fadeFactor) {
        // У локальній системі координат барабана:
        // Центр шарніра барабана: (0.5, 0.5625, 0.5)
        // Передня лінза барабана розташована на Z = 0.2375 (відстань ~0.2625 від центру)
        // Промінь поширюється прямо вперед вздовж осі -Z
        Vec3 start = new Vec3(0.5, 0.5625, 0.2375);
        double coneLength = Math.max(0.1, beamDistance - 0.2625);

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        float lengthRatio = (float) Math.min(1.0, coneLength / 49.7375);

        Vec3[][] ringVertices = new Vec3[RINGS + 1][SIDES];
        int[] ringAlphas = new int[RINGS + 1];

        for (int s = 0; s < SHELLS; s++) {
            float uNorm = (s + 1) / (float) SHELLS;
            float gaussianWeight = (float) Math.exp(-3.2f * uNorm * uNorm);
            float baseAlpha = 0.038f * gaussianWeight * fadeFactor;

            for (int k = 0; k <= RINGS; k++) {
                float t = k / (float) RINGS;
                float axialFade = (float) Math.pow(1.0f - t, 2.2f) * Math.min(1.0f, t * 8.0f + 0.35f);
                float alpha = baseAlpha * axialFade;
                ringAlphas[k] = Mth.clamp(Math.round(255.0f * alpha), 0, 255);

                float ringRadius = (0.28f + t * 2.32f * lengthRatio) * (0.4f + 0.6f * uNorm);
                double rz = start.z - t * coneLength;

                for (int i = 0; i < SIDES; i++) {
                    double rx = start.x + COS_TABLE[i] * ringRadius;
                    double ry = start.y + SIN_TABLE[i] * ringRadius;
                    ringVertices[k][i] = new Vec3(rx, ry, rz);
                }
            }

            for (int k = 0; k < RINGS; k++) {
                int a1 = ringAlphas[k];
                int a2 = ringAlphas[k + 1];

                if (a1 == 0 && a2 == 0) continue;

                Vec3[] r1 = ringVertices[k];
                Vec3[] r2 = ringVertices[k + 1];

                for (int i = 0; i < SIDES; i++) {
                    int next = (i + 1) % SIDES;

                    Vec3 s1 = r1[i];
                    Vec3 s2 = r1[next];
                    Vec3 e2 = r2[next];
                    Vec3 e1 = r2[i];

                    consumer.addVertex(mat, (float) s1.x, (float) s1.y, (float) s1.z).setColor(COLOR_R, COLOR_G, COLOR_B, a1);
                    consumer.addVertex(mat, (float) s2.x, (float) s2.y, (float) s2.z).setColor(COLOR_R, COLOR_G, COLOR_B, a1);
                    consumer.addVertex(mat, (float) e2.x, (float) e2.y, (float) e2.z).setColor(COLOR_R, COLOR_G, COLOR_B, a2);
                    consumer.addVertex(mat, (float) e1.x, (float) e1.y, (float) e1.z).setColor(COLOR_R, COLOR_G, COLOR_B, a2);
                }
            }
        }
    }

    /**
     * Жива електрична плазмова дуга у фокусі барабана між вугільними електродами.
     * Працює на високій частоті мерехтіння (High-Voltage Carbon Arc Flickering)
     * із багатошаровим аддитивним світінням RenderType.lightning():
     * - Сліпуче біле ядро (#FFFFFF)
     * - Електрично-блакитна плазмова оболонка (#88EEFF)
     * - Фіолетово-синє гало (#5577FF)
     * - Глибока синьо-фіолетова корона (#3344DD)
     */
    private void renderElectricArc(PoseStack poseStack, MultiBufferSource bufferSource, FloodlightBlockEntity be) {
        long time = System.currentTimeMillis();
        // Псевдовипадкове мерехтіння високої частоти (~25-30 Гц)
        long seed = be.getBlockPos().asLong() ^ (time / 35L);
        java.util.Random rand = new java.util.Random(seed);

        float flickerScale = 1.0f + (rand.nextFloat() - 0.5f) * 0.08f;
        float flickerAlpha = Mth.clamp(1.0f + (rand.nextFloat() - 0.5f) * 0.12f, 0.7f, 1.2f);

        // Мікро-дрейф фокальної точки дуги
        float jx = (rand.nextFloat() - 0.5f) * 0.002f;
        float jy = (rand.nextFloat() - 0.5f) * 0.002f;
        float jz = (rand.nextFloat() - 0.5f) * 0.002f;

        float cx = 0.50f + jx;
        float cy = 0.5625f + jy;
        float cz = 0.60f + jz;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f mat = poseStack.last().pose();

        // 1. Біле розжарене ядро (#FFFFFF)
        float s1 = 0.015f * flickerScale;
        int a1 = Mth.clamp(Math.round(255 * flickerAlpha), 0, 255);
        renderArcBox(consumer, mat, cx - s1, cy - s1 * 1.2f, cz - s1 * 1.4f, cx + s1, cy + s1 * 1.2f, cz + s1 * 1.4f, 255, 255, 255, a1);

        // 2. Електрично-блакитна плазмова оболонка (#88EEFF)
        float s2 = 0.030f * flickerScale;
        int a2 = Mth.clamp(Math.round(190 * flickerAlpha), 0, 255);
        renderArcBox(consumer, mat, cx - s2, cy - s2 * 1.2f, cz - s2 * 1.3f, cx + s2, cy + s2 * 1.2f, cz + s2 * 1.3f, 136, 238, 255, a2);

        // 3. Фіолетово-синє гало (#5577FF)
        float s3 = 0.052f * flickerScale;
        int a3 = Mth.clamp(Math.round(100 * flickerAlpha), 0, 255);
        renderArcBox(consumer, mat, cx - s3, cy - s3 * 1.15f, cz - s3 * 1.2f, cx + s3, cy + s3 * 1.15f, cz + s3 * 1.2f, 85, 119, 255, a3);

        // 4. Глибока синьо-фіолетова корона (#3344DD)
        float s4 = 0.078f * flickerScale;
        int a4 = Mth.clamp(Math.round(45 * flickerAlpha), 0, 255);
        renderArcBox(consumer, mat, cx - s4, cy - s4 * 1.1f, cz - s4 * 1.15f, cx + s4, cy + s4 * 1.1f, cz + s4 * 1.15f, 51, 68, 221, a4);
    }

    private void renderArcBox(VertexConsumer consumer, Matrix4f mat,
                              float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ,
                              int r, int g, int b, int a) {
        // North (Z-)
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);

        // South (Z+)
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);

        // East (X+)
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);

        // West (X-)
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);

        // Up (Y+)
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);

        // Down (Y-)
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(FloodlightBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(FloodlightBlockEntity be) {
        return be.getRenderBoundingBox();
    }
}
