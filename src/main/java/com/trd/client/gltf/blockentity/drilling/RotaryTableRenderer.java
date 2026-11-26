package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.gpu.GpuSkinningDispatcher;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.mesh.PrimitiveMaterial;
import com.hex.hexrender.mesh.SkinnedPrimitive;
import com.hex.hexrender.motion.SkeletalAnimation;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.api.industiral.rotation.ClientKineticAngleTracker;
import com.trd.block.basic.ModBlocks;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.RotaryTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Рендерер роторного стола (гибрид Flywheel 1.0 fallback + hexrender glTF каркаса вышки + голограмма Этапа 2).
 * Все буферы скелета, палитры матриц и потребители вершин переиспользуются без аллокаций на кадр.
 */
public class RotaryTableRenderer implements BlockEntityRenderer<RotaryTableBlockEntity> {

    public static final ResourceLocation FRACKING_TOWER_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/fracking_tower.gltf");
    public static final ResourceLocation PIPE_DRUM_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/drill_pipe_drum.gltf");

    private static final String[] DRUM_SLOT_BONES = {
            "slot_pipe_1", "slot_pipe_2", "slot_pipe_3",
            "slot_pipe_4", "slot_pipe_5", "slot_pipe_6"
    };

    private Matrix4f[] towerPalette = new Matrix4f[32];
    @Nullable
    private SkeletonPose.NodeTransformState towerPoseState;
    @Nullable
    private GltfModelAsset cachedTowerAsset;
    private int cachedTowerGen = -1;
    private int carriedPipeBoneIdx = -1;

    private Matrix4f[] drumPalette = new Matrix4f[24];
    @Nullable
    private SkeletonPose.NodeTransformState drumPoseState;
    @Nullable
    private GltfModelAsset cachedDrumAsset;
    private int cachedDrumGen = -1;
    private final int[] drumSlotBoneIndices = {-1, -1, -1, -1, -1, -1};
    private final Map<RenderType, RenderType> drumTranslucentRemap = new HashMap<>(8);

    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    private final ReusableAlphaConsumer alphaConsumer = new ReusableAlphaConsumer();
    private final HologramBufferSource hologramBufferSource = new HologramBufferSource(drumTranslucentRemap, alphaConsumer);

    public RotaryTableRenderer(BlockEntityRendererProvider.Context context) {
        initPalette(towerPalette);
        initPalette(drumPalette);
    }

    private static void initPalette(Matrix4f[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Matrix4f();
        }
    }

    @Override
    public AABB getRenderBoundingBox(RotaryTableBlockEntity be) {
        BlockPos well = be.getWellCenterPos();
        int minY = well.getY() - (Math.max(1, be.getPipeCountInWell() + 2) * 4);
        return new AABB(
                well.getX() - 5.5, minY, well.getZ() - 5.5,
                well.getX() + 6.5, well.getY() + 23.0, well.getZ() + 6.5
        );
    }

    @Override
    public boolean shouldRenderOffScreen(RotaryTableBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(RotaryTableBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockPos ctrlPos = be.getBlockPos();
        BlockPos wellCenter = be.getWellCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();
        float rotDeg = 180.0f - be.getDriveFacing().toYRot();

        // 1. Vanilla / Iris shadow fallback для базы, диска и спускающейся колонны
        if (!FlywheelFallbackHelper.isFlywheelActive(level)) {
            renderFlywheelFallback(be, level, wellCenter, relWellX, relWellZ, rotDeg, partialTick, poseStack, bufferSource, packedOverlay);
        }

        // 2. Собранный каркас вышки fracking_tower.gltf (hexrender GPU skinning)
        if (be.isDerrickAssembled()) {
            renderDerrickTower(be, level, wellCenter, relWellX, relWellZ, rotDeg, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }

        // 3. Голограммы Этапа 2: кольцо 5×5 + 4 наклонные P2P-балки + PipeDrum на локальной -Z
        if (be.shouldRenderStage2Hologram()) {
            renderStage2Holograms(be, level, wellCenter, relWellX, relWellZ, rotDeg, partialTick, poseStack, bufferSource);
        }
    }

    private void renderFlywheelFallback(RotaryTableBlockEntity be, Level level, BlockPos wellCenter,
                                        int relWellX, int relWellZ, float rotDeg, float partialTick,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        int light = LevelRenderer.getLightColor(level, wellCenter);

        poseStack.pushPose();
        poseStack.translate(relWellX + 0.5f, 0.0f, relWellZ + 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
        FlywheelFallbackHelper.renderPartialModel(ModModels.ROTARY_TABLE_BASE, poseStack, bufferSource, be.getBlockState(), light, packedOverlay);

        float spinRad;
        if (!be.isExtractingStopped()) {
            spinRad = ClientKineticAngleTracker.getAngle(be, null, partialTick);
            be.setFrozenDiskAngle(spinRad);
        } else {
            spinRad = be.getFrozenDiskAngle();
        }
        poseStack.mulPose(Axis.YP.rotation(spinRad));
        FlywheelFallbackHelper.renderPartialModel(ModModels.ROTARY_DISK, poseStack, bufferSource, be.getBlockState(), light, packedOverlay);
        poseStack.popPose();

        if (be.isDescendingPipe()) {
            int count = be.getPipeCountInWell() + 1;
            float descent = be.getVisualDescentBlocks(partialTick);
            for (int i = 0; i < count; i++) {
                float bottomRelY = -4.0f * i - descent;
                scanPos.set(wellCenter.getX(), Mth.floor(wellCenter.getY() + bottomRelY + 3.0f), wellCenter.getZ());
                int pipeLight = LevelRenderer.getLightColor(level, scanPos);

                poseStack.pushPose();
                poseStack.translate(relWellX + 0.5f, bottomRelY, relWellZ + 0.5f);
                poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
                poseStack.mulPose(Axis.YP.rotation(spinRad));
                FlywheelFallbackHelper.renderPartialModel(ModModels.DRILL_PIPE, poseStack, bufferSource, be.getBlockState(), pipeLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    private void renderDerrickTower(RotaryTableBlockEntity be, Level level, BlockPos wellCenter,
                                    int relWellX, int relWellZ, float rotDeg, float partialTick,
                                    PoseStack poseStack, MultiBufferSource bufferSource,
                                    int packedLight, int packedOverlay) {
        GltfModelAsset asset = ModelRegistry.obtain(FRACKING_TOWER_MODEL);
        if (asset == null) return;

        RigHierarchy skeleton = asset.skeleton();
        int gen = ModelRegistry.generation();
        if (asset != cachedTowerAsset || gen != cachedTowerGen) {
            cachedTowerAsset = asset;
            cachedTowerGen = gen;
            if (towerPalette.length < skeleton.jointCount()) {
                towerPalette = new Matrix4f[skeleton.jointCount()];
                initPalette(towerPalette);
            }
            if (towerPoseState == null || towerPoseState.translations.length < skeleton.nodeCount()) {
                towerPoseState = new SkeletonPose.NodeTransformState(skeleton.nodeCount());
            }
            carriedPipeBoneIdx = skeleton.findNodeIndex("carried_pipe_bone");
        }

        towerPoseState.reset(skeleton);
        SkeletalAnimation clip = asset.animation("pipe_feed_cycle");
        if (clip != null) {
            float animTicks = be.isTowerAnimPaused()
                    ? be.getTowerAnimTick()
                    : be.getVisualAnimTick() + partialTick;
            float seconds = Mth.clamp(animTicks / 20.0f, 0.0f, clip.duration());
            clip.apply(seconds, towerPoseState);
        }

        if (carriedPipeBoneIdx >= 0) {
            float s = be.isHoldingPipe() ? 1.0f : 0.0f;
            towerPoseState.scales[carriedPipeBoneIdx].set(s, s, s);
        }

        SkeletonPose.computeJointPalette(skeleton, towerPoseState, towerPalette);

        scanPos.set(wellCenter.getX(), wellCenter.getY() + 10, wellCenter.getZ());
        int towerLight = Math.max(packedLight, LevelRenderer.getLightColor(level, scanPos));

        poseStack.pushPose();
        poseStack.translate(relWellX + 0.5f, 0.0f, relWellZ + 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
        GpuSkinningDispatcher.renderModel(asset, towerPalette, poseStack, bufferSource, towerLight, packedOverlay, 0xFFFFFFFF, null, true);
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        poseStack.popPose();
    }

    private void renderStage2Holograms(RotaryTableBlockEntity be, Level level, BlockPos wellCenter,
                                       int relWellX, int relWellZ, float rotDeg, float partialTick,
                                       PoseStack poseStack, MultiBufferSource bufferSource) {
        long timeMs = level.getGameTime() * 50L + (long) (partialTick * 50.0f);
        float alpha = 0.24f + 0.16f * (float) ((Math.sin(timeMs / 260.0) + 1.0) * 0.5);

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockDispatcher = mc.getBlockRenderer();

        if (!be.isDerrickAssembled()) {
            VertexConsumer baseBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            alphaConsumer.bind(baseBuffer, alpha);

            BlockState steelState = ModBlocks.DECO_STEEL.get().defaultBlockState();
            BakedModel steelModel = blockDispatcher.getBlockModel(steelState);
            BlockState beamState = ModBlocks.BEAM_BLOCK.get().defaultBlockState();
            BakedModel beamModel = blockDispatcher.getBlockModel(beamState);

            // Голограмма верхнего полого кольца 5×5 на высоте Y_platform + 20 (Y_well - 1 + 20 = Y_well + 19)
            int ringRelY = RotaryTableBlockEntity.RING_HEIGHT_FROM_PLATFORM - 1;
            int ringWorldY = wellCenter.getY() + ringRelY;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                    scanPos.set(wellCenter.getX() + dx, ringWorldY, wellCenter.getZ() + dz);
                    if (level.getBlockState(scanPos).is(ModBlocks.DECO_STEEL.get())) {
                        continue;
                    }
                    poseStack.pushPose();
                    poseStack.translate(relWellX + dx + 0.005f, ringRelY + 0.005f, relWellZ + dz + 0.005f);
                    poseStack.scale(0.99f, 0.99f, 0.99f);
                    blockDispatcher.getModelRenderer().renderModel(
                            poseStack.last(), alphaConsumer, steelState, steelModel,
                            0.45f, 0.85f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                    );
                    poseStack.popPose();
                }
            }

            // Голограмма 4 наклонных P2P-балок от углов платформы (dx=±4, dy=-1) к углам кольца (dx=±2, dy=ringRelY)
            float beamDy = (ringRelY + 0.5f) - (-0.5f);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float beamDx = (sx * 2.0f) - (sx * 4.0f);
                    float beamDz = (sz * 2.0f) - (sz * 4.0f);
                    float len = (float) Math.sqrt(beamDx * beamDx + beamDy * beamDy + beamDz * beamDz);
                    float yawRad = (float) Math.atan2(beamDx, beamDz);
                    float pitchRad = (float) Math.acos(beamDy / len);

                    poseStack.pushPose();
                    poseStack.translate(relWellX + sx * 4.0f + 0.5f, -0.5f, relWellZ + sz * 4.0f + 0.5f);
                    poseStack.mulPose(Axis.YP.rotation(yawRad));
                    poseStack.mulPose(Axis.XP.rotation(pitchRad));
                    poseStack.scale(0.38f, len, 0.38f);
                    poseStack.translate(-0.5f, 0.0f, -0.5f);
                    blockDispatcher.getModelRenderer().renderModel(
                            poseStack.last(), alphaConsumer, beamState, beamModel,
                            0.45f, 0.85f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                    );
                    poseStack.popPose();
                }
            }
        }

        // Голограмма установки PipeDrum со стороны локальной -Z (отверстие на Z=-3 для контроллера)
        if (!be.hasPipeDrumAttachedClient()) {
            renderPipeDrumHologram(be, relWellX, relWellZ, rotDeg, alpha, poseStack, bufferSource);
        }
    }

    private void renderPipeDrumHologram(RotaryTableBlockEntity be, int relWellX, int relWellZ, float rotDeg,
                                        float alpha, PoseStack poseStack, MultiBufferSource bufferSource) {
        GltfModelAsset drumAsset = ModelRegistry.obtain(PIPE_DRUM_MODEL);
        if (drumAsset == null) return;

        RigHierarchy skeleton = drumAsset.skeleton();
        int gen = ModelRegistry.generation();
        if (drumAsset != cachedDrumAsset || gen != cachedDrumGen) {
            cachedDrumAsset = drumAsset;
            cachedDrumGen = gen;
            if (drumPalette.length < skeleton.jointCount()) {
                drumPalette = new Matrix4f[skeleton.jointCount()];
                initPalette(drumPalette);
            }
            if (drumPoseState == null || drumPoseState.translations.length < skeleton.nodeCount()) {
                drumPoseState = new SkeletonPose.NodeTransformState(skeleton.nodeCount());
            }
            for (int i = 0; i < 6; i++) {
                drumSlotBoneIndices[i] = skeleton.findNodeIndex(DRUM_SLOT_BONES[i]);
            }
            drumTranslucentRemap.clear();
            for (SkinnedPrimitive prim : drumAsset.primitives()) {
                PrimitiveMaterial mat = prim.material();
                ResourceLocation tex = mat.texture();
                RenderType original = ModelSkinningRenderer.resolveRenderType(tex, mat);
                RenderType translucent = RenderType.entityTranslucent(tex);
                drumTranslucentRemap.put(original, translucent);
                drumTranslucentRemap.put(RenderType.entityCutoutNoCull(tex), translucent);
            }
        }

        drumPoseState.reset(skeleton);
        for (int idx : drumSlotBoneIndices) {
            if (idx >= 0) {
                drumPoseState.scales[idx].set(0.0f, 0.0f, 0.0f);
            }
        }
        SkeletonPose.computeJointPalette(skeleton, drumPoseState, drumPalette);

        hologramBufferSource.bind(bufferSource, alpha);

        poseStack.pushPose();
        poseStack.translate(relWellX + 0.5f, 0.0f, relWellZ + 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
        poseStack.translate(0.0f, 0.0f, -3.0f);
        poseStack.scale(0.995f, 0.995f, 0.995f);

        ModelSkinningRenderer.renderModel(
                drumAsset, drumPalette, poseStack, hologramBufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF73D9FF, null
        );
        poseStack.popPose();
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
