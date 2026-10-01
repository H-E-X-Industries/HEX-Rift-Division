package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.GilseEntity;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Гильза на glTF через GemRender.
 * <p>
 * Модель статичная, поэтому клипов нет и передаём {@code null}: GemRender
 * берёт позу покоя. Рендер идёт через {@link DirectPass#LEVEL}, тот же путь,
 * что и у пули.
 */
public class GilseRenderer extends EntityRenderer<GilseEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID,
                    "models/entity/gilse.gltf");

    /**
     * Увеличение модели вдвое относительно исходного размера.
     * <p>
     * Размер сущности ({@code EntityType#sized}) задаёт только хитбокс, на
     * геометрию glTF он не влияет, поэтому масштаб ставится здесь. Значение
     * согласовано с {@code ModEntities.GILSE}: хитбокс 0.2, модель выросла
     * пропорционально.
     */
    private static final float MODEL_SCALE = 2.0F;

    public GilseRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GilseEntity entity) {
        // Текстура живёт внутри glTF и подставляется GemRender'ом.
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
    public void render(GilseEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        var model = GemRenderModels.get(MODEL);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Поворот вдоль собственной скорости гильзы плюс прокрутка вокруг оси
        // полёта. Именно эти углы приезжают с сервера в SynchedEntityData, а не
        // берутся из getDeltaMovement(), как у пули: скорость гильзы клиенту не
        // синхронизируется, и посчитанный на клиенте угол смотрел бы не туда.
        // Углы интерполируются между тиками, иначе гильза дёргалась бы раз в
        // 20 мс.
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick) - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderSpin(partialTick)));

        var matrix = poseStack.last().pose();
        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                matrix, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, DirectPass.LEVEL,
                com.wf.gemrender.texture.VariantUv.NONE);

        poseStack.popPose();
    }
}