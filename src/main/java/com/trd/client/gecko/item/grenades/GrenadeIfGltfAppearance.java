package com.trd.client.gecko.item.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * <p><b>Чека видна.</b> Именно её и выдёргивает {@code pin_pull}, так что предмет
 * показывает гранату собранной и готовую к броску. Прятать её надо не здесь, а у
 * летящей сущности — см. {@link com.trd.client.gecko.entity.grenades.GrenadeIfGltfRenderer}.
 *
 * <p><b>Пока анимация не идёт, клип не передаётся вовсе</b>: {@code null} у
 * GemRender означает позу покоя, а это ровно то, что нужно собранной гранате в руке.
 * Ставить рядом клип на нулевой секунде было бы тем же самым, только дороже.
 *
 * <p>Геометрия модели задана в единицах хитбокса предмета: корпус занимает
 * примерно 0.19 блока по ширину и 0.286 по высоте и стоит выше начала координат,
 * так что без {@link #MODEL_SCALE} граната выглядела бы втрое меньше обычного
 * спрайта, а с центрированием по нулю ещё и висела бы над серединой куба.
 */
public class GrenadeIfGltfAppearance implements ItemAppearance {

    /**
     * Увеличение модели относительно её собственного размера.
     * <p>
     * Два с половиной раза — это около 0.5 блока по ширине и 0.7 по высоте:
     * граната уверенно читается в руке и от первого лица, но не занимает весь
     * куб, как это делает плоский спрайт. Число подбирается на глаз, единица —
     * модель в размере хитбокса.
     */
    private static final float MODEL_SCALE = 1F;

    /**
     * Насколько корпус поднят над началом координат модели, в тех же единицах.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом 0.16 вверх, а вершины идут от
     * -0.16 до +0.12, то есть в готовой модели корпус занимает Y от 0 до 0.284.
     * Ровно его середина и сдвигается вниз, иначе граната висит над центром
     * куба, куда её поставил рендерер GemRender.
     * <p>
     * По X и Z смещения нет: узел сдвигает корпус на -0.0125, то есть ровно на
     * половину его ширины, и по этим осям он уже симметричен.
     * <p>
     * Сдвиг задан <b>после</b> масштаба, поэтому остаётся в координатах модели и
     * уезжает вместе с ней: PoseStack домножает матрицу справа, и translate
     * до scale превратился бы в 0.355 блока вместо 0.142.
     */
    private static final float MODEL_CENTER_Y = 0.142F;

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
        String current = GrenadeIfClientAnim.current();
        if (current == null) {
            return null;
        }
        GemRenderGltfModel model = GrenadeIfVariants.model();
        return model != null ? model.animation(current) : null;
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        return GrenadeIfClientAnim.current() == null ? 0.0F : GrenadeIfClientAnim.seconds(partialTick);
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
        poseStack.translate(0.0F, -MODEL_CENTER_Y, 0.0F);
    }
}