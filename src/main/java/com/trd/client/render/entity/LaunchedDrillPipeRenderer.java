package com.trd.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.entity.industrial.drilling.LaunchedDrillPipeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Рендерер вылетевшей буровой трубы (drill_pipe.obj).
 */
public class LaunchedDrillPipeRenderer extends EntityRenderer<LaunchedDrillPipeEntity> {

    public LaunchedDrillPipeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.45f;
    }

    @Override
    public boolean shouldRender(LaunchedDrillPipeEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return frustum.isVisible(entity.getBoundingBox().inflate(2.2));
    }

    @Override
    public void render(LaunchedDrillPipeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0f, entity.getBbHeight() * 0.5f, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
        poseStack.translate(0.0f, -2.0f, 0.0f);
        FlywheelFallbackHelper.renderPartialModel(
                ModModels.DRILL_PIPE, poseStack, bufferSource, null, packedLight, OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LaunchedDrillPipeEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
