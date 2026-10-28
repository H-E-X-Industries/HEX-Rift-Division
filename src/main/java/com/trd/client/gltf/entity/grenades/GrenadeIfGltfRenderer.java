package com.trd.client.gltf.entity.grenades;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gltf.item.grenades.GrenadeIfVariants;
import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Ударная граната в полёте на glTF через HexRender.
 * <p>
 * Чека скрывается автоматически через обнуление масштаба кости "pin".
 * Рендерится напрямую в MultiBufferSource через чистый ModelSkinningRenderer.
 */
public class GrenadeIfGltfRenderer extends EntityRenderer<GrenadeIfProjectileEntity> {

    private static final float MODEL_SCALE = 1.0F;
    private static final float MODEL_CENTER_Y = 0.23515F;
    private static final float SPIN_DEGREES_PER_SECOND = 220.0F;

    public GrenadeIfGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeIfProjectileEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(GrenadeIfProjectileEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(GrenadeIfProjectileEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.1F));
    }

    @Override
    public void render(GrenadeIfProjectileEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.isRemoved() || !entity.level().hasChunkAt(entity.blockPosition())) {
            return;
        }

        var type = GrenadeIfVariants.typeOf(entity);
        GltfModelAsset model = ModelRegistry.fetch(GrenadeIfVariants.modelIdOf(type));
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        // Центр хитбокса
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Плавная интерполяция поворотов (yaw, pitch, roll) от движения и отскоков
        float renderYaw = entity.getInterpolatedYaw(partialTick);
        float renderPitch = entity.getInterpolatedPitch(partialTick);
        float renderRoll = entity.getInterpolatedRoll(partialTick);

        poseStack.mulPose(Axis.YP.rotationDegrees(-renderYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(renderRoll));

        // Смещение центра вращения модели
        poseStack.translate(0.0F, -MODEL_CENTER_Y, 0.0F);

        Matrix4f[] palette = null;
        if (model.jointCount() > 0) {
            palette = model.newPalette();
            // Скрываем кость чеки "pin"
            model.evaluatePose(null, 0.0F, palette, "pin");
        }

        ModelSkinningRenderer.renderModel(
                model,
                palette,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                null
        );

        poseStack.popPose();
    }
}
