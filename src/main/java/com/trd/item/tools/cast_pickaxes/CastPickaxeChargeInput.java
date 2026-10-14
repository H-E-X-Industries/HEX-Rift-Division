package com.trd.item.tools.cast_pickaxes;

import com.trd.main.MainRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Заряд литой кирки по левой кнопке мыши.
 *
 * <h2>Что перехватывается</h2>
 * Литая кирка не рубит и не бьёт как обычная: она заряжается и бьёт сама. Поэтому
 * на ЛКМ ванильная кнопка атаки отменяется целиком — ни удара по мобу, ни начала
 * ломания блока, ни взмаха рукой, — и вместо неё начинается заряд. ПКМ больше не
 * заряжает: держать надо именно ЛКМ.
 *
 * <p>Отменять приходится не {@code PlayerInteractEvent}, а
 * {@link InputEvent.InteractionKeyMappingTriggered}, потому что это единственное
 * место, где можно снять и действие, и взмах рукой разом:
 * {@code Minecraft#startAttack} спрашивает у события, отменять ли ему вообще
 * ({@code isCanceled}) и махать ли рукой afterwards ({@code shouldSwingHand}).
 * Через {@code PlayerInteractEvent} взмах уже не отменить, и рука дёргалась бы на
 * каждом нажатии.
 *
 * <h2>Почему клавишу использования приходится удерживать</h2>
 * Ванилль отпускает предмет только по ПКМ:
 *
 * <pre>{@code
 * if (player.isUsingItem()) {
 *     if (!options.keyUse.isDown()) gameMode.releaseUsingItem(player);
 *     ...
 * }
 * }</pre>
 *
 * <p>Заряд, начатый по ЛКМ, ванилль отпустил бы на следующем же кадре: ПКМ не
 * нажата, значит нечего держать. Отсюда и приходится на время заряда удерживать
 * ПКМ нажатой — {@code setDown} трогает только состояние клавиши, ни на что
 * больше не влияет. Обращение идёт по самой клавише, а не по физической, так что
 * перебиндывание в настройках ничего не ломает.
 *
 * <p>Удержание переутверждается каждый тик: {@code KeyMapping#setAll()} сбрасывает
 * все клавиши при потере фокуса окна, и без этого заряд прервался бы при
 * переключении на другой экран.
 *
 * <h2>Кто отпускает</h2>
 * Ванилль отпустит предмет по ПКМ сама, а по ЛКМ — никто, поэтому отпускание
 * здесь: как только ЛКМ поднята, шлётся ровно тот же
 * {@code releaseUsingItem}, что шлёт ванилль, и серверный урон считается как
 * прежде.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class CastPickaxeChargeInput {

    /** Идёт ли сейчас заряд, начатый нами. */
    private static boolean charging;

    /** Удерживаем ли мы клавишу использования вместо игрока. */
    private static boolean useKeyHeld;

    private CastPickaxeChargeInput() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof CastPickaxeItem)) {
            return;
        }

        if (event.isAttack()) {
            // Ни удара, ни ломания блока, ни взмаха рукой — только заряд.
            event.setCanceled(true);
            event.setSwingHand(false);
            beginCharge(mc);
            return;
        }

        if (event.isUseItem()) {
            // Заряд переехал на ЛКМ, но взаимодействие с миром по ПКМ у кирки
            // остаётся: двери, сундуки, верстаки, рычаги. Поэтому в пустоту ПКМ
            // просто гасится — иначе она ушла бы в useItem и зарядила кирку.
            if (!(mc.hitResult instanceof BlockHitResult)) {
                event.setCanceled(true);
            }
            // Взмаха рукой у кирки нет ни при каком вводе.
            event.setSwingHand(false);
        }
    }

    /** Начинает заряд, если предмет к нему готов. */
    private static void beginCharge(Minecraft mc) {
        Player player = mc.player;
        if (player.isUsingItem()) {
            // Уже заряжается: повторное нажатие не должно начинать заряд заново.
            return;
        }

        // Тот же вход, что и у ПКМ, только с другой кнопки: сервер получит
        // ServerboundUseItemPacket, отработает Item#use и начнёт отсчёт заряда.
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);

        // useItem применяет Item#use на клиенте через предсказание, так что
        // старт здесь же и синхронный. Если предмет отказался — кулдаун или
        // занятая вторая рука — заряда не будет, и ждать нечего.
        if (!player.isUsingItem()) {
            return;
        }

        charging = true;
        holdUseKey(mc);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }

        if (!charging) {
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            // ЛКМ отпущена — заряд окончен. Отпускаем кирку тем же вызовом, каким
            // пользуется ванилль на ПКМ: он шлёт серверу пакет отпуска и сразу
            // снимает использование на клиенте.
            stop();
            mc.gameMode.releaseUsingItem(mc.player);
            return;
        }

        if (!mc.player.isUsingItem()) {
            // Заряд сорвался сам: игрок умер, кирку убрали или открыли инвентарь —
            // открытие экрана сбрасывает все клавиши, и удержание ПКМ теряется.
            // Отпускать нечего, предмет уже не используется.
            stop();
            return;
        }

        if (!(mc.player.getMainHandItem().getItem() instanceof CastPickaxeItem)) {
            stop();
            return;
        }

        holdUseKey(mc);
    }

    /** Прижимает клавишу использования, чтобы ванилль не отпустила кирку. */
    private static void holdUseKey(Minecraft mc) {
        KeyMapping use = mc.options.keyUse;
        if (useKeyHeld && use.isDown()) {
            return;
        }
        use.setDown(true);
        useKeyHeld = true;
    }

    /** Отпускает всё: и заряд, и удерживаемую клавишу. */
    private static void stop() {
        charging = false;
        if (!useKeyHeld) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
        useKeyHeld = false;
    }
}
