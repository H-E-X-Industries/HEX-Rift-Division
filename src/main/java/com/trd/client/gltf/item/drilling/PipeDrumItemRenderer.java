package com.trd.client.gltf.item.drilling;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.hex.hexrender.skeleton.RigHierarchy;
import com.hex.hexrender.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.client.gltf.blockentity.drilling.PipeDrumRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Предметный BEWLR для барабана буровых труб (GUI/рука/земля).
 * Скрывает 6 костей труб (slot_pipe_1..6), переиспользуя буферы скелета без аллокаций на кадр.
 */
public class PipeDrumItemRenderer extends BlockEntityWithoutLevelRenderer {

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
    private final int[] slotBoneIndices = {-1, -1, -1, -1, -1, -1};

    public PipeDrumItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        initPalette(palette);
    }

    private static void initPalette(Matrix4f[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Matrix4f();
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        GltfModelAsset asset = ModelRegistry.obtain(PipeDrumRenderer.DRUM_MODEL);
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
            for (int i = 0; i < 6; i++) {
                slotBoneIndices[i] = skeleton.findNodeIndex(SLOT_BONE_NAMES[i]);
            }
        }

        poseState.reset(skeleton);
        for (int idx : slotBoneIndices) {
            if (idx >= 0) {
                poseState.scales[idx].set(0.0f, 0.0f, 0.0f);
            }
        }
        SkeletonPose.computeJointPalette(skeleton, poseState, palette);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.0f, 0.0f);
        ModelSkinningRenderer.renderModel(asset, palette, poseStack, bufferSource, packedLight, packedOverlay, 0xFFFFFFFF, null);
        poseStack.popPose();
    }
}
