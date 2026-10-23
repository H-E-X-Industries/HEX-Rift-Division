package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.lighting.*;
import com.trd.block.entity.industrial.lighting.SocketBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * 3D-рендер теплового світлового ореолу (Emissive Glow / Halo Effect) для ламп розжарення.
 * Формує сліпучо-біле розпечене ядро (#FFFDF0) та аддитивний тепловий ореол
 * із градієнтом від гарячого оранжевого (#FF7700) до темно-вишневого (#AA1100).
 */
public class SocketBlockEntityRenderer implements BlockEntityRenderer<SocketBlockEntity> {

    public SocketBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SocketBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ElectricLampBlock lampBlock)) {
            return;
        }
        if (!state.hasProperty(ElectricLampBlock.LIT) || !state.getValue(ElectricLampBlock.LIT)) {
            return;
        }
        if (state.hasProperty(ElectricLampBlock.BROKEN) && state.getValue(ElectricLampBlock.BROKEN)) {
            return;
        }

        Direction facing = state.hasProperty(ElectricLampBlock.FACING)
                ? state.getValue(ElectricLampBlock.FACING)
                : Direction.UP;

        poseStack.pushPose();

        // Орієнтація за напрямком кріплення патрона FACING
        poseStack.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
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

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        if (lampBlock instanceof TungstenLampBlock) {
            renderTungstenGlow(poseStack, consumer);
        } else if (lampBlock instanceof CarbonLampBlock) {
            renderCarbonGlow(poseStack, consumer);
        } else if (lampBlock instanceof FluorescentLampBlock) {
            renderFluorescentGlow(poseStack, consumer);
        } else if (lampBlock instanceof LedLampBlock) {
            renderLedGlow(poseStack, consumer);
        }

        poseStack.popPose();
    }

    /**
     * Вольфрамова лампа: горизонтальна спіраль розжарення з гарячим сліпучим ядром
     * та 4-шаровим аддитивним ореолом (Core -> Golden -> Hot Orange -> Dark Cherry).
     */
    private void renderTungstenGlow(PoseStack poseStack, VertexConsumer consumer) {
        // Нитка: X=0.40..0.60, Y=0.534..0.553, Z=0.489..0.511

        // 1. Сліпуче біле ядро (#FFFDF0)
        renderGlowingBox(poseStack, consumer,
                0.400f, 0.534f, 0.489f,
                0.600f, 0.553f, 0.511f,
                255, 253, 240, 255);

        // 2. Золотисто-жовтий перехідний шар (+0.15 px)
        renderGlowingBox(poseStack, consumer,
                0.390f, 0.525f, 0.480f,
                0.610f, 0.562f, 0.520f,
                255, 210, 40, 180);

        // 3. Гарячий оранжевий ореол #FF7700 (+0.35 px)
        renderGlowingBox(poseStack, consumer,
                0.378f, 0.512f, 0.467f,
                0.622f, 0.575f, 0.533f,
                255, 119, 0, 120);

        // 4. Темно-вишневий зовнішній контур #AA1100 (+0.55 px)
        renderGlowingBox(poseStack, consumer,
                0.366f, 0.500f, 0.455f,
                0.634f, 0.587f, 0.545f,
                170, 17, 0, 60);
    }

    /**
     * Вугільна лампа: висока аркова нитка з насиченим бурштиново-вишневим ореолом.
     */
    private void renderCarbonGlow(PoseStack poseStack, VertexConsumer consumer) {
        // Ліва стійка: X=0.438..0.456, Y=0.412..0.556, Z=0.490..0.510
        renderCarbonSegment(poseStack, consumer, 0.438f, 0.412f, 0.490f, 0.456f, 0.556f, 0.510f);

        // Права стійка: X=0.544..0.562, Y=0.412..0.556, Z=0.490..0.510
        renderCarbonSegment(poseStack, consumer, 0.544f, 0.412f, 0.490f, 0.562f, 0.556f, 0.510f);

        // Верхня перемичка арки: X=0.447..0.553, Y=0.550..0.572, Z=0.490..0.510
        renderCarbonSegment(poseStack, consumer, 0.447f, 0.550f, 0.490f, 0.553f, 0.572f, 0.510f);
    }

    private void renderCarbonSegment(PoseStack poseStack, VertexConsumer consumer,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        // 1. Гаряче ядро
        renderGlowingBox(poseStack, consumer,
                minX, minY, minZ, maxX, maxY, maxZ,
                255, 248, 224, 255);

        // 2. Золотавий перехід (+0.15 px = 0.0094)
        renderGlowingBox(poseStack, consumer,
                minX - 0.0094f, minY - 0.0094f, minZ - 0.0094f,
                maxX + 0.0094f, maxY + 0.0094f, maxZ + 0.0094f,
                255, 170, 16, 170);

        // 3. Гарячий оранжевий ореол (+0.35 px = 0.022)
        renderGlowingBox(poseStack, consumer,
                minX - 0.022f, minY - 0.022f, minZ - 0.022f,
                maxX + 0.022f, maxY + 0.022f, maxZ + 0.022f,
                255, 100, 0, 110);

        // 4. Темно-вишневий контур (+0.55 px = 0.034)
        renderGlowingBox(poseStack, consumer,
                minX - 0.034f, minY - 0.034f, minZ - 0.034f,
                maxX + 0.034f, maxY + 0.034f, maxZ + 0.034f,
                160, 10, 0, 50);
    }

    /**
     * Люмінесцентна лампа: м'який біло-блакитний ореол навколо спіралей.
     */
    private void renderFluorescentGlow(PoseStack poseStack, VertexConsumer consumer) {
        renderGlowingBox(poseStack, consumer,
                0.368f, 0.412f, 0.368f,
                0.632f, 0.718f, 0.632f,
                220, 248, 255, 45);
        renderGlowingBox(poseStack, consumer,
                0.380f, 0.420f, 0.380f,
                0.620f, 0.710f, 0.620f,
                240, 252, 255, 80);
    }

    /**
     * LED лампа: яскраве холодно-біле світіння SMD чіпів.
     */
    private void renderLedGlow(PoseStack poseStack, VertexConsumer consumer) {
        renderGlowingBox(poseStack, consumer,
                0.460f, 0.228f, 0.460f,
                0.540f, 0.245f, 0.540f,
                255, 255, 250, 230);
        renderGlowingBox(poseStack, consumer,
                0.440f, 0.220f, 0.440f,
                0.560f, 0.255f, 0.560f,
                220, 240, 255, 80);
    }

    private void renderGlowingBox(PoseStack poseStack, VertexConsumer consumer,
                                   float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ,
                                   int r, int g, int b, int a) {
        Matrix4f mat = poseStack.last().pose();

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
    public boolean shouldRenderOffScreen(SocketBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(SocketBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.2);
    }
}
