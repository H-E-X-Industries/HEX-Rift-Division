package com.trd.client.gltf.entity.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gltf.item.grenades.GrenadeIfVariants;
import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.hex.hexrender.direct.DirectPass;
import com.hex.hexrender.direct.DirectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Ударная граната в полёте на glTF через GemRender.
 *
 * <p>Та же модель и та же текстура, что и у предмета в руке, но с одной
 * поправкой: <b>чеки здесь нет</b>. Летящей гранате она уже не нужна — чеку
 * выдернули при броске, и обратно она не вернётся. Прячется кость через
 * {@link GrenadeIfVariants#hiddenPin}.
 *
 * <p>Поза — исходная, а не последний кадр {@code pin_pull}: в конце клипа у кости
 * {@code pin} стоит ключ {@code scale = [0, 1, 0]}, то есть чека сплющивается
 * сама, и летящая граната всё равно осталась бы с видимой чекой на месте. Исходная
 * поза к тому же единственная симметричная — в конце клипа корпус довёрнут на 10°
 * вокруг шарнира у самого верха, и центрировать пришлось бы по наклонному силуэту.
 *
 * <p>Рендер идёт через {@link DirectPass#LEVEL} — тот же путь, что у пули и гильзы.
 */
public class GrenadeIfGltfRenderer extends EntityRenderer<GrenadeIfProjectileEntity> {

/**
     * Увеличение модели относительно её собственного размера.
     * <p>
     * Единица — модель в размере хитбокса, и она же единственно верная. Считать
     * масштаб от хитбокса, как было раньше, смысла не было: у видов разные
     * хитбоксы (0.25 у ударной и липучей, 0.5 у фугасной и зажигательной), и одна
     * и та же цифра либо уводила гранату наполовину в блок под собой, либо
     * болтала её в верхней половине хитбокса. Размер теперь задаёт сама модель.
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * Где в модели стоит ось вращения, в единицах блока.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом 0.1603 вверх, вершины идут от
     * -0.1619 до +0.1241, и в готовой модели корпус занимает Y от -0.0016 до
     * +0.2844 — по X и Z он симметричен ровно, узел сдвигает его на половину
     * ширины. Геометрическая середина отсюда — 0.1414, и именно она стояла здесь
     * раньше.
     * <p>
     * Но визуально граната при полёте крутилась вокруг точки заметно ниже
     * середины, из-за чего подпрыгивала на обороте и дёргалась. Ось поднята на
     * 1.5 пикселя, то есть на 1.5/16 = 0.09375 блока, и теперь стоит выше
     * геометрической середины намеренно.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа.
     */
    private static final float MODEL_CENTER_Y = 0.23515F;

    /**
     * Оборотов в секунду вокруг собственной оси.
     * <p>
     * Граната в полёте крутится, и без этого она летела бы задом наперёд и не
     * читалась бы как летящая. Точная физика вращения из броска не
     * восстанавливается, да и не нужна: у ударной гранаты форма почти
     * осесимметричная, важно лишь что кадр меняется.
     */
    private static final float SPIN_DEGREES_PER_SECOND = 220.0F;

    public GrenadeIfGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeIfProjectileEntity entity) {
        // Текстура живёт внутри glTF и подставляется GemRender'ом из атласа
        // вариантов по типу гранаты.
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
        var model = GrenadeIfVariants.model(type);
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

        // Смещение центра вращения модели: применяется ПОСЛЕ вращения в PoseStack
        // (то есть ДО вращения вершин модели), чтобы модель крутилась строго вокруг заданной оси.
        poseStack.translate(0.0F, -MODEL_CENTER_Y, 0.0F);

        if (com.hex.hexrender.direct.IrisDirectBridge.isShaderPackInUse()) {
            com.hex.hexrender.direct.GltfVanillaRenderer.render(
                    model,
                    GrenadeIfVariants.hiddenPin(type),
                    0.0F,
                    poseStack,
                    bufferSource,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF,
                    com.hex.hexrender.texture.VariantUv.NONE,
                    com.hex.hexrender.direct.TextureSlots.NONE);
        } else {
            DirectRenderer.submit(model, GrenadeIfVariants.hiddenPin(type), 0.0F,
                    poseStack.last().pose(), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.LEVEL);
        }

        poseStack.popPose();
    }
}
