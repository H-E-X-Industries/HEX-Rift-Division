package com.trd.item.weapons.grenades;

import com.trd.main.MainRegistry;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Клиентский автомат анимаций ударной гранаты.
 *
 * <p>Клип у предмета один — {@link #PIN_PULL}, он играет каждый раз, когда граната
 * оказывается в руке. Геколибовских контроллеров в проекте нет (рендер ушёл на
 * GemRender), поэтому состояние держит автомат: ровно так же, как это делает
 * {@code MachineGunClientAnim} у пушки.
 *
 * <p><b>Клипа броска нет.</b> В модели он есть, но показывать его нечем: бросок
 * мгновенный, граната сразу исчезает из руки, и половина жеста всё равно не
 * успевает отрисоваться. Имя клипа {@code throw} осталось жить только в
 * {@link GrenadeIfAnimation#THROW} — там оно нужно, чтобы сервер нашёл в разметке
 * момент для звука броска и поставил его в очередь на свои тики.
 *
 * <p><b>Длины клипов берутся из самой модели</b> и обновляются каждый кадр
 * рендера: держать их руками опасно, потому что любое расхождение с glTF обрежет
 * анимацию и она выглядит дёрганой.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class GrenadeIfClientAnim {

    /** Выдёргивание чеки: играет, когда граната взята в руку. */
    public static final String PIN_PULL = GrenadeIfAnimation.PIN_PULL;

    /**
     * Длина клипа до первой загрузки модели.
     * <p>
     * Взята из glTF, а не из json экспорта: там у {@code pin_pull} объявлено
     * 1.25 с, но это окно показа «держать последний кадр», а не конец движения —
     * последний ключ и в json, и в модели стоит на 1.0833 с. Замерять анимацию по
     * той цифре нельзя, она обрежется по длине клипа.
     */
    private static float pinPullDuration = 1.0833f;

    @Nullable
    private static String current;
    private static int age;

    /**
     * Дробный возраст клипа. Растёт на {@link #onClientTick}, но рендер читает его
     * же с добавлением {@code partialTick}, поэтому анимация идёт плавно, а не
     * ступеньками по 20 в секунду.
     */
    private static double preciseAge;

    /** Звуки текущего клипа и курсор по ним. */
    private static List<GrenadeIfAnimation.Marker> markers = List.of();
    private static int markerCursor;

    /**
     * Что было в руках на прошлом тике.
     * <p>
     * Хранится предметом и его счётом, а не стопкой: сравнение стопок на каждый
     * тик создавало бы мусор. Счёт важен не меньше предмета — у гранаты их в руке
     * может быть сразу несколько, и каждая должна начать с новой выдернутой чекой.
     * Смена счёта и есть бросок, так что и��ход счёт и есть «достать следующую».
     */
    @Nullable
    private static Item lastMain;
    private static int lastMainCount;
    @Nullable
    private static Item lastOff;
    private static int lastOffCount;

    private GrenadeIfClientAnim() {
    }

    /**
     * Подтягивает реальную длину клипа из загруженной модели.
     * <p>
     * Должна вызываться только с клиента: {@link GemRenderGltfModel} живёт в
     * GemRender, а на сервере той нет.
     */
    public static void syncDurations(@Nullable GemRenderGltfModel model) {
        if (model == null) {
            return;
        }

        var pinPull = model.animation(PIN_PULL);
        if (pinPull != null) {
            pinPullDuration = pinPull.duration();
        }
    }

    /** Имя клипа для рендера либо {@code null}, если граната в покое. */
    @Nullable
    public static String current() {
        return current;
    }

    /** Клик, который можно запустить заново в любой момент. */
    private static boolean triggerPull() {
        if (pinPullDuration <= 0.0f) {
            return false;
        }

        current = PIN_PULL;
        age = 0;
        preciseAge = 0.0;
        markers = GrenadeIfAnimation.sounds(PIN_PULL, pinPullDuration);
        markerCursor = 0;
        return true;
    }

    /** Тик клиента: ловит взятие гранаты в руку и доводит клип до конца. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.screen != null) {
            reset();
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (main.getItem() instanceof GrenadeIfItem || off.getItem() instanceof GrenadeIfItem) {
            if (handChanged(main, off)) {
                triggerPull();
            }
            lastMain = main.getItem();
            lastMainCount = main.getCount();
            lastOff = off.getItem();
            lastOffCount = off.getCount();
        } else {
            reset();
            return;
        }

        advance();
    }

    /**
     * Изменилось ли то, что в руках.
     * <p>
     * Ловится и смена слота, и подбор в руку, и <b>бросок</b>: у гранаты их в
     * руке может быть несколько, и после броска счёт уменьшается — следующая
     * выдергивает себе чеку сама. Без этого счёта многоствольная граната
     * выдавала бы чеку только при первой выдаче, а дальше бросалась бы уже
     * готовой, без звука и без жеста.
     * <p>
     * Клипа броска больше нет, поэтому ложных срабатываний на уменьшении стопки
     * не случается: счёт меняется ровно один раз, и это ровно то, что нужно.
     */
    private static boolean handChanged(ItemStack main, ItemStack off) {
        return changed(main.getItem(), main.getCount(), lastMain, lastMainCount)
                || changed(off.getItem(), off.getCount(), lastOff, lastOffCount);
    }

    private static boolean changed(Item item, int count, @Nullable Item last, int lastCount) {
        return item != last || count != lastCount;
    }

    /** Доводит клип до конца и проигрывает звуки, до которых дошла анимация. */
    private static void advance() {
        if (current == null) return;

        age++;
        preciseAge += 1.0;

        if (age >= pinPullDuration * 20.0f) {
            current = null;
            age = 0;
            preciseAge = 0.0;
            markers = List.of();
            markerCursor = 0;
            return;
        }

        playDueSounds();
    }

    /**
     * Проигрывает помеченные кадры, до которых дошла анимация.
     * <p>
     * Отбор идёт по интервалу {@code (было, стало]}, а не по флажку «уже
     * проиграно»: кадр срабатывает ровно один раз, даже если между тиками
     * анимация перескочила сразу через два. Нижней границы нет намеренно —
     * курсор и так не даёт кадру сработать дважды, а маркер на 0.0 при строгом
     * сравнении отсекался бы навсегда.
     */
    private static void playDueSounds() {
        if (markers.isEmpty()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        float now = (float) (preciseAge / 20.0);
        while (markerCursor < markers.size()) {
            GrenadeIfAnimation.Marker marker = markers.get(markerCursor);
            if (marker.time() > now) break;

            markerCursor++;
            // Щелчок чеки слышит только тот, кто держит гранату: момент «взял в
            // руку» на сервере не существует, а чужие чеки слышать незачем.
            // Путь звука берётся из маркера, а не захардкожен, — как и всё
            // остальное, что касается тайминга, из экспорта анимаций.
            SoundEvent sound = GrenadeIfAnimation.sound(marker.id());
            if (sound != null) {
                player.playSound(sound, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
        }
    }

    /** Сброс состояния — граната убрана из рук или открыт какой-то экран. */
    public static void reset() {
        current = null;
        age = 0;
        preciseAge = 0.0;
        markers = List.of();
        markerCursor = 0;
        lastMain = null;
        lastMainCount = 0;
        lastOff = null;
        lastOffCount = 0;
    }

    /**
     * Насколько меньше длины клипа приходится отдавать в рендер.
     * <p>
     * Внутри {@code DirectRenderer.stagePalette} время прогоняется через
     * {@code GltfAnimation.loop()}, а это остаток от деления на длину. Ровно на
     * длине остаток нулевой, то есть {@code duration} тихо превращается в позу
     * покоя. Отдаём на миллисекунду меньше: попадаем ровно на последний ключ, а
     * ломать перенос времени уже нечем.
     */
    private static final float CLIP_EPSILON = 1.0E-3F;

    /**
     * Время клипа в секундах — GemRender ждёт именно секунды.
     * <p>
     * {@code partialTick} (0..1) добавляется к накопленному возрасту, поэтому
     * между тиками кадр всё равно сдвигается — на высоком FPS анимация идёт
     * плавно, а не ступеньками по 20 раз в секунду.
     */
    public static float seconds(float partialTick) {
        float seconds = (float) ((preciseAge + partialTick) / 20.0);

        if (pinPullDuration > 0.0f && seconds > pinPullDuration - CLIP_EPSILON) {
            seconds = Math.max(0.0F, pinPullDuration - CLIP_EPSILON);
        }
        return seconds;
    }
}