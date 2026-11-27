package com.trd.client.render;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.mesh.PrimitiveMaterial;
import com.hex.hexrender.mesh.SkinnedPrimitive;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.block.basic.ModBlocks;
import com.trd.client.gltf.blockentity.drilling.RotaryTableRenderer;
import com.trd.client.render.flywheel.ModModels;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
import com.trd.multiblock.industrial.drilling.RotaryTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Рендерер голограмм Этапа 1 (платформа 9×9 + RotaryTable) и Этапа 2 (кольцо 5×5 + P2P-балки + PipeDrum)
 * на стадии AFTER_TRANSLUCENT_BLOCKS без записи в depth-буфер, чтобы вода позади и перед голограммой
 * сохранялась корректно и не «просвечивало» сухое дно водоёма.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class OilRigHologramRenderer {

    private static final double MAX_WELLHEAD_DIST_SQ = 96.0 * 96.0;
    private static final double MAX_TABLE_DIST_SQ = 128.0 * 128.0;

    private static final String[] DRUM_SLOT_BONES = {
            "slot_pipe_1", "slot_pipe_2", "slot_pipe_3",
            "slot_pipe_4", "slot_pipe_5", "slot_pipe_6"
    };

    private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
    private static final ReusableAlphaConsumer ALPHA_CONSUMER = new ReusableAlphaConsumer();

    private static Matrix4f[] drumPalette = new Matrix4f[24];
    @Nullable
    private static SkeletonPose.NodeTransformState drumPoseState;
    @Nullable
    private static GltfModelAsset cachedDrumAsset;
    private static int cachedDrumGen = -1;
    private static final int[] DRUM_SLOT_BONE_INDICES = {-1, -1, -1, -1, -1, -1};
    private static final Map<RenderType, RenderType> DRUM_TRANSLUCENT_REMAP = new HashMap<>(8);
    private static final List<RenderType> DISTINCT_DRUM_TYPES = new ArrayList<>(8);
    private static final HologramBufferSource HOLOGRAM_BUFFER_SOURCE =
            new HologramBufferSource(DRUM_TRANSLUCENT_REMAP, ALPHA_CONSUMER);

    static {
        for (int i = 0; i < drumPalette.length; i++) {
            drumPalette[i] = new Matrix4f();
        }
    }

    private OilRigHologramRenderer() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            MudCrossBlockEntity.CLIENT_WELLHEADS.clear();
            RotaryTableBlockEntity.CLIENT_TABLES.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (MudCrossBlockEntity.CLIENT_WELLHEADS.isEmpty() && RotaryTableBlockEntity.CLIENT_TABLES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        long timeMs = level.getGameTime() * 50L + (long) (partialTick * 50.0f);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockDispatcher = mc.getBlockRenderer();
        RenderType blockHologramType = HologramRenderTypes.translucentNoDepthWrite(InventoryMenu.BLOCK_ATLAS);

        BlockState steelState = ModBlocks.DECO_STEEL.get().defaultBlockState();
        BakedModel steelModel = blockDispatcher.getBlockModel(steelState);
        BlockState beamState = ModBlocks.BEAM_BLOCK.get().defaultBlockState();
        BakedModel beamModel = blockDispatcher.getBlockModel(beamState);

        boolean drewBlockHologram = false;

        // 1. Голограммы Этапа 1 (установленный MudCross -> платформа 9×9 и RotaryTable)
        if (!MudCrossBlockEntity.CLIENT_WELLHEADS.isEmpty()) {
            float stage1Alpha = 0.22f + 0.16f * (float) ((Math.sin(timeMs / 260.0) + 1.0) * 0.5);
            for (MudCrossBlockEntity wellhead : MudCrossBlockEntity.CLIENT_WELLHEADS) {
                if (wellhead.getLevel() != level || wellhead.isRemoved() || wellhead.isStage1Complete()) {
                    continue;
                }
                BlockPos ctrlPos = wellhead.getBlockPos();
                if (ctrlPos.distToCenterSqr(camPos) > MAX_WELLHEAD_DIST_SQ) {
                    continue;
                }
                VertexConsumer baseBuffer = buffers.getBuffer(blockHologramType);
                ALPHA_CONSUMER.bind(baseBuffer, stage1Alpha);
                renderStage1Wellhead(wellhead, level, camPos, ctrlPos, poseStack, blockDispatcher, steelState, steelModel);
                drewBlockHologram = true;
            }
        }

        // 2. Голограммы Этапа 2 (кольцо 5×5 + 4 наклонные балки + PipeDrum)
        float stage2Alpha = 0.24f + 0.16f * (float) ((Math.sin(timeMs / 260.0) + 1.0) * 0.5);
        if (!RotaryTableBlockEntity.CLIENT_TABLES.isEmpty()) {
            for (RotaryTableBlockEntity table : RotaryTableBlockEntity.CLIENT_TABLES) {
                if (table.getLevel() != level || table.isRemoved() || !table.shouldRenderStage2Hologram()) {
                    continue;
                }
                BlockPos ctrlPos = table.getBlockPos();
                if (ctrlPos.distToCenterSqr(camPos) > MAX_TABLE_DIST_SQ) {
                    continue;
                }
                if (!table.isDerrickAssembled()) {
                    VertexConsumer baseBuffer = buffers.getBuffer(blockHologramType);
                    ALPHA_CONSUMER.bind(baseBuffer, stage2Alpha);
                    renderStage2DerrickBlocks(table, level, camPos, ctrlPos, poseStack, blockDispatcher, steelState, steelModel, beamState, beamModel);
                    drewBlockHologram = true;
                }
            }
        }

        if (drewBlockHologram) {
            buffers.endBatch(blockHologramType);
        }

        // 3. GLTF-голограмма барабана труб (отдельные текстуры атласа сущностей без записи глубины)
        if (!RotaryTableBlockEntity.CLIENT_TABLES.isEmpty()) {
            for (RotaryTableBlockEntity table : RotaryTableBlockEntity.CLIENT_TABLES) {
                if (table.getLevel() != level || table.isRemoved()
                        || !table.shouldRenderStage2Hologram() || table.hasPipeDrumAttachedClient()) {
                    continue;
                }
                BlockPos ctrlPos = table.getBlockPos();
                if (ctrlPos.distToCenterSqr(camPos) > MAX_TABLE_DIST_SQ) {
                    continue;
                }
                renderPipeDrumHologram(table, camPos, ctrlPos, stage2Alpha, poseStack, buffers);
            }
        }
    }

    private static void renderStage1Wellhead(MudCrossBlockEntity be, Level level, Vec3 camPos, BlockPos ctrlPos,
                                             PoseStack poseStack, BlockRenderDispatcher blockDispatcher,
                                             BlockState steelState, BakedModel steelModel) {
        BlockPos wellCenter = be.getWellheadCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();

        poseStack.pushPose();
        poseStack.translate(ctrlPos.getX() - camPos.x, ctrlPos.getY() - camPos.y, ctrlPos.getZ() - camPos.z);

        int platformY = wellCenter.getY() + 1;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) continue;
                SCAN_POS.set(wellCenter.getX() + dx, platformY, wellCenter.getZ() + dz);
                if (level.getBlockState(SCAN_POS).is(ModBlocks.DECO_STEEL.get())) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(relWellX + dx + 0.005f, 1.005f, relWellZ + dz + 0.005f);
                poseStack.scale(0.99f, 0.99f, 0.99f);
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(), ALPHA_CONSUMER, steelState, steelModel,
                        1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }
        }

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
                        poseStack.last(), ALPHA_CONSUMER, null, tableBase,
                        1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
            }
            if (tableDisk != null) {
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(), ALPHA_CONSUMER, null, tableDisk,
                        1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
            }
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void renderStage2DerrickBlocks(RotaryTableBlockEntity be, Level level, Vec3 camPos, BlockPos ctrlPos,
                                                  PoseStack poseStack, BlockRenderDispatcher blockDispatcher,
                                                  BlockState steelState, BakedModel steelModel,
                                                  BlockState beamState, BakedModel beamModel) {
        BlockPos wellCenter = be.getWellCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();

        poseStack.pushPose();
        poseStack.translate(ctrlPos.getX() - camPos.x, ctrlPos.getY() - camPos.y, ctrlPos.getZ() - camPos.z);

        int ringRelY = RotaryTableBlockEntity.RING_HEIGHT_FROM_PLATFORM - 1;
        int ringWorldY = wellCenter.getY() + ringRelY;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                SCAN_POS.set(wellCenter.getX() + dx, ringWorldY, wellCenter.getZ() + dz);
                if (level.getBlockState(SCAN_POS).is(ModBlocks.DECO_STEEL.get())) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(relWellX + dx + 0.005f, ringRelY + 0.005f, relWellZ + dz + 0.005f);
                poseStack.scale(0.99f, 0.99f, 0.99f);
                blockDispatcher.getModelRenderer().renderModel(
                        poseStack.last(), ALPHA_CONSUMER, steelState, steelModel,
                        1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }
        }

        float beamDy = (ringRelY + 0.5f) - (-0.5f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float startX = relWellX + sx * 4.0f + 0.5f;
                float startY = -0.5f;
                float startZ = relWellZ + sz * 4.0f + 0.5f;
                float beamDx = (sx * 2.0f) - (sx * 4.0f);
                float beamDz = (sz * 2.0f) - (sz * 4.0f);
                float horiz = (float) Math.sqrt(beamDx * beamDx + beamDz * beamDz);
                float dist = (float) Math.sqrt(beamDx * beamDx + beamDy * beamDy + beamDz * beamDz);
                float yawRad = (float) Math.atan2(beamDx, beamDz);
                float pitchRad = (float) Math.atan2(beamDy, horiz);
                int fullBlocks = (int) dist;
                float remainder = dist - fullBlocks;

                poseStack.pushPose();
                poseStack.translate(startX, startY, startZ);
                poseStack.mulPose(Axis.YP.rotation(yawRad));
                poseStack.mulPose(Axis.XP.rotation(-pitchRad));
                poseStack.translate(-0.5f, -0.5f, 0.0f);

                for (int i = 0; i <= fullBlocks; i++) {
                    float scaleZ = (i == fullBlocks) ? remainder : 1.0f;
                    if (scaleZ <= 0.001f) continue;

                    poseStack.pushPose();
                    poseStack.translate(0.0f, 0.0f, (float) i);
                    if (i == fullBlocks) {
                        poseStack.scale(1.0f, 1.0f, scaleZ);
                    }
                    blockDispatcher.getModelRenderer().renderModel(
                            poseStack.last(), ALPHA_CONSUMER, beamState, beamModel,
                            1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                    );
                    poseStack.popPose();
                }
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    private static void renderPipeDrumHologram(RotaryTableBlockEntity be, Vec3 camPos, BlockPos ctrlPos,
                                               float alpha, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        GltfModelAsset drumAsset = ModelRegistry.obtain(RotaryTableRenderer.PIPE_DRUM_MODEL);
        if (drumAsset == null) return;

        RigHierarchy skeleton = drumAsset.skeleton();
        int gen = ModelRegistry.generation();
        if (drumAsset != cachedDrumAsset || gen != cachedDrumGen) {
            cachedDrumAsset = drumAsset;
            cachedDrumGen = gen;
            if (drumPalette.length < skeleton.jointCount()) {
                drumPalette = new Matrix4f[skeleton.jointCount()];
                for (int i = 0; i < drumPalette.length; i++) {
                    drumPalette[i] = new Matrix4f();
                }
            }
            if (drumPoseState == null || drumPoseState.translations.length < skeleton.nodeCount()) {
                drumPoseState = new SkeletonPose.NodeTransformState(skeleton.nodeCount());
            }
            for (int i = 0; i < 6; i++) {
                DRUM_SLOT_BONE_INDICES[i] = skeleton.findNodeIndex(DRUM_SLOT_BONES[i]);
            }
            DRUM_TRANSLUCENT_REMAP.clear();
            DISTINCT_DRUM_TYPES.clear();
            for (SkinnedPrimitive prim : drumAsset.primitives()) {
                PrimitiveMaterial mat = prim.material();
                ResourceLocation tex = mat.texture();
                RenderType original = ModelSkinningRenderer.resolveRenderType(tex, mat);
                RenderType hologramType = HologramRenderTypes.translucentNoDepthWrite(tex);
                DRUM_TRANSLUCENT_REMAP.put(original, hologramType);
                DRUM_TRANSLUCENT_REMAP.put(RenderType.entityCutoutNoCull(tex), hologramType);
                if (!DISTINCT_DRUM_TYPES.contains(hologramType)) {
                    DISTINCT_DRUM_TYPES.add(hologramType);
                }
            }
        }

        drumPoseState.reset(skeleton);
        for (int idx : DRUM_SLOT_BONE_INDICES) {
            if (idx >= 0) {
                drumPoseState.scales[idx].set(0.0f, 0.0f, 0.0f);
            }
        }
        SkeletonPose.computeJointPalette(skeleton, drumPoseState, drumPalette);

        BlockPos wellCenter = be.getWellCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();
        float rotDeg = 180.0f - be.getDriveFacing().toYRot();

        HOLOGRAM_BUFFER_SOURCE.bind(buffers, alpha);

        poseStack.pushPose();
        poseStack.translate(ctrlPos.getX() - camPos.x + relWellX + 0.5f,
                ctrlPos.getY() - camPos.y,
                ctrlPos.getZ() - camPos.z + relWellZ + 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
        poseStack.translate(0.0f, 0.0f, -3.0f);
        poseStack.scale(0.995f, 0.995f, 0.995f);

        ModelSkinningRenderer.renderModel(
                drumAsset, drumPalette, poseStack, HOLOGRAM_BUFFER_SOURCE,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null
        );
        poseStack.popPose();

        for (int i = 0; i < DISTINCT_DRUM_TYPES.size(); i++) {
            buffers.endBatch(DISTINCT_DRUM_TYPES.get(i));
        }
    }

    private static final class HologramBufferSource implements MultiBufferSource {
        private final Map<RenderType, RenderType> remap;
        private final ReusableAlphaConsumer alphaConsumer;
        private MultiBufferSource delegate;
        private float alpha;

        HologramBufferSource(Map<RenderType, RenderType> remap, ReusableAlphaConsumer alphaConsumer) {
            this.remap = remap;
            this.alphaConsumer = alphaConsumer;
        }

        void bind(MultiBufferSource delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            RenderType target = remap.getOrDefault(renderType, renderType);
            alphaConsumer.bind(delegate.getBuffer(target), alpha);
            return alphaConsumer;
        }
    }

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
