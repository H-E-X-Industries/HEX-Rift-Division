package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class TurretBulletRenderer extends GeoEntityRenderer<TurretBulletEntity> {

    /** Полностью освещённый свет для аддитивного слоя свечения. */
    private static final int FULL_BRIGHT = 15728880;
    private static final int WHITE = 0xFFFFFFFF;

    public TurretBulletRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TurretBulletModel());
        addRenderLayer(new TurretBulletGlowLayer(this));
    }

    @Override
    protected void applyRotations(TurretBulletEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(animatable.spin));
    }

    /**
     * Слой свечения: текстура выбирается так же, как у основной модели.
     */
    public static class TurretBulletGlowLayer extends GeoRenderLayer<TurretBulletEntity> {

        private static final String ITEM_PREFIX = "ammo_turret";
        private static final String BULLET_PREFIX = "turret_bullet";

        public TurretBulletGlowLayer(GeoEntityRenderer<TurretBulletEntity> entityRenderer) {
            super(entityRenderer);
        }

        @Override
        public void render(PoseStack poseStack, TurretBulletEntity entity, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            ResourceLocation glowTexture = ResourceLocation.fromNamespaceAndPath(
                    MainRegistry.MOD_ID, "textures/entity/" + glowTextureName(entity) + ".png");
            RenderType glowRenderType = RenderType.eyes(glowTexture);

            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, entity,
                    glowRenderType, bufferSource.getBuffer(glowRenderType),
                    partialTick, FULL_BRIGHT, packedOverlay, WHITE);
        }

        private static String glowTextureName(TurretBulletEntity entity) {
            String path = entity.getAmmoId();
            if (path.contains(":")) {
                path = path.substring(path.indexOf(':') + 1);
            }

            if (path.startsWith(ITEM_PREFIX)) {
                String suffix = path.replace(ITEM_PREFIX, "");
                if (!suffix.isEmpty()) return BULLET_PREFIX + suffix + "_glow";
            } else if (path.contains("piercing")) {
                return BULLET_PREFIX + "_piercing_glow";
            }

            return BULLET_PREFIX + "_glow";
        }
    }
}
