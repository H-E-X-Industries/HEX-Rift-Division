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
import org.joml.Matrix4f;

/**
 * 3D Block Entity Renderer for industrial swiveling searchlights (Floodlight).
 * Performs high-framerate (144+ FPS) partialTick interpolation for gimbal orientation (Yaw + Pitch),
 * renders Flywheel/baked partial models (yoke and drum), and projects volumetric light cone shells.
 */
public class FloodlightBlockEntityRenderer implements BlockEntityRenderer<FloodlightBlockEntity> {

    // Warm high-intensity searchlight beam tint
    private static final int COLOR_R = 255;
    private static final int COLOR_G = 248;
    private static final int COLOR_B = 225;

    private static final int SIDES = 32;
    private static final int RINGS = 4;
    private static final int SHELLS = 8;

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

        // 1. Interpolate rotation angles smoothly between physics ticks
        float renderYaw = Mth.rotLerp(partialTick, be.prevYaw, be.yaw);
        float renderPitch = Mth.lerp(partialTick, be.prevPitch, be.pitch);

        poseStack.pushPose();

        // 2. Transform to align with block MOUNT_FACE orientation
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

        // Yoke rotation (Yaw): Pivot around block center vertical axis (0.5, 0.0, 0.5)
        poseStack.translate(0.5f, 0.0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - renderYaw));
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        FlywheelFallbackHelper.renderBakedModel(ModModels.FLOODLIGHT_YOKE.get(), poseStack, bufferSource, state, packedLight, packedOverlay);

        // Drum elevation (Pitch): Pivot around trunnion bearings (0.5, 0.5625, 0.5)
        poseStack.translate(0.5f, 0.5625f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch));
        poseStack.translate(-0.5f, -0.5625f, -0.5f);

        boolean isLit = state.hasProperty(FloodlightBlock.LIT) && state.getValue(FloodlightBlock.LIT);
        BakedModel drumModel = isLit ? ModModels.FLOODLIGHT_DRUM_LIT.get() : ModModels.FLOODLIGHT_DRUM.get();
        FlywheelFallbackHelper.renderBakedModel(drumModel, poseStack, bufferSource, state, packedLight, packedOverlay);

        // Render incandescent electric arc between carbon electrodes
        if (isLit) {
            renderElectricArc(poseStack, bufferSource, be);
        }

        // Volumetric beam projection (RenderType.lightning): unshaded additive blending
        float currentDistance = be.getBeamDistance();
        if (isLit && currentDistance > 0.1f) {
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

            float intensityFactor = be.getEffectiveLightLevel() / 15.0f;
            renderVolumetricBeam(poseStack, consumer, currentDistance, intensityFactor);
        }

        poseStack.popPose();
    }

    /**
     * Renders a live electric arc between the carbon rods at the optical focal center of the drum reflector.
     */
    private void renderElectricArc(PoseStack poseStack, MultiBufferSource bufferSource, FloodlightBlockEntity be) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        float arcX = 0.5f;
        float arcY = 0.5625f;
        float arcZ = 0.60f;

        long time = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float flicker = (float) Math.sin(time * 1.5) * 0.003f;

        // 1. Ultra-bright incandescent core (#FFFFFF)
        renderArcCube(poseStack, consumer,
                arcX - 0.012f + flicker, arcY - 0.012f, arcZ - 0.012f,
                arcX + 0.012f + flicker, arcY + 0.012f, arcZ + 0.012f,
                255, 255, 255, 255);

        // 2. High-temperature golden plasma zone (#FFEA70)
        renderArcCube(poseStack, consumer,
                arcX - 0.025f + flicker, arcY - 0.025f, arcZ - 0.025f,
                arcX + 0.025f + flicker, arcY + 0.025f, arcZ + 0.025f,
                255, 234, 112, 180);

        // 3. Diffuse corona zone (#FFAA20)
        renderArcCube(poseStack, consumer,
                arcX - 0.045f, arcY - 0.045f, arcZ - 0.045f,
                arcX + 0.045f, arcY + 0.045f, arcZ + 0.045f,
                255, 170, 32, 90);
    }

    private void renderArcCube(PoseStack poseStack, VertexConsumer consumer,
                               float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ,
                               int r, int g, int b, int a) {
        Matrix4f mat = poseStack.last().pose();

        // North
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);

        // South
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);

        // East
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);

        // West
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);

        // Up
        consumer.addVertex(mat, minX, maxY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, maxY, minZ).setColor(r, g, b, a);

        // Down
        consumer.addVertex(mat, minX, minY, maxZ).setColor(r, g, b, a);
        consumer.addVertex(mat, minX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, minZ).setColor(r, g, b, a);
        consumer.addVertex(mat, maxX, minY, maxZ).setColor(r, g, b, a);
    }

    /**
     * Renders a soft, wide atmospheric volumetric light cone projecting forward from the drum's front lens (along -Z).
     * Uses 8 closely spaced nested shells with gentle start opacity (combined 0.12f–0.16f at the lens)
     * and quadratic longitudinal falloff ((1.0 - t)^2.2) to simulate diffused atmospheric haze without solid laser banding.
     */
    private void renderVolumetricBeam(PoseStack poseStack, VertexConsumer consumer, float distance, float intensity) {
        Matrix4f mat = poseStack.last().pose();

        final float originX = 0.5f;
        final float originY = 0.5625f;
        final float originZ = 0.22f; // Front lens face location in local drum space

        // Front lens aperture radius (~0.28f, fitting snugly into the drum housing)
        final float startRadius = 0.28f;
        // Wide atmospheric haze footprint at full 45-block range (diameter ~7.0 blocks)
        final float fullEndRadius = 3.50f;
        final float maxRange = (float) FloodlightBlockEntity.MAX_DISTANCE;

        // Proportional expansion scaling when beam is truncated by obstacles
        float lengthFraction = Math.min(1.0f, distance / maxRange);
        float effectiveEndRadius = startRadius + (fullEndRadius - startRadius) * lengthFraction;

        // Smooth quadratic radial distribution across 8 nested shells
        float[] shellFactors = { 0.35f, 0.45f, 0.55f, 0.65f, 0.75f, 0.84f, 0.92f, 1.00f };
        // Soft per-shell base alphas (sum = 35 / 255 ≈ 0.137 total opacity at lens, individual 0.012f–0.020f)
        int[] baseAlphas =     { 5,     5,     5,     5,     4,     4,     4,     3     };

        for (int shell = 0; shell < SHELLS; shell++) {
            float shellRatio = shellFactors[shell];
            int maxAlpha = Math.round(baseAlphas[shell] * intensity);
            if (maxAlpha <= 0) continue;

            // Start all shells snugly at the front lens aperture (0.244f..0.280f)
            float r0 = startRadius * (0.80f + 0.20f * shellRatio);
            float rEnd = effectiveEndRadius * shellRatio;

            for (int ring = 0; ring < RINGS; ring++) {
                float t0 = (float) ring / RINGS;
                float t1 = (float) (ring + 1) / RINGS;

                // Project beam forward through the front lens (along -Z in local coordinates)
                float z0 = originZ - t0 * distance;
                float z1 = originZ - t1 * distance;

                float ringRadius0 = r0 + (rEnd - r0) * t0;
                float ringRadius1 = r0 + (rEnd - r0) * t1;

                int a0 = calculateRingAlpha(t0, maxAlpha);
                int a1 = calculateRingAlpha(t1, maxAlpha);

                for (int side = 0; side < SIDES; side++) {
                    int nextSide = (side + 1) % SIDES;

                    float cosA = (float) COS_TABLE[side];
                    float sinA = (float) SIN_TABLE[side];
                    float cosB = (float) COS_TABLE[nextSide];
                    float sinB = (float) SIN_TABLE[nextSide];

                    float x0A = originX + ringRadius0 * cosA;
                    float y0A = originY + ringRadius0 * sinA;

                    float x0B = originX + ringRadius0 * cosB;
                    float y0B = originY + ringRadius0 * sinB;

                    float x1B = originX + ringRadius1 * cosB;
                    float y1B = originY + ringRadius1 * sinB;

                    float x1A = originX + ringRadius1 * cosA;
                    float y1A = originY + ringRadius1 * sinA;

                    // Quad: 0A -> 0B -> 1B -> 1A
                    consumer.addVertex(mat, x0A, y0A, z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0);
                    consumer.addVertex(mat, x0B, y0B, z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0);
                    consumer.addVertex(mat, x1B, y1B, z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1);
                    consumer.addVertex(mat, x1A, y1A, z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1);
                }
            }
        }
    }

    /**
     * Calculates longitudinal quadratic distance falloff ((1.0 - t)^2.2) along the beam,
     * ensuring gentle visibility near the lens and soft dissolution to 0.0 at the far end.
     */
    private int calculateRingAlpha(float t, int maxAlpha) {
        if (t >= 0.999f || maxAlpha <= 0) {
            return 0;
        }
        float falloff = (float) Math.pow(1.0f - t, 2.2);
        int alpha = Math.round(maxAlpha * falloff);
        if (alpha == 0 && falloff > 0.02f) {
            alpha = 1;
        }
        return Math.min(255, alpha);
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
