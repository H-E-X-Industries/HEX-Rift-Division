package com.trd.client.render;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.mesh.PrimitiveMaterial;
import com.hex.hexrender.mesh.SkinnedPrimitive;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.deco.BeamBlock;
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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Рендерер голограмм Этапа 1 (платформа 9×9 + RotaryTable) и Этапа 2 (каркас 5×5 + P2P-балки + PipeDrum)
 * на стадии AFTER_LEVEL (после облаков, погоды и composite/final проходов шейдеров) без записи в depth-буфер,
 * сохраняя корректный depth-test по сцене без искажения воды, неба и облаков.
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
    private static final RandomSource HOLOGRAM_RANDOM = RandomSource.create(42L);
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
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

        BlockState hazardBrightState = ModBlocks.CONCRETE_HAZARD_BRIGHT.get().defaultBlockState();
        BakedModel hazardBrightModel = blockDispatcher.getBlockModel(hazardBrightState);
        BlockState concreteLightState = ModBlocks.CONCRETE_STRIPPED.get().defaultBlockState();
        BakedModel concreteLightModel = blockDispatcher.getBlockModel(concreteLightState);
        BlockState decoBeamState = ModBlocks.DECO_BEAM.get().defaultBlockState();
        BakedModel decoBeamModel = blockDispatcher.getBlockModel(decoBeamState);
        BlockState beamStateZ = ModBlocks.BEAM_BLOCK.get().defaultBlockState().setValue(BeamBlock.FACING, Direction.NORTH);
        BakedModel beamModelZ = blockDispatcher.getBlockModel(beamStateZ);
        BlockState beamStateX = ModBlocks.BEAM_BLOCK.get().defaultBlockState().setValue(BeamBlock.FACING, Direction.EAST);
        BakedModel beamModelX = blockDispatcher.getBlockModel(beamStateX);

        boolean drewBlockHologram = false;

        mc.getMainRenderTarget().bindWrite(false);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
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
                    renderStage1Wellhead(
                            wellhead, level, camPos, ctrlPos, poseStack, blockDispatcher,
                            hazardBrightState, hazardBrightModel, concreteLightState, concreteLightModel
                    );
                    drewBlockHologram = true;
                }
            }

            // 2. Голограммы Этапа 2 (каркас 5×5 + 4 наклонные балки + PipeDrum)
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
                        renderStage2DerrickBlocks(
                                table, level, camPos, ctrlPos, poseStack, blockDispatcher,
                                decoBeamState, decoBeamModel, beamStateX, beamModelX, beamStateZ, beamModelZ
                        );
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
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void renderStage1Wellhead(MudCrossBlockEntity be, Level level, Vec3 camPos, BlockPos ctrlPos,
                                             PoseStack poseStack, BlockRenderDispatcher blockDispatcher,
                                             BlockState hazardBrightState, BakedModel hazardBrightModel,
                                             BlockState concreteLightState, BakedModel concreteLightModel) {
        BlockPos wellCenter = be.getWellheadCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();

        poseStack.pushPose();
        poseStack.translate(ctrlPos.getX() - camPos.x, ctrlPos.getY() - camPos.y, ctrlPos.getZ() - camPos.z);

        int platformY = wellCenter.getY() + 1;
        Block hazardBrightBlock = ModBlocks.CONCRETE_HAZARD_BRIGHT.get();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block expectedBlock = RotaryTableBlockEntity.getExpectedPlatformBlock(dx, dz);
                if (expectedBlock == null) continue;
                SCAN_POS.set(wellCenter.getX() + dx, platformY, wellCenter.getZ() + dz);
                if (level.getBlockState(SCAN_POS).is(expectedBlock)) {
                    continue;
                }
                int cullMask = 0;
                if (isStage1PlatformCell(dx, dz - 1)) cullMask |= dirMask(Direction.NORTH);
                if (isStage1PlatformCell(dx, dz + 1)) cullMask |= dirMask(Direction.SOUTH);
                if (isStage1PlatformCell(dx - 1, dz)) cullMask |= dirMask(Direction.WEST);
                if (isStage1PlatformCell(dx + 1, dz)) cullMask |= dirMask(Direction.EAST);

                boolean isRim = expectedBlock == hazardBrightBlock;
                BlockState targetState = isRim ? hazardBrightState : concreteLightState;
                BakedModel targetModel = isRim ? hazardBrightModel : concreteLightModel;

                poseStack.pushPose();
                poseStack.translate(relWellX + dx, 1.002f, relWellZ + dz);
                poseStack.scale(1.0f, 0.996f, 1.0f);
                renderModelWithCulledFaces(poseStack.last(), ALPHA_CONSUMER, targetState, targetModel, cullMask);
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
                                                  BlockState decoBeamState, BakedModel decoBeamModel,
                                                  BlockState beamStateX, BakedModel beamModelX,
                                                  BlockState beamStateZ, BakedModel beamModelZ) {
        BlockPos wellCenter = be.getWellCenterPos();
        int relWellX = wellCenter.getX() - ctrlPos.getX();
        int relWellZ = wellCenter.getZ() - ctrlPos.getZ();

        poseStack.pushPose();
        poseStack.translate(ctrlPos.getX() - camPos.x, ctrlPos.getY() - camPos.y, ctrlPos.getZ() - camPos.z);

        int ringRelY = RotaryTableBlockEntity.RING_HEIGHT_FROM_PLATFORM - 1;
        int ringWorldY = wellCenter.getY() + ringRelY;

        // 4 угла верхнего каркаса 5×5 из deco_beam
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int dx = sx * 2;
                int dz = sz * 2;
                SCAN_POS.set(wellCenter.getX() + dx, ringWorldY, wellCenter.getZ() + dz);
                if (level.getBlockState(SCAN_POS).is(ModBlocks.DECO_BEAM.get())) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(relWellX + dx, ringRelY + 0.002f, relWellZ + dz);
                poseStack.scale(1.0f, 0.996f, 1.0f);
                renderModelWithCulledFaces(poseStack.last(), ALPHA_CONSUMER, decoBeamState, decoBeamModel, 0);
                poseStack.popPose();
            }
        }

        // 4 горизонтальных ребра верхнего каркаса 5×5 (ручные beam_block или P2P-балка)
        BlockPos nw = new BlockPos(wellCenter.getX() - 2, ringWorldY, wellCenter.getZ() - 2);
        BlockPos ne = new BlockPos(wellCenter.getX() + 2, ringWorldY, wellCenter.getZ() - 2);
        BlockPos sw = new BlockPos(wellCenter.getX() - 2, ringWorldY, wellCenter.getZ() + 2);
        BlockPos se = new BlockPos(wellCenter.getX() + 2, ringWorldY, wellCenter.getZ() + 2);
        renderStage2RingEdge(level, wellCenter, relWellX, relWellZ, ringRelY, nw, ne, Direction.Axis.X, poseStack, beamStateX, beamModelX);
        renderStage2RingEdge(level, wellCenter, relWellX, relWellZ, ringRelY, sw, se, Direction.Axis.X, poseStack, beamStateX, beamModelX);
        renderStage2RingEdge(level, wellCenter, relWellX, relWellZ, ringRelY, nw, sw, Direction.Axis.Z, poseStack, beamStateZ, beamModelZ);
        renderStage2RingEdge(level, wellCenter, relWellX, relWellZ, ringRelY, ne, se, Direction.Axis.Z, poseStack, beamStateZ, beamModelZ);

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
                int lastIdx = remainder > 0.001f ? fullBlocks : (fullBlocks - 1);

                poseStack.pushPose();
                poseStack.translate(startX, startY, startZ);
                poseStack.mulPose(Axis.YP.rotation(yawRad));
                poseStack.mulPose(Axis.XP.rotation(-pitchRad));
                poseStack.translate(-0.5f, -0.5f, 0.0f);

                for (int i = 0; i <= lastIdx; i++) {
                    float scaleZ = (i == fullBlocks) ? remainder : 1.0f;
                    if (scaleZ <= 0.001f) continue;

                    int cullMask = 0;
                    if (i > 0) cullMask |= dirMask(Direction.NORTH);
                    if (i < lastIdx) cullMask |= dirMask(Direction.SOUTH);

                    poseStack.pushPose();
                    poseStack.translate(0.0f, 0.0f, (float) i);
                    if (i == fullBlocks) {
                        poseStack.scale(1.0f, 1.0f, scaleZ);
                    }
                    renderModelWithCulledFaces(poseStack.last(), ALPHA_CONSUMER, beamStateZ, beamModelZ, cullMask);
                    poseStack.popPose();
                }
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    private static void renderStage2RingEdge(Level level, BlockPos wellCenter, int relWellX, int relWellZ, int ringRelY,
                                             BlockPos cornerA, BlockPos cornerB, Direction.Axis axis,
                                             PoseStack poseStack, BlockState beamState, BakedModel beamModel) {
        if (RotaryTableBlockEntity.findMatchingRingEdgeBeam(level, cornerA, cornerB) != null) {
            return;
        }
        int stepX = Integer.signum(cornerB.getX() - cornerA.getX());
        int stepZ = Integer.signum(cornerB.getZ() - cornerA.getZ());
        int cullMask = (axis == Direction.Axis.X)
                ? (dirMask(Direction.WEST) | dirMask(Direction.EAST))
                : (dirMask(Direction.NORTH) | dirMask(Direction.SOUTH));

        for (int i = 1; i <= 3; i++) {
            int worldX = cornerA.getX() + stepX * i;
            int worldZ = cornerA.getZ() + stepZ * i;
            SCAN_POS.set(worldX, cornerA.getY(), worldZ);
            if (RotaryTableBlockEntity.isValidManualRingBeam(level.getBlockState(SCAN_POS), axis)) {
                continue;
            }
            int dx = worldX - wellCenter.getX();
            int dz = worldZ - wellCenter.getZ();
            poseStack.pushPose();
            poseStack.translate(relWellX + dx, ringRelY + 0.002f, relWellZ + dz);
            poseStack.scale(1.0f, 0.996f, 1.0f);
            renderModelWithCulledFaces(poseStack.last(), ALPHA_CONSUMER, beamState, beamModel, cullMask);
            poseStack.popPose();
        }
    }

    private static boolean isStage1PlatformCell(int dx, int dz) {
        return Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && !(dx == 0 && dz == 0);
    }

    private static int dirMask(Direction dir) {
        return 1 << dir.ordinal();
    }

    private static void renderModelWithCulledFaces(PoseStack.Pose pose, VertexConsumer consumer,
                                                   @Nullable BlockState state, BakedModel model, int culledDirMask) {
        for (Direction side : ALL_DIRECTIONS) {
            if ((culledDirMask & dirMask(side)) != 0) {
                continue;
            }
            emitQuads(pose, consumer, model.getQuads(state, side, HOLOGRAM_RANDOM, ModelData.EMPTY, null), 0);
        }
        emitQuads(pose, consumer, model.getQuads(state, null, HOLOGRAM_RANDOM, ModelData.EMPTY, null), culledDirMask);
    }

    private static void emitQuads(PoseStack.Pose pose, VertexConsumer consumer,
                                  List<BakedQuad> quads, int culledDirMask) {
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            if (culledDirMask != 0 && (culledDirMask & dirMask(quad.getDirection())) != 0) {
                continue;
            }
            consumer.putBulkData(pose, quad, 1.0f, 1.0f, 1.0f, 1.0f,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
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
                ResourceLocation resolvedTex = tex != null ? tex : ResourceLocation.withDefaultNamespace("textures/misc/white.png");
                RenderType hologramType = HologramRenderTypes.translucentNoDepthWrite(resolvedTex);
                DRUM_TRANSLUCENT_REMAP.put(ModelSkinningRenderer.resolveRenderType(tex, mat, false), hologramType);
                DRUM_TRANSLUCENT_REMAP.put(ModelSkinningRenderer.resolveRenderType(tex, mat, true), hologramType);
                DRUM_TRANSLUCENT_REMAP.put(RenderType.entityCutoutNoCull(resolvedTex), hologramType);
                DRUM_TRANSLUCENT_REMAP.put(RenderType.entityTranslucent(resolvedTex), hologramType);
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
