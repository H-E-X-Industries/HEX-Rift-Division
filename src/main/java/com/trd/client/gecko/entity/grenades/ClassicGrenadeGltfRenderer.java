package com.trd.client.gecko.entity.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gecko.item.grenades.ClassicGrenadeVariants;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Обычная граната в полёте на glTF через GemRender.
 *
 * <p>Та же модель и та же текстура, что и у предмета в руке, но с одной поправкой:
 * <b>чеки здесь нет</b>. Летящей гранате она уже не нужна — чеку выдернули при
 * броске, и обратно она не вернётся. Прячется кость через
 * {@link ClassicGrenadeVariants#hiddenPin}.
 *
 * <p>Рендер идёт через {@link DirectPass#LEVEL} — тот же путь, что у ударной
 * гранаты, пули и гильзы.
 *
 * <p><b>Временная мера.</b> Все семь обычных гранат летят на геометрии ударной,
 * пока у них не будет своих моделей. См. {@link ClassicGrenadeVariants}.
 *
 * @param <T> тип снаряда: обычная, водородная и гравитационная гранаты — разные
 *            классы, но рисуются одинаково
 */
public class ClassicGrenadeGltfRenderer<T extends Entity> extends EntityRenderer<T> {

    /**
     * Увеличение модели относительно её собственного размера.
     * <p>
     * Единица — модель в размере хитбокса, и она же единственно верная: считать
     * масштаб от хитбокса нельзя, у снарядов разные размеры (0.25 у водородной и
     * 0.5 у остальных), и одна и та же цифра либо уводила бы гранату наполовину
     * в блок под собой, либо болтала её в верхней половине хитбокса.
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * Где в модели стоит её середина, в единицах блока.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом вверх, и в готовой модели корпус
     * занимает Y примерно от -0.002 до +0.284. По X и Z корпус симметричен ровно,
     * а вот вверх он смещён, и без этого граната висела бы над серединой хитбокса.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа.
     */
    private static final float MODEL_CENTER_Y = 0.1414F;

    public ClassicGrenadeGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        // Текстура живёт внутри glTF, и её GemRender достаёт из файла модели по
        // виду гранаты. Своей у рендерера нет.
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
        var model = ClassicGrenadeVariants.model(variant);
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        // Центр хитбокса — до масштаба: перенос после scale домножается им же.
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, -MODEL_CENTER_Y, 0.0F);

        // Граната летит туда, куда смотрит бросивший, — углы приходят с сервера
        // вместе с позицией, ровно как их использует ванильный рендерер брошенного
        // предмета. Поворота вокруг оси полёта нет намеренно: проворот делался
        // фиксированным, и на сильном броске граната крутилась как вертолёт, а
        // на слабом — одинаково. Теперь она летит стабильно, носом по вектору
        // скорости, и зарядка влияет ровно на одно: на саму скорость.
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));

        // Поза покоя модели, но с прибитой чекой: клип отдаётся обязательно, иначе
        // NodeHide не применится и чека останется видимой. Вид берётся из типа
        // сущности, а не из синхронизированных данных снаряда — см.
        // ClassicGrenadeVariants#variantOf.
        DirectRenderer.submit(model, ClassicGrenadeVariants.hiddenPin(variant), 0.0F,
                poseStack.last().pose(), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                DirectPass.LEVEL);

        poseStack.popPose();
    }
}