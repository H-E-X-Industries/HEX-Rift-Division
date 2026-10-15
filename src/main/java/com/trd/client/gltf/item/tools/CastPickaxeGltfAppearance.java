package com.trd.client.gltf.item.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.item.tools.cast_pickaxes.CastPickaxeClientAnim;
import com.trd.item.tools.cast_pickaxes.CastPickaxeItem;
import com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Облик литой кирки на glTF.
 *
 * <p>Модель общая у обеих кирок — {@code assets/trd/models/item/cast_pickaxe.gltf},
 * различаются они только текстурой. Текстура в модель не зашита: в её материале
 * стоит метка слота {@value #TEXTURE_SLOT}, а она означает «текстуру сюда
 * подставляет вызывающий». Слот подставляет {@link CastPickaxeRenderer}, поэтому
 * одна модель обслуживает обе кирки и дублировать файл ради второй текстуры не
 * приходится.
 *
 * <p>Анимации живут в самой модели: {@code charging}, {@code holding} и
 * {@code hit}. Какой из них показывать и на какой секунде — решает
 * {@link CastPickaxeClientAnim}.
 *
 * <p><b>Анимируется кирка только в руках.</b> Контекстов отрисовки у предмета
 * много, и модель перебирают по десять раз за кадр: значок в инвентаре, в хотбаре,
 * на полке, в голове, в сравнении с блоком. Вне рук возвращается исходная поза
 * модели — см. {@link #isHand}.
 */
public class CastPickaxeGltfAppearance implements ItemAppearance {

    /** Метка слота в материале модели: текстуру подставляет рендерер. */
    public static final String TEXTURE_SLOT = "pickaxe";

    private static final ResourceLocation IRON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_iron.png");
    private static final ResourceLocation STEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_steel.png");

    /**
     * Текстура последнего отрисованного стака.
     *
     * <p>Дальше по конвейеру до рендерера доходит уже одна и та же модель без
     * сведений о предмете, поэтому выбор делается здесь, а рендерер просто
     * забирает результат. Значение ставится и для не-руки: контекст не-руки у
     * рендерера не спрашивает, но текстуру там всё равно ставить надо, иначе
     * значок в инвентаре нарисовался бы чужой.
     */
    @Nullable
    private static ResourceLocation currentTexture = IRON_TEXTURE;

    /** Чья это рука в третьем лице; ставится {@link CastPickaxeHandOwner}. */
    @Nullable
    private static Player holder;

    private CastPickaxeGltfAppearance() {
    }

    public static CastPickaxeGltfAppearance create() {
        return new CastPickaxeGltfAppearance();
    }

    /**
     * Рисуется ли предмет в руке.
     *
     * <p>Только четыре контекста рук. Всё остальное — инвентарь, хотбар, полка,
     * голова, брошенный предмет, значок в креативе — это значок, а не оружие в
     * руках, и жить анимацией замаха он не должен.
     *
     * <p>Сравнения, а не {@code switch}: {@link ItemDisplayContext} у NeoForge
     * расширяемый, и значения, добавленные чужим модом, попали бы в ветку «не
     * рука» только при честном {@code default}.
     */
    public static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** Рука, которой рисуется этот контекст, либо {@code null} для не-руки. */
    @Nullable
    public static InteractionHand handOf(ItemDisplayContext context) {
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

    /** Текстура, которой рисуется стак. */
    public static ResourceLocation texture() {
        return currentTexture;
    }

    /** Кому принадлежит рука, которую сейчас рисуют в третьем лице. */
    public static void setHolder(@Nullable Player player) {
        holder = player;
    }

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        currentTexture = stack.getItem() instanceof CastPickaxeSteelItem ? STEEL_TEXTURE : IRON_TEXTURE;

        GemRenderGltfModel model = CastPickaxeGltfModels.model();
        if (model != null) {
            CastPickaxeClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        Player player = playerOf(stack, context);
        if (player == null) {
            return null;
        }

        String current = CastPickaxeClientAnim.current(player, handOf(context));
        return current == null ? null : CastPickaxeGltfModels.clip(current);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        Player player = playerOf(stack, context);
        if (player == null) {
            return 0.0F;
        }

        return CastPickaxeClientAnim.seconds(player, handOf(context), partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        // Трансформаций у кирки нет: поза целиком задаётся клипами модели, а
        // поворот и наклон в руке — это делает сам предметный контекст Minecraft.
    }

    /**
     * Игрок, которому принадлежит этот контекст рук, либо {@code null}.
     *
     * <p>От первого лица рука на экране — всегда локального игрока. Из третьего
     * лица кирку в руке видно у того, чья это модель, но контекст отрисовки
     * предметов такую принадлежность не несёт: {@code renderByItem} сущность не
     * получает. Поэтому владельца подставляет {@link CastPickaxeHandOwner} по
     * событию отрисовки живого существа — прямо перед тем, как рисуются руки.
     */
    @Nullable
    private static Player playerOf(ItemStack stack, ItemDisplayContext context) {
        if (!isHand(context) || !(stack.getItem() instanceof CastPickaxeItem)) {
            return null;
        }

        if (context.firstPerson()) {
            return Minecraft.getInstance().player;
        }

        Player current = holder;
        if (current == null || !current.isAlive()) {
            return null;
        }
        return current;
    }
}