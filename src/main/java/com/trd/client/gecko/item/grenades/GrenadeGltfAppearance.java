package com.trd.client.gecko.item.grenades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.item.weapons.grenades.ChargableGrenadeItem;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.item.weapons.grenades.GrenadeIfClientAnim;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
     * Амплитуда качания при полном заряде, в градусах.
     * <p>
     * Качание вокруг собственного центра гранаты, а не натяжение. Ванильный
     * {@code UseAnim.BOW} на зарядке растягивал модель по горизонтали почти в
     * 1.75 раза и уводил её из руки — выглядело это так, будто граната раздувается
     * резиновой. Здесь её только слегка покачивает, и чем ближе полный заряд, тем
     * заметнее, так что стадия зарядки всё ещё читается по руке.
     */
    private static final float CHARGE_SWAY_DEGREES = 14.0F;

    /**
     * Насколько качание по главной оси слабее поперечного, в долях.
     * <p>
     * Две оси с разной частотой дают не колебание маятника, а именно дрожь: если
     * бы они шли в такт, предмет просто наклонялся бы туда-обратно и выглядел бы
     * маятником на ниточке.
     */
    private static final float CROSS_AXIS_RATIO = 0.45F;

    /** Скорость качания, радиан на тик. */
    private static final float CHARGE_FREQUENCY = 0.62F;

    /**
     * Насколько предмет уходит вниз при жесте «взял новую гранату», в блоках.
     * <p>
     * Ровно на столько же, на сколько ванильный рендерер уводит предмет при
     * смене слота, — так жест выглядит привычно и не выглядит как отдельное
     * изобретение.
     */
    private static final float REQUIP_DROP = 0.6F;

    /**
     * Степень заряда этой стопки, от 0 до 1, либо 0 если предмет сейчас не
     * заряжается.
     * <p>
     * Считается по тому же, что и полоска заряда на экране, — из
     * {@link ChargableGrenadeItem#MAX_CHARGE_TICKS} и времени удержания, а не из
     * остатка использования: он считается от 72000 и для заряда бесполезен.
     * <p>
     * Предмет сверяется по типу, а не по самой стопке: рендер получает ту же
     * стопку, но сравнение по ссылке на руке не годится — стопка в руке и
     * отрисованная это разные объекты. Вне рук заряд не читается вовсе: там у
     * предмета нет и не может быть заряда, а качание началось бы из инвентаря.
     */
    private static float chargeOf(ItemStack stack, @Nullable ItemDisplayContext context) {
        if (handOf(context) == null) {
            return 0.0F;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) {
            return 0.0F;
        }
        if (player.getUseItem().getItem() != stack.getItem()) {
            return 0.0F;
        }

        return Math.min(1.0F, player.getTicksUsingItem() / (float) ChargableGrenadeItem.MAX_CHARGE_TICKS);
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

        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Зарядка: только качание вокруг собственного центра, без сдвига и без
        // масштаба. Поворот стоит после scale, а не до, чтобы шатание шло вокруг
        // центра модели, а не вокруг точки, от которой её двигает блок display.
        float charge = chargeOf(stack, context);
        if (charge > 0.0F) {
            LocalPlayer player = Minecraft.getInstance().player;
            float phase = player.getTicksUsingItem() * CHARGE_FREQUENCY;
            float sway = CHARGE_SWAY_DEGREES * charge;
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase) * sway));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(phase * 1.6F) * sway * CROSS_AXIS_RATIO));
        }
    }
}