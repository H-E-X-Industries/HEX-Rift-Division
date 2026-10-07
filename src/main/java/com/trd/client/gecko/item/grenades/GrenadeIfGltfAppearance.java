package com.trd.client.gecko.item.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.item.weapons.grenades.GrenadeIfClientAnim;
import com.trd.item.weapons.grenades.GrenadeIfItem;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.world.InteractionHand;
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
 * <p><b>Между анимациями предмет стоит в исходной позе модели.</b> Это и есть
 * состояние собранной гранаты в руке, и поднимать её оттуда не нужно: клип идёт
 * ровно на месте, для чего с клипа снят повтор смещения корневой кости (см.
 * {@link GrenadeIfVariants}), так что ни начала, ни конца анимации предмет никуда
 * не прыгает. Отсюда и клип {@code null} в покое — он и означает исходные позы.
 *
 * <p><b>Чека видна всегда, кроме рук.</b> Предмет показывает гранату собранной, и
 * её же выдёргивает {@code pin_pull} — но только в руке. Значок в инвентаре и в
 * хотбаре, а также граната на земле всегда рисуются собранными: см.
 * {@link #handOf}.
 *
 * <p>Центр смещения посчитан по вершинам модели в исходной позе: на -0.0 по X и
 * +0.1414 по Y. По X и Z корпус симметричен ровно — узел с мешем сдвигает его на
 * ровно половину ширины, — а вот вверх он смещён на 0.16, и без этого граната
 * висела бы над серединой куба.
 */
public class GrenadeIfGltfAppearance implements ItemAppearance {

    /**
     * Увеличение модели относительно её собственного размера.
     * <p>
     * Единица — модель в размере хитбокса, и она же единственно верная: при
     * большем увеличении граната не помещается в слот предмета, и в инвентаре
     * видно не её, а растянутый кусок текстуры — плоский спрайт. Увеличивать
     * имеет смысл только саму модель в Blockbench, а не её на экране.
     */
    private static final float MODEL_SCALE = 1.0F;

    /**
     * Насколько корпус поднят над началом координат модели, в тех же единицах.
     * <p>
     * Меш сидит на узле {@code main} со сдвигом 0.1603 вверх, вершины идут от
     * -0.1619 до +0.1241, и в готовой модели корпус занимает Y от -0.0016 до
     * +0.2844 — ровно до 0.1414 над началом координат.
     * <p>
     * Вычитается <b>после</b> масштаба: сдвиг остаётся в координатах модели и
     * уезжает вместе с ней, потому что PoseStack домножает матрицу справа, и
     * translate до scale превратился бы в 0.354 блока вместо 0.141.
     */
    private static final float MODEL_CENTER_Y = 0.1414F;

    /** Вид гранаты по предмету: своей текстурой у каждого вида своя. */
    private static GrenadeIfType typeOf(ItemStack stack) {
        return stack.getItem() instanceof GrenadeIfItem item ? item.getGrenadeType() : GrenadeIfType.GRENADE_IF;
    }

    /**
     * Рука, в которой рисуется предмет, либо {@code null}, если это не рука.
     * <p>
     * Один и тот же рендерер обслуживает все контексты отрисовки сразу: и руку, и
     * инвентарь, и хотбар, и гравитационный блок. Выдёргивание чеки — жест
     * игрока, а не свойство предмета, поэтому в хотбаре и в инвентаре значок
     * гранаты обязан стоять собранным: клип выбирается только по рукам, во всех
     * остальных контекстах возвращается {@code null}, то есть исходная поза
     * модели с чекой на месте.
     * <p>
     * Сравнения, а не {@code switch}: {@link ItemDisplayContext} у NeoForge
     * расширяемый, и значения, добавленные чужим модом, попали бы в ветку
     * «не рука» только при честном {@code default}.
     */
    private static InteractionHand handOf(@Nullable ItemDisplayContext context) {
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return InteractionHand.MAIN_HAND;
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        GemRenderGltfModel model = GrenadeIfVariants.model(typeOf(stack));
        if (model != null) {
            GrenadeIfClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public @Nullable GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        // Не в руке — всегда исходная поза модели: та же собранная граната, что и
        // в инвентаре, только без жеста.
        InteractionHand hand = handOf(context);
        if (hand == null || GrenadeIfClientAnim.current(hand) == null) {
            return null;
        }
        return GrenadeIfVariants.poseClip(typeOf(stack), GrenadeIfAnimation.PIN_PULL);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        InteractionHand hand = handOf(context);
        return hand == null || GrenadeIfClientAnim.current(hand) == null
                ? 0.0F
                : GrenadeIfClientAnim.seconds(hand, partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, -MODEL_CENTER_Y, 0.0F);
    }
}