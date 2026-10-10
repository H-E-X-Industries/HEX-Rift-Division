package com.trd.client.gecko.item.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.item.weapons.grenades.GrenadeIfClientAnim;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Облик гранаты в руке на glTF вместо геколибовской geo-модели.
 *
 * <p>Общий для всех видов: геометрия у них одна и та же, а различаются они только
 * тем, какую текстуру зашили в свой файл модели. Поэтому класс ничего не знает ни
 * об одном конкретном виде — ему дают функцию «предмет → файл модели», и он сам
 * решает только, какой кадр анимации показывать.
 *
 * <p>Какой это файл — задаёт подкласс: {@link GrenadeIfGltfAppearance} для ударных,
 * {@code ClassicGrenadeGltfAppearance} для обычных. Сама механика загрузки и
 * поправок к клипам живёт в {@link GrenadeGltfModels}.
 *
 * <p><b>Между анимациями предмет стоит в исходной позе модели.</b> Это и есть
 * состояние собранной гранаты в руке, и поднимать её оттуда не нужно: клип идёт
 * ровно на месте, для чего с клипа снят повтор смещения корневой кости (см.
 * {@link GrenadeGltfModels}), так что ни начала, ни конца анимации предмет никуда
 * не прыгает. Отсюда и клип {@code null} в покое — он и означает исходные позы.
 *
 * <p><b>Чека видна всегда, кроме рук.</b> Предмет показывает гранату собранной, и
 * её же выдёргивает {@code pin_pull} — но только в руке. Значок в инвентаре и в
 * хотбаре, а также граната на земле всегда рисуются собранными: см.
 * {@link #handOf}.
 */
public class GrenadeGltfAppearance implements ItemAppearance {

    /** Предмет, ради которого рисуется модель. */
    private final Function<Item, ResourceLocation> modelId;

    /**
     * @param modelId файл модели по предмету; для предмета вне набора гранат
     *                значение не важно, но должно быть не {@code null}
     */
    public GrenadeGltfAppearance(Function<Item, ResourceLocation> modelId) {
        this.modelId = modelId;
    }

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
     * Насколько предмет уходит вниз при жесте «взял новую гранату», в блоках.
     * <p>
     * Ровно на столько же, на сколько ванильный рендерер уводит предмет при
     * смене слота, — так жест выглядит привычно и не выглядит как отдельное
     * изобретение.
     */
    private static final float REQUIP_DROP = 0.6F;

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
        GemRenderGltfModel model = GrenadeGltfModels.model(modelId.apply(stack.getItem()));
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
        return GrenadeGltfModels.poseClip(modelId.apply(stack.getItem()), GrenadeIfAnimation.PIN_PULL);
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
        // Жест «взял новую гранату» — только в руке. Сдвиг идёт до всего остального:
        // PoseStack домножает матрицу справа, и трансляция под масштабом съедается
        // масштабом вместе с блоком display.
        float requip = GrenadeIfClientAnim.requip(handOf(context));
        if (requip > 0.0F) {
            poseStack.translate(0.0F, -REQUIP_DROP * requip, 0.0F);
        }

        // Больше тут ничего: положение и размер предмета задаёт блок display в
        // models/item/<вид>.json, который ваниль применяет сама ещё до вызова
        // рендерера. Своей центровки у предмета нет намеренно — модель стоит ровно
        // там, куда её поставил Blockbench, и любой сдвиг здесь складывался бы с
        // блоком display вместо того, чтобы его заменять.
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}