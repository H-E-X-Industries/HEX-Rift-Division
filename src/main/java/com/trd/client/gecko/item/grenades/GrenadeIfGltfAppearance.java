package com.trd.client.gecko.item.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.item.weapons.grenades.GrenadeIfClientAnim;
import com.trd.item.weapons.grenades.GrenadeIfItem;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Облик ударной гранаты в руке на glTF вместо геколибовской geo-модели.
 *
 * <p>Модель и клип живут в {@code assets/trd/models/item/grenade_if.gltf}: там же
 * зашита анимация {@code pin_pull}. Какой кадр показывать — решает
 * {@link GrenadeIfClientAnim}.
 *
 * <p><b>Чека видна</b>, и между анимациями предмет стоит на последнем кадре её
 * выдергивания: возвращать его в исходную позу модели означало бы на глазах у
 * игрока вдвигать чеку обратно. Отсюда клип уходит всегда, а не {@code null}:
 * {@code null} у GemRender — это поза покоя, то есть ровно та, которой мы
 * избегаем.
 *
 * <p>Центр смещения посчитан по вершинам модели в показываемой позе, а не
 * подобран на глаз: на последнем кадре {@code pin_pull} он уезжает на -0.0134 по
 * X и +0.1327 по Y. По исходной позе было бы 0 и +0.1414, то есть граната
 * уходила бы вбок и вниз.
 */
public class GrenadeIfGltfAppearance implements ItemAppearance {

    /**
     * Увеличение модели относительно её собственного размера.
     * <p>
     * Два с половиной раза — это около 0.5 блока по ширине и 0.75 по высоте:
     * граната уверенно читается в руке и от первого лица, но не занимает весь
     * куб, как это делает плоский спрайт. Число подбирается на глаз, единица —
     * модель в размере хитбокса.
     */
    private static final float MODEL_SCALE = 1F;

    /**
     * Где в модели стоит её середина в показываемой позе, в единицах блока.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом 0.1603 вверх, вершины идут от
     * -0.1619 до +0.1241, и в готовой модели корпус занимает Y от -0.0016 до
     * +0.2844. Последний кадр выдергивания чеки доверяет корпус на 10° вокруг
     * шарнира у самого верха, и вместе с чекой смещение уезжает ещё и вбок.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа, и
     * translate до scale превратился бы в 0.332 блока вместо 0.133.
     */
    private static final float MODEL_CENTER_X = 0.0134F;
    private static final float MODEL_CENTER_Y = 0.1327F;

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        GemRenderGltfModel model = GrenadeIfVariants.model();
        if (model != null) {
            GrenadeIfClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public @Nullable GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        return GrenadeIfVariants.poseClip(GrenadeIfAnimation.PIN_PULL);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        if (GrenadeIfClientAnim.current() != null) {
            return GrenadeIfClientAnim.seconds(partialTick);
        }
        return GrenadeIfVariants.endSeconds(clip(stack, context));
    }

    @Override
    public VariantUv variant(ItemStack stack, ItemDisplayContext context) {
        return stack.getItem() instanceof GrenadeIfItem item
                ? GrenadeIfVariants.variant(item.getGrenadeType())
                : VariantUv.NONE;
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(-MODEL_CENTER_X, -MODEL_CENTER_Y, 0.0F);
    }
}