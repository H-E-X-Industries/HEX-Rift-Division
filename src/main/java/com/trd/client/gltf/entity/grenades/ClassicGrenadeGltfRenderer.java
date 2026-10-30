package com.trd.client.gltf.entity.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gltf.item.grenades.ClassicGrenadeVariants;
import com.hex.hexrender.direct.DirectPass;
import com.hex.hexrender.direct.HexDirectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Обычная граната в полёте на glTF через HexRender.
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
     * Величина своя у каждого вида, потому что своя геометрия есть не у всех:
     * шесть видов летят на общей и делят одну центровку, а у водородной модель
     * отдельная. См. {@link ClassicGrenadeVariants#centerY}.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа.
     */
    private static float modelCenterY(@Nullable ClassicGrenadeVariants.Variant variant) {
        return ClassicGrenadeVariants.centerY(variant);
    }

    /**
     * Оборотов в секунду вокруг оси полёта.
     * <p>
     * Взято у ударной гранаты — {@code GrenadeIfGltfRenderer#SPIN_DEGREES_PER_SECOND}
     * — чтобы водородная проворачивалась ровно так же. Без вращения граната летит
     * задом наперёд и не читается как летящая.
     */
    private static final float SPIN_DEGREES_PER_SECOND = 220.0F;

    /**
     * Проворачивается ли вид в полёте.
     * <p>
     * Только водородная: у неё своя геометрия, и она крупнее остальных, так что
     * без проворота её силуэт скользит мимо цели, не показывая, куда она летит.
     */
    private static boolean spins(@Nullable ClassicGrenadeVariants.Variant variant) {
        return variant == ClassicGrenadeVariants.Variant.HYDROGEN;
    }

    /**
     * Сколько секунд граната уже летит, сглаженно.
     * <p>
     * Дробная часть кадра обязательна: на низком FPS целочисленный
     * {@code tickCount} прыгал бы между тиками, и проворот шёл бы рывками.
     */
    private static float flightSeconds(Entity entity, float partialTick) {
        return (entity.tickCount + partialTick) / 20.0F;
    }

    public ClassicGrenadeGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        // Текстура живёт внутри glTF, и её HEX Render достаёт из файла модели по
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

        // Смещение центра вращения модели: применяется ПОСЛЕ вращения в PoseStack
        // (то есть ДО вращения вершин модели), чтобы модель крутилась строго вокруг заданной оси.
        poseStack.translate(0.0F, -modelCenterY(variant), 0.0F);

        if (com.hex.hexrender.direct.HexIrisBridge.hasActiveShaders()) {
            com.hex.hexrender.direct.HexVanillaRenderer.renderVanilla(
                    model,
                    ClassicGrenadeVariants.hiddenPin(variant),
                    0.0F,
                    poseStack,
                    bufferSource,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF,
                    com.hex.hexrender.texture.VariantUv.NONE,
                    com.hex.hexrender.direct.TextureSlots.NONE);
        } else {
            HexDirectRenderer.renderModel(model, ClassicGrenadeVariants.hiddenPin(variant), 0.0F,
                    poseStack.last().pose(), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.LEVEL);
        }

        poseStack.popPose();
    }
}
