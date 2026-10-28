package com.trd.client.gltf.entity.bullets;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.GilseEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Гильза на glTF через HexRender.
 * <p>
 * Модель статичная, поэтому клипов нет и передаём null: HEX Render
 * берёт позу покоя. Рендерится через чистый ModelSkinningRenderer прямо в MultiBufferSource.
 */
public class GilseRenderer extends EntityRenderer<GilseEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID,
                    "models/entity/gilse.gltf");

    /**
     * Увеличение модели относительно исходного размера.
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * Сдвиг модели вдоль собственной оси цилиндра, в единицах до MODEL_SCALE.
     */
    private static final float MODEL_AXIS_OFFSET = 0.01875F;

    public GilseRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GilseEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(GilseEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(GilseEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.1F));
    }

    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(GilseEntity entity, float partialTick) {
        return entity.renderOffset(partialTick);
    }

    @Override
    public void render(GilseEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        GltfModelAsset model = ModelRegistry.fetch(MODEL);
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, 0.0F, -MODEL_AXIS_OFFSET);

        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick) - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderRoll(partialTick)));

        ModelSkinningRenderer.renderModel(
                model,
                null,
                poseStack,
                bufferSource,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                null
        );

        poseStack.popPose();
    }
}
