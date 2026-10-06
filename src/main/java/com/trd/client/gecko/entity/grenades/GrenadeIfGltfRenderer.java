package com.trd.client.gecko.entity.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.gecko.item.grenades.GrenadeIfVariants;
import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
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
     * Единица — модель в размере хитбокса. При ней корпус занимает 0.19 блока по
     * ширине и 0.286 по высоте, то есть чуть уже хитбокса ударной и липучей (0.25)
     * и намного уже хитбокса фугасной и зажигательной (0.5). Разница хитов у видов
     * от радиуса взрыва, а рисунок у них общий — поэтому масштаб один, и
     * зажигательная граната не выглядит вдвое крупнее осколочной.
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * Где в модели стоит её середина, в единицах блока.
     * <p>
     * Взято из Blockbench: -0.2 по X и 1.41 по Y в его сетке, где блок занимает
     * десять единиц, — то есть -0.02 и +0.141 блока от начала координат. Z не
     * смещён: узел {@code main} двигает корпус по нему на -0.00028, а по
     * ширине корпус и так симметричен.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом 0.160268 вверх, вершины идут от
     * -0.161886 до +0.124107, и в готовой модели корпус занимает Y от -0.0016 до
     * +0.2844 — середина оттуда и есть 0.1414.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа.
     */
    private static final float MODEL_CENTER_X = -0.02F;
    private static final float MODEL_CENTER_Y = 0.1414F;

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

        var model = GrenadeIfVariants.model();
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        // Центр хитбокса — до масштаба: перенос после scale домножается им же.
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(-MODEL_CENTER_X, -MODEL_CENTER_Y, 0.0F);

        // Граната летит туда, куда смотрит бросивший, — углы приходят с сервера
        // вместе с позицией, ровно как их использует ванильный рендерер брошенного
        // предмета.
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));

        // Вращение вокруг оси полёта. Возраст берётся сглаженным, иначе на низком
        // FPS граната дёргалась бы между тиками.
        float spin = (entity.tickCount + partialTick) * SPIN_DEGREES_PER_SECOND * 0.05F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));

        // Поза покоя модели, но с прибитой чекой: клип отдаётся обязательно, иначе
        // NodeHide не применится и чека останется видимой. Тип берётся из типа
        // сущности, а не из синхронизированных данных снаряда — см.
        // GrenadeIfType#typeOf.
        DirectRenderer.submit(model, GrenadeIfVariants.hiddenPin(), 0.0F,
                poseStack.last().pose(), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                DirectPass.LEVEL, GrenadeIfVariants.variant(GrenadeIfType.typeOf(entity.getType())));

        poseStack.popPose();
    }
}