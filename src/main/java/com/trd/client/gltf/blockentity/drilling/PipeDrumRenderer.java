package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.gpu.GpuSkinningDispatcher;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.PipeDrumBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * GLTF-рендерер револьверного барабана (drill_pipe_drum.gltf).
 * Управляет углом carousel_drum_bone и масштабами 6 костей slot_pipe_1..6 без аллокаций на кадр.
 */
public class PipeDrumRenderer implements BlockEntityRenderer<PipeDrumBlockEntity> {

    public static final ResourceLocation DRUM_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/drill_pipe_drum.gltf");

    private static final String[] SLOT_BONE_NAMES = {
            "slot_pipe_1", "slot_pipe_2", "slot_pipe_3",
            "slot_pipe_4", "slot_pipe_5", "slot_pipe_6"
    };

    private Matrix4f[] palette = new Matrix4f[24];
    @Nullable
    private SkeletonPose.NodeTransformState poseState;
    @Nullable
    private GltfModelAsset cachedAsset;
    private int cachedGen = -1;
    private int carouselBoneIdx = -1;
    private final int[] slotBoneIndices = {-1, -1, -1, -1, -1, -1};
    private final Quaternionf scratchQuat = new Quaternionf();

    public PipeDrumRenderer(BlockEntityRendererProvider.Context context) {
        initPalette(palette);
    }

    private static void initPalette(Matrix4f[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Matrix4f();
        }
    }

    @Override
    public AABB getRenderBoundingBox(PipeDrumBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 3.0, p.getY(), p.getZ() - 3.0, p.getX() + 4.0, p.getY() + 6.5, p.getZ() + 4.0);
    }

    @Override
    public boolean shouldRenderOffScreen(PipeDrumBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(PipeDrumBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        GltfModelAsset asset = ModelRegistry.obtain(DRUM_MODEL);
        if (asset == null) return;

        RigHierarchy skeleton = asset.skeleton();
        int gen = ModelRegistry.generation();
        if (asset != cachedAsset || gen != cachedGen) {
            cachedAsset = asset;
            cachedGen = gen;
            if (palette.length < skeleton.jointCount()) {
                palette = new Matrix4f[skeleton.jointCount()];
                initPalette(palette);
            }
            if (poseState == null || poseState.translations.length < skeleton.nodeCount()) {
                poseState = new SkeletonPose.NodeTransformState(skeleton.nodeCount());
            }
            carouselBoneIdx = skeleton.findNodeIndex("carousel_drum_bone");
            for (int i = 0; i < PipeDrumBlockEntity.SLOT_COUNT; i++) {
                slotBoneIndices[i] = skeleton.findNodeIndex(SLOT_BONE_NAMES[i]);
            }
        }

        poseState.reset(skeleton);
        if (carouselBoneIdx >= 0) {
            float angleRad = (float) Math.toRadians(be.getCarouselAngleDeg(partialTick));
            scratchQuat.identity().rotateY(angleRad);
            poseState.rotations[carouselBoneIdx].set(scratchQuat);
        }
        for (int i = 0; i < PipeDrumBlockEntity.SLOT_COUNT; i++) {
            int idx = slotBoneIndices[i];
            if (idx >= 0) {
                float s = be.hasPipeInBone(i) ? 1.0f : 0.0f;
                poseState.scales[idx].set(s, s, s);
            }
        }

        SkeletonPose.computeJointPalette(skeleton, poseState, palette);

        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            light = Math.max(packedLight, LevelRenderer.getLightColor(level, be.getBlockPos().above(3)));
        }

        float rotDeg = 180.0f - be.getFeederFacing().toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
        GpuSkinningDispatcher.renderModel(asset, palette, poseStack, bufferSource, light, packedOverlay, 0xFFFFFFFF, null, true);
        poseStack.popPose();
    }
}
