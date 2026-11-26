package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.client.GltfBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.block.basic.ModBlocks;
import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;

/**
 * Рендерер буровой крестовины (glTF + голограмма Этапа 1: платформа 9×9 и RotaryTable).
 */
public class MudCrossRenderer extends GltfBlockEntityRenderer<MudCrossBlockEntity> {

    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    private final ReusableAlphaConsumer alphaConsumer = new ReusableAlphaConsumer();

    public MudCrossRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new MudCrossModelDescriptor());
    }

    @Override
    public void render(MudCrossBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (be.isStage1Complete()) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockPos ctrlPos = be.getBlockPos();
        BlockPos wellCenter = be.getWellheadCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();

        long timeMs = level.getGameTime() * 50L + (long) (partialTick * 50.0f);
        float alpha = 0.22f + 0.16f * (float) ((Math.sin(timeMs / 260.0) + 1.0) * 0.5);

        VertexConsumer baseBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        alphaConsumer.bind(baseBuffer, alpha);

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockDispatcher = mc.getBlockRenderer();
        BlockState steelState = ModBlocks.DECO_STEEL.get().defaultBlockState();
        BakedModel steelModel = blockDispatcher.getBlockModel(steelState);

        // 1. Голограмма недостающих блоков платформы 9×9 (слой 0, Y = +1 от MudCross) с отверстием 1×1 в центре
        int platformY = wellCenter.getY() + 1;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) continue;
                scanPos.set(wellCenter.getX() + dx, platformY, wellCenter.getZ() + dz);
                if (level.getBlockState(scanPos).is(ModBlocks.DECO_STEEL.get())) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(relWellX + dx + 0.005f, 1.005f, relWellZ + dz + 0.005f);
                poseStack.scale(0.99f, 0.99f, 0.99f);
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(),
                        alphaConsumer,
                        steelState,
                        steelModel,
                        0.45f, 0.85f, 1.0f,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }
        }

        // 2. Голограмма роторного стола RotaryTable (слой 1, Y = +2 от MudCross)
        if (!be.hasRotaryTableAbove()) {
            BakedModel tableBase = ModModels.ROTARY_TABLE_BASE.get();
            BakedModel tableDisk = ModModels.ROTARY_DISK.get();
            float rotDeg = 180.0f - be.getFacing().toYRot();

            poseStack.pushPose();
            poseStack.translate(relWellX + 0.5f, 2.0f, relWellZ + 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
            poseStack.scale(0.995f, 0.995f, 0.995f);

            if (tableBase != null) {
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(), alphaConsumer, null, tableBase,
                        0.45f, 0.85f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
            }
            if (tableDisk != null) {
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(), alphaConsumer, null, tableDisk,
                        0.45f, 0.85f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
            }
            poseStack.popPose();
        }
    }

    /** Переиспользуемый VertexConsumer без аллокаций на кадр. */
    private static final class ReusableAlphaConsumer implements VertexConsumer {
        private VertexConsumer delegate;
        private float alpha;

        void bind(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            delegate.setColor(r, g, b, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(float r, float g, float b, float a) {
            delegate.setColor(r, g, b, a * alpha);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int a) {
            delegate.setWhiteAlpha((int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad,
                                float red, float green, float blue, float a, int packedLight, int packedOverlay) {
            delegate.putBulkData(pose, quad, red, green, blue, a * alpha, packedLight, packedOverlay);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad,
                                float[] brightness, float red, float green, float blue, float a,
                                int[] lightmap, int packedOverlay, boolean readExistingColor) {
            delegate.putBulkData(pose, quad, brightness, red, green, blue, a * alpha, lightmap, packedOverlay, readExistingColor);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad,
                                float red, float green, float blue, float a,
                                int packedLight, int packedOverlay, boolean readExistingColor) {
            delegate.putBulkData(pose, quad, red, green, blue, a * alpha, packedLight, packedOverlay, readExistingColor);
        }

        @Override
        public VertexConsumer misc(com.mojang.blaze3d.vertex.VertexFormatElement element, int... values) {
            delegate.misc(element, values);
            return this;
        }

        @Override
        public int applyBakedLighting(int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(org.joml.Vector3f normal, ByteBuffer data, org.joml.Matrix3f pose) {
            delegate.applyBakedNormals(normal, data, pose);
        }
    }
}
