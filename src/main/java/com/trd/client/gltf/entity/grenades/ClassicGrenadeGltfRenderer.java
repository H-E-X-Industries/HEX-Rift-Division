package com.trd.client.gltf.entity.grenades;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.render.ModelSkinningRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gltf.item.grenades.ClassicGrenadeVariants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * Обычная граната в полёте на glTF через HexRender.
 * <p>
 * Чека скрывается автоматически через обнуление масштаба кости "pin".
 * Рендерится напрямую в MultiBufferSource через чистый ModelSkinningRenderer.
 *
 * @param <T> тип снаряда
 */
public class ClassicGrenadeGltfRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final float MODEL_SCALE = 1.0F;

    private static float modelCenterY(@Nullable ClassicGrenadeVariants.Variant variant) {
        return ClassicGrenadeVariants.centerY(variant);
    }

    private static final float SPIN_DEGREES_PER_SECOND = 220.0F;

    private static boolean spins(@Nullable ClassicGrenadeVariants.Variant variant) {
        return variant == ClassicGrenadeVariants.Variant.HYDROGEN;
    }

    private static float flightSeconds(Entity entity, float partialTick) {
        return (entity.tickCount + partialTick) / 20.0F;
    }

    public ClassicGrenadeGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(T entity) {
        return false;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.1F));
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.isRemoved() || !entity.level().hasChunkAt(entity.blockPosition())) {
            return;
        }

        var variant = ClassicGrenadeVariants.variantOf(entity);
        GltfModelAsset model = ModelRegistry.fetch(ClassicGrenadeVariants.modelIdOf(variant));
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        // Центр хитбокса
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Плавная интерполяция поворотов (yaw, pitch, roll) от движения и отскоков
        float renderYaw;
        float renderPitch;
        float renderRoll;

        if (entity instanceof com.trd.entity.weapons.grenades.AbstractGrenadeEntity grenade) {
            renderYaw = grenade.getInterpolatedYaw(partialTick);
            renderPitch = grenade.getInterpolatedPitch(partialTick);
            renderRoll = grenade.getInterpolatedRoll(partialTick);
        } else {
            renderYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            renderPitch = net.minecraft.util.Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            renderRoll = flightSeconds(entity, partialTick) * SPIN_DEGREES_PER_SECOND;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-renderYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(renderRoll));

        // Смещение центра вращения модели
        poseStack.translate(0.0F, -modelCenterY(variant), 0.0F);

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
