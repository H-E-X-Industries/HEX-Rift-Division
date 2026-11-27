package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.gpu.GpuSkinningDispatcher;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.motion.SkeletalAnimation;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.api.industiral.rotation.ClientKineticAngleTracker;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.RotaryTableBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Рендерер роторного стола (гибрид Flywheel 1.0 fallback + hexrender CPU-скиннинг каркаса вышки).
 * Голограмма Этапа 2 перенесена в {@link com.trd.client.render.OilRigHologramRenderer} на стадию AFTER_TRANSLUCENT_BLOCKS.
 */
public class RotaryTableRenderer implements BlockEntityRenderer<RotaryTableBlockEntity> {

    public static final ResourceLocation FRACKING_TOWER_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/fracking_tower.gltf");
    public static final ResourceLocation PIPE_DRUM_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/drill_pipe_drum.gltf");

    private Matrix4f[] towerPalette = new Matrix4f[32];
    @Nullable
    private SkeletonPose.NodeTransformState towerPoseState;
    @Nullable
    private GltfModelAsset cachedTowerAsset;
    private int cachedTowerGen = -1;
    private int carriedPipeBoneIdx = -1;

    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

    public RotaryTableRenderer(BlockEntityRendererProvider.Context context) {
        for (int i = 0; i < towerPalette.length; i++) {
            towerPalette[i] = new Matrix4f();
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

        // 1. Vanilla / Iris shadow fallback для базы, диска, входного вала и спускающейся колонны
        if (!FlywheelFallbackHelper.isFlywheelActive(level)) {
            renderFlywheelFallback(be, level, wellCenter, relWellX, relWellZ, rotDeg, partialTick, poseStack, bufferSource, packedOverlay);
        }

        // 2. Собранный каркас вышки fracking_tower.gltf (HEX-Render GPU Skinning)
        if (be.isDerrickAssembled()) {
            renderDerrickTower(be, level, wellCenter, relWellX, relWellZ, rotDeg, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
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

        float spinRad = be.getDiskSpinAngleRad(partialTick);
        poseStack.mulPose(Axis.YP.rotation(spinRad));
        FlywheelFallbackHelper.renderPartialModel(ModModels.ROTARY_DISK, poseStack, bufferSource, be.getBlockState(), light, packedOverlay);
        poseStack.popPose();

        BlockPos ctrlPos = be.getBlockPos();
        BlockPos portPos = be.getKineticPortPos();
        Direction portSide = be.getKineticPortSide();
        int portLight = LevelRenderer.getLightColor(level, portPos);
        float shaftAngleRad = ClientKineticAngleTracker.getAngle(be, portSide, partialTick);
        poseStack.pushPose();
        poseStack.translate(portPos.getX() - ctrlPos.getX() + 0.5f, portPos.getY() - ctrlPos.getY() + 0.5f, portPos.getZ() - ctrlPos.getZ() + 0.5f);
        if (portSide.getAxis() == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(portSide == Direction.EAST ? 270 : 90));
        } else if (portSide == Direction.SOUTH) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.ZP.rotation(shaftAngleRad));
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        FlywheelFallbackHelper.renderPartialModel(ModModels.SHAFT_MODELS.get("shaft_light_iron"), poseStack, bufferSource, be.getBlockState(), portLight, packedOverlay);
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
                for (int i = 0; i < towerPalette.length; i++) {
                    towerPalette[i] = new Matrix4f();
                }
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
        poseStack.popPose();
    }
}
