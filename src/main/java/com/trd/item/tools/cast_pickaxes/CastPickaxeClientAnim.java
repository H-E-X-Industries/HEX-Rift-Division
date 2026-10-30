package com.trd.item.tools.cast_pickaxes;

import com.trd.main.MainRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.motion.SkeletalAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Клиентский автомат анимаций литой кирки.
 *
 * <h2>Зачем он свой</h2>
 * Раньше состояние анимаций держал геколибовский {@code AnimationController}:
 * сервер дёргал триггеры {@code charging}, {@code holding} и {@code hit}.
 * С переходом на glTF тикать контроллеры больше некому — рендерером стал
 * HEX Render, который про геколиб ничего не знает, и синхронизировать анимацию
 * предмета через {@code GeoItem.getOrAssignId} больше нечем.
 *
 * <p>Пакет при этом не понадобился: {@code LivingEntity#isUsingItem} читает бит
 * в синхронизируемых данных сущности, поэтому клиент и без всякой сети знает и
 * про локального игрока, и про любого другого — достаточно посчитать, сколько
 * тиков тот держит кирку. Ванилль сама ставит клиенту счётчик
 * {@code useItemRemainingTicks} на старте использования, см.
 * {@code LivingEntity#onSyncedDataUpdated}. Так что замах видят все, кому виден и
 * сам игрок, и без единого нового пакета.
 *
 * <h2>Три клипа</h2>
 * <ul>
 *   <li>{@code charging} — набор заряда; длится ровно {@code chargeTicks} тиков,
 *       см. {@link #speed};</li>
 *   <li>{@code holding} — удержание на полном заряде, крутится по кругу;</li>
 *   <li>{@code hit} — замах при отпускании, играется один раз.</li>
 * </ul>
 *
 * <p>Скорость {@code hit} и {@code holding} не трогается: множитель существует
 * ради синхронизации набора заряда с полоской из
 * {@link com.trd.client.overlay.hud.CastPickaxeHud}, а растянутый замах — это не
 * то, что нужно.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class CastPickaxeClientAnim {

    /** Набор заряда. */
    public static final String CHARGING = "charging";
    /** Удержание на полном заряде. */
    public static final String HOLDING = "holding";
    /** Замах при отпускании. */
    public static final String HIT = "hit";

    /**
     * Ниже этого заряда удар не считается ударом.
     *
     * <p>Ровно тот же порог, что в {@link CastPickaxeItem#releaseUsing}: при
     * нём игрок только машет киркой, ничего не ломая и не задевая.
     */
    private static final float MIN_CHARGE = 0.05F;

    /**
     * Насколько не доезжать до конца клипа.
     *
     * <p>Ровно на длине клипа {@code loop()} заворачивает время в ноль, поэтому
     * последний кадр и должен быть чуть раньше конца.
     */
    private static final float CLIP_EPSILON = 1.0E-3F;

    /** Длины клипов в секундах; читаются из модели в {@link #syncDurations}. */
    private static float chargingSeconds = 2.0F;
    private static float hitSeconds = 1.75F;

    /**
     * Длина клипа замаха в тиках, по ней он и доигрывается.
     *
     * <p>Исходное значение берётся у предмета, а не выдумывается здесь: кулдаун
     * между зарядами равен длине замаха, и числа обязаны совпадать, иначе либо
     * кирку можно будет замахнуть поверх недоконченного удара, либо придётся ждать
     * впустую. Настоящая длина потом подтягивается из самой модели.
     */
    private static float hitTicks = CastPickaxeItem.COOLDOWN_TICKS;

    /** Кто чем сейчас занят: по игроку и по руке. */
    private static final Map<Holder, State> HOLDERS = new HashMap<>();

    private CastPickaxeClientAnim() {
    }

    /**
     * Состояние одной руки одного игрока.
     *
     * <p>Ключ — пара «игрок + рука», а не просто рука: два игрока, замахнувшихся
     * в одном кадре, должны выглядеть каждый по-своему, а одно общее состояние на
     * руку рисовало бы обоим чужой замах.
     */
    private record Holder(Player player, InteractionHand hand) {
    }

    /** Что сейчас играет у одной руки. */
    private static final class State {

        @Nullable
        private CastPickaxeItem pickaxe;

        @Nullable
        private String clip;

        /** Сколько тиков идёт текущий клип. */
        private double age;

        /**
         * Замах ещё идёт, а кирку уже отпустили.
         *
         * <p>Отдельный флаг вместо проверки «прямо сейчас использует предмет»:
         * иначе замах обрывался бы в тот же тик, когда пропадает признак
         * использования, и удара не было бы видно совсем.
         */
        private boolean swinging;

        /** Сколько тиков кирка заряжалась подряд — решает, будет ли замах. */
        private int chargedTicks;

        void reset() {
            pickaxe = null;
            clip = null;
            age = 0.0;
            swinging = false;
            chargedTicks = 0;
        }
    }

    /**
     * Подтягивает реальные длины клипов из загруженной модели.
     *
     * <p>Только с клиента: {@link HexGltfModel} живёт в HEX Render, на
     * сервере той нет.
     */
    public static void syncDurations(@Nullable GltfModelAsset model) {
        if (model == null) return;

        SkeletalAnimation charging = model.animation(CHARGING);
        if (charging != null && charging.duration() > 0.0F) {
            chargingSeconds = charging.duration();
        }
        SkeletalAnimation hit = model.animation(HIT);
        if (hit != null && hit.duration() > 0.0F) {
            hitSeconds = hit.duration();
            hitTicks = hit.duration() * 20.0F;
        }
    }

    /** Имя клипа для рендера либо {@code null}, если кирка в покое. */
    @Nullable
    public static String current(Player player, @Nullable InteractionHand hand) {
        if (hand == null || player == null) return null;
        State state = HOLDERS.get(new Holder(player, hand));
        return state != null ? state.clip : null;
    }

    /**
     * Время клипа в секундах — HEX Render ждёт именно секунды.
     *
     * <p>{@code partialTick} добавляется к накопленному возрасту, поэтому между
     * тиками кадр всё равно сдвигается: на высоком FPS анимация идёт плавно, а не
     * ступеньками по двадцать раз в секунду.
     *
     * <p>Время обрезается по длине клипа, а не пропускается через него.
     * {@code HEX Render} внутри отрисовки зовёт {@code GltfAnimation#loop()}, а
     * это остаток от деления на длину: любое время сверх конца клипа
     * заворачивается в начало. У чужого игрока счётчик использования предмета
     * может на тик отставать, и без обрезки кирка в последнем кадре замаха
     * прыгала бы с конца клипа в его начало.
     *
     * <p>{@code holding} не обрезается: он крутится по кругу, и замкнут сам по
     * себе.
     */
    public static float seconds(@Nullable Player player, @Nullable InteractionHand hand, float partialTick) {
        if (hand == null || player == null) return 0.0F;
        State state = HOLDERS.get(new Holder(player, hand));
        if (state == null) return 0.0F;

        float seconds = (float) ((state.age + partialTick) / 20.0) * speed(state.clip, state.pickaxe);

        float limit = limitOf(state.clip);
        if (limit > 0.0F && seconds > limit - CLIP_EPSILON) {
            seconds = Math.max(0.0F, limit - CLIP_EPSILON);
        }
        return seconds;
    }

    /**
     * Длина клипа в секундах, до которой время ещё не заворачивается.
     *
     * <p>У крутящегося {@code holding} предела нет: возвращается ноль, и рендер
     * крутит клип штатно.
     */
    private static float limitOf(@Nullable String clip) {
        if (CHARGING.equals(clip)) return chargingSeconds;
        if (HIT.equals(clip)) return hitSeconds;
        return 0.0F;
    }

    /**
     * Множитель скорости клипа.
     *
     * <p>Ровно то, что раньше задавалось геколибовскому контроллеру через
     * {@code CastPickaxeItem#getAnimationSpeed}: клип набора заряда должен
     * уложиться в полное время заряда кирки, иначе полная картинка разошлась
     * бы с полоской заряда.
     */
    private static float speed(@Nullable String clip, @Nullable CastPickaxeItem pickaxe) {
        if (!CHARGING.equals(clip) || pickaxe == null) return 1.0F;
        int chargeTicks = pickaxe.getStats().getChargeTicks();
        return chargeTicks <= 0 ? 1.0F : 40.0F / chargeTicks;
    }

    /** Клик клиента: следит за руками всех игроков и доводит замахи до конца. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        // Открытый экран клип не сматывает, а задерживает: игрок мог открыть
        // инвентарь посреди заряда, и после закрытия замах должен продолжиться
        // с того же места, а не начаться заново.
        if (mc.screen != null) {
            return;
        }

        forgetGonePlayers(mc);

        for (Player player : mc.level.players()) {
            track(player, InteractionHand.MAIN_HAND);
            track(player, InteractionHand.OFF_HAND);
        }
    }

    /** Забывает игроков, которых больше нет в мире. */
    private static void forgetGonePlayers(Minecraft mc) {
        Iterator<Map.Entry<Holder, State>> it = HOLDERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Holder, State> entry = it.next();
            Player player = entry.getKey().player();
            if (player.isAlive() && player.level() == mc.level) {
                continue;
            }
            entry.getValue().reset();
            it.remove();
        }
    }

    /** Следит за одной рукой одного игрока. */
    private static void track(Player player, InteractionHand hand) {
        State state = HOLDERS.computeIfAbsent(new Holder(player, hand), key -> new State());

        boolean charging = player.isUsingItem()
                && player.getUsedItemHand() == hand
                && player.getUseItem().getItem() instanceof CastPickaxeItem;

        if (charging) {
            charge(state, (CastPickaxeItem) player.getUseItem().getItem(), player);
            return;
        }

        if (state.swinging) {
            state.age += 1.0;
            if (state.age >= hitTicks) {
                state.reset();
            }
            return;
        }

        release(state);
    }

    /** Игрок держит кирку: крутим набор заряда, на полном — удержание. */
    private static void charge(State state, CastPickaxeItem pickaxe, Player player) {
        state.pickaxe = pickaxe;
        state.chargedTicks++;

        // Порог полного заряда тот же, по которому предмет считает заряд полным:
        // ровно на chargeTicks тике набор доходит до конца, и следующий клип
        // начинается в тот же тик.
        String wanted = player.getTicksUsingItem() >= pickaxe.getStats().getChargeTicks() ? HOLDING : CHARGING;

        if (!wanted.equals(state.clip)) {
            // Смена клипа всегда с нуля: holding начинается там, где кончился
            // charging, а hit — там, где игрок отпустил кирку. Продолжение
            // предыдущего клипа с его середины означало бы замах из ниоткуда.
            state.clip = wanted;
            state.age = 0.0;
        }
        state.swinging = false;
        state.age += 1.0;
    }

    /** Кирку отпустили: либо показываем замах, либо просто роняем кирку в покой. */
    private static void release(State state) {
        if (state.clip == null) {
            state.reset();
            return;
        }

        int chargeTicks = state.pickaxe != null ? state.pickaxe.getStats().getChargeTicks() : 0;
        boolean charged = chargeTicks > 0 && state.chargedTicks >= MIN_CHARGE * chargeTicks;

        if (charged) {
            state.clip = HIT;
            state.age = 0.0;
            state.swinging = true;
            return;
        }

        // Заряда не набралось — удара не было, и показывать нечего. То же самое
        // делает предмет: при заряде меньше 5% он просто машет киркой.
        state.reset();
    }

    /** Сбрасывает всё: кирка убрана из руки, мир выгружен, игрок вышел. */
    public static void reset() {
        for (State state : HOLDERS.values()) {
            state.reset();
        }
        HOLDERS.clear();
    }
}