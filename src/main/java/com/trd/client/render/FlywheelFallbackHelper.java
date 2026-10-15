package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FlywheelFallbackHelper {

    public static boolean isFlywheelActive(@Nullable Level level) {
        if (com.wf.gemrender.direct.IrisDirectBridge.isRenderingShadows()) {
            return false;
        }
        try {
            return BackendManager.isBackendOn() && VisualizationManager.supportsVisualization(level);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void renderPartialModel(@Nullable PartialModel partialModel, PoseStack poseStack,
                                          MultiBufferSource buffer, @Nullable BlockState state,
                                          int packedLight, int packedOverlay) {
        if (partialModel == null) return;
        renderBakedModel(partialModel.get(), poseStack, buffer, state, packedLight, packedOverlay);
    }

    public static void renderBakedModel(@Nullable BakedModel model, PoseStack poseStack,
                                        MultiBufferSource buffer, @Nullable BlockState state,
                                        int packedLight, int packedOverlay) {
        if (model == null) return;
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                state,
                model,
                1.0F, 1.0F, 1.0F,
                packedLight,
                packedOverlay
        );
    }
}
