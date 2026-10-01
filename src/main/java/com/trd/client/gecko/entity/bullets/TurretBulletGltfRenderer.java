package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Пуля на glTF через GemRender.
 * <p>
 * Разворот задаётся так же, как был у геколибовского рендерера: сначала yaw,
 * потом pitch, потом собственное вращение пули вокруг оси полёта. Углы берутся
 * с интерполяцией между прошлым и текущим тиком, иначе на высоком FPS пуля
 * дёргалась бы между тиками.
 */
public class TurretBulletGltfRenderer extends EntityRenderer<TurretBulletEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet.gltf");

    public TurretBulletGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TurretBulletEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(TurretBulletEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(TurretBulletEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.2F));
    }

    @Override
    public void render(TurretBulletEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        var model = GemRenderModels.get(MODEL);
        if (model == null) {
            return;
        }

        // Направление берём из вектора полёта, а не из углов сущности. Углы приходят
        // отдельным пакетом поворота и на клиенте отстают от позиции, из-за
        // чего ствол пули метался, хотя сама пуля летела ровно.
        Vec3 dir = entity.flightDirection();
        if (dir.lengthSqr() < 1.0E-8D) {
            // Скорости ещё нет — fallback на углы сущности, иначе пуля не видна.
            dir = entity.getDeltaMovement();
            if (dir.lengthSqr() < 1.0E-8D) {
                return;
            }
        }

        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yawDeg = (float) (Math.atan2(dir.x, dir.z) * (180.0D / Math.PI));
        float pitchDeg = (float) (Math.atan2(dir.y, horizontal) * (180.0D / Math.PI));

        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        // Собственное вращение пули вокруг оси полёта.
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.spin));

        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                poseStack.last().pose(), packedLight, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, DirectPass.LEVEL, VariantUv.NONE);
    }
}