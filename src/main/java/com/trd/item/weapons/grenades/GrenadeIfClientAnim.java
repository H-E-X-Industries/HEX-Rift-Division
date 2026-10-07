package com.trd.item.weapons.grenades;

import com.trd.main.MainRegistry;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
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
 * <h2>Состояние живёт по рукам</h2>
 * Раньше состояние было одно на оба предмета, и это ломало всё: клип продолжал
 * играть в горячей панели и в инвентаре, а смена слота на любой другой предмет
 * начинала выдёргивание чеки заново — даже если граната осталась лежать в
 * другой руке. Теперь состояния два, по одному на руку, и каждое знает, что
 * у него в руке.
 *
 * <h2>Когда клип начинается</h2>
 * Ровно в двух случаях: в руку попал <b>предмет-граната</b>, которого там не было,
 * и только что выброшена граната из этой же руки. Второе важно для ощущения от
 * броска: счёт в стопке уменьшился, в руке лежит уже следующая граната, и она
 * обязана выдернуть себе чеку сама — иначе после кулдауна кидаешь уже готовую,
 * без звука и без жеста.
 *
 * <p>Чего клип не делает: не реагировать на <b>счёт</b> в стопке. Раньше смена
 * счёта была поводом для анимации, но это ловушка — сервер меняет счёт стопки
 * отдельным пакетом, и на клиенте тот же бросок приходил двумя разными
 * событиями. Теперь повод только один и он точный — сам бросок, из
 * {@link GrenadeIfItem#use}.
 *
 * <h2>Когда клип кончается</h2>
 * В тот же тик, в который гранаты в руке не стало. Убирание предмета и смена
 * слота обрывают выдёргивание на середине, а не дают ему доиграть уже в пустой
 * руке. Открытие экрана клип тоже обрывает, но <b>не забывает</b>, что было в
 * руке: иначе закрытие инвентаря считалось бы взятием гранаты и чека
 * выдёргивалась бы каждый раз.
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
     * Форма жеста «взял предмет»: насколько глубоко предмет уходит вниз, по
     * тикам от начала жеста.
     * <p>
     * Треугольник: мимо, мимо, дно, мимо, мимо — предмет ныряет и возвращается.
     * Ноль по краям обязателен: без него предмет застрял бы внизу навсегда.
     */
    private static final float[] REQUIP_FRAMES = {0.3F, 0.75F, 1.0F, 0.75F, 0.3F, 0.0F};

    /**
     * Длина клипа до первой загрузки модели.
     * <p>
     * Взята из glTF, а не из json экспорта: там у {@code pin_pull} объявлено
     * 1.25 с, но это окно показа «держать последний кадр», а не конец движения —
     * последний ключ и в json, и в модели стоит на 1.0833 с. Замерять анимацию по
     * той цифре нельзя, она обрежется по длине клипа.
     */
    private static float pinPullDuration = 1.0833f;

    /**
     * Сколько тиков проходит между броском и выдёргиванием чеки.
     * <p>
     * Ровно столько, сколько нужно предмету, чтобы вернуться в руку: жест идёт
     * {@link #REQUIP_FRAMES} тиков, и до последнего из них предмет ещё опущен.
     * Пока он опущен, в нём показан собранная граната — та самая, новенькая.
     */
    private static final int REQUIP_PULL_DELAY = REQUIP_FRAMES.length;

    /** Состояние правой руки. */
    private static final Hand MAIN = new Hand();

    /** Состояние левой руки. */
    private static final Hand OFF = new Hand();

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

    /**
     * Граната только что выброшена из этой руки: в руке осталась следующая.
     * <p>
     * Здесь же запускается жест «взял новую гранату» — предмет уходит вниз и
     * возвращается, ровно как при подборе. Без него бросок читается как просто
     * исчезновение предмета, а следующая граната выглядит той же самой, что и
     * предыдущая: у неё уже выдернута чека.
     * <p>
     * Сам жест рисует рендер, а не поднимает руку: см. {@link #requip}.
     */
    public static void onThrown(InteractionHand hand) {
        stateOf(hand).pull();
    }

    /**
     * Насколько предмет в этой руке сейчас опущен — от 0 (в руке) до 1 (у рук).
     * <p>
     * Раньше для этого дёргали приватное поле {@code ItemInHandRenderer} — и
     * это была неправильная затея: {@code ItemInHandRenderer#tick} каждый кадр
     * пересчитывает высоту руки сам, по кубу силы удара, а у брошенной гранаты
     * сила удара обнулена сервером, так что подъём растягивался на полсекунды и
     * выглядел как «граната просто появилась». Теперь жест живёт в позе
     * предмета и ни с чем не конкурирует: рендер кладёт трансляцию сам, а
     * {@link #REQUIP_FRAMES} задаёт её форму.
     * <p>
     * Значение нужно только руке; в инвентаре и на земле предмета нет, поэтому
     * там возвращается ноль.
     */
    public static float requip(@Nullable InteractionHand hand) {
        return hand == null ? 0.0F : stateOf(hand).requip;
    }

    /**
     * Имя клипа для рендера либо {@code null}, если в этой руке граната в покое.
     * <p>
     * Рукой, а не «чем угодно», потому что у GemRender один и тот же рендерер
     * обслуживает и руку, и инвентарь, и хотбар: клип должен попадать только в
     * руку, иначе значок гранаты в панели дёргал бы чеку вместе с ней.
     * {@code null} означает «не в руке» — рисуется исходная поза модели.
     */
    @Nullable
    public static String current(@Nullable InteractionHand hand) {
        return hand == null ? null : stateOf(hand).current;
    }

    /**
     * Доводит клипы до конца и ловит взятие гранаты в руку.
     * <p>
     * Экран не считается взятием: пока открыт любой {@code Screen}, клипы просто
     * стоят на паузе, а предметы в руках запоминаются. Иначе каждое открытие
     * инвентаря считалось бы повторным взятием гранаты.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            reset();
            return;
        }

        if (mc.screen != null) {
            MAIN.stop();
            OFF.stop();
            return;
        }

        MAIN.track(player.getMainHandItem());
        OFF.track(player.getOffhandItem());
    }

    /** Полный сброс: игрока нет, мир и руки можно забыть. */
    public static void reset() {
        MAIN.clear();
        OFF.clear();
    }

    private static Hand stateOf(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? OFF : MAIN;
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
    public static float seconds(@Nullable InteractionHand hand, float partialTick) {
        double precise = stateOf(hand == null ? InteractionHand.MAIN_HAND : hand).preciseAge;

        float seconds = (float) ((precise + partialTick) / 20.0);
        if (pinPullDuration > 0.0f && seconds > pinPullDuration - CLIP_EPSILON) {
            seconds = Math.max(0.0F, pinPullDuration - CLIP_EPSILON);
        }
        return seconds;
    }

    /** Состояние одной руки: что в ней лежит, что в ней играет и как глубоко опущен предмет. */
    private static final class Hand {

        /**
         * Предмет, ради которого клип был запущен, либо {@code null}, если в руке
         * гранаты нет.
         * <p>
         * Память живёт ровно столько, сколько граната лежит в руке: пока её там
         * нет, состояние о ней забыто, и появление гранаты снова будет «взятием».
         * В отличие от клипа, который обрывается ещё и на открытом экране, эта
         * память переживает паузу — иначе каждое открытие инвентаря выглядело бы
         * так, будто гранату взяли заново.
         */
        @Nullable
        private Item held;

        /** Имя играющего клипа либо {@code null}, если в этой руке покой. */
        @Nullable
        private String current;

        /**
         * Клип дошёл до конца и стоит на последнем кадре.
         * <p>
         * Дальше он уже не идёт, но и не пропадает: граната в руке остаётся с
         * выдернутой чекой, ровно как её выдернули. Если бы клип в этот момент
         * просто выключался, предмет мгновенно возвращался бы к исходной позе с
         * целой чекой — и выглядел бы так, будто гранату только что зарядили.
         */
        private boolean finished;

        /** Тиков, оставшихся до старта клипа; ненулевое — после броска. */
        private int delay;

        /**
         * Насколько предмет опущен жестом «взял новую гранату», от 0 до 1.
         * <p>
         * Это единственное, что нужно рисователю: он сдвигает предмет вниз ровно
         * настолько и возвращает обратно по {@link #REQUIP_FRAMES}.
         */
        private float requip;

        /** Индекс кадра жеста; равен длине, когда жеста нет. */
        private int requipFrame;

        /** Прошло тиков с начала клипа. */
        private int age;

        /**
         * Дробный возраст клипа. Растёт на {@link #advance}, но рендер читает его
         * же с добавлением {@code partialTick}, поэтому анимация идёт плавно, а не
         * ступеньками по 20 в секунду.
         */
        private double preciseAge;

        /** Звуки текущего клипа и курсор по ним. */
        private List<GrenadeIfAnimation.Marker> markers = List.of();
        private int markerCursor;

        /**
         * Смотрит, что лежит в руке, и решает, играть ли чеку.
         * <p>
         * Счёт стопки намеренно не учитывается: он меняется и от броска, и от
         * подбора, и от серверной синхронизации, и по нему анимация
         * заводилась бы сама собой. Бросок приходит отдельным вызовом
         * {@link #onThrown}, где повод точно известен.
         */
        private void track(ItemStack stack) {
            Item item = stack.getItem();

            if (!(item instanceof GrenadeIfItem)) {
                // Гранаты в руке больше нет — клип обрывается здесь же, а не
                // доигрывает уже в пустой руке.
                if (held != null) {
                    held = null;
                    stop();
                }
                return;
            }

            if (item != held && pull()) {
                // Запоминаем предмет только когда клип действительно пошёл: пока
                // модель не загрузилась, длина клипа неизвестна и запускать нечего,
                // а «взятие» тогда попробует повториться уже со следующим тиком.
                held = item;
            }

            advance();
        }

        /**
         * Ставит клип на очередь: сначала предмет ныряет и возвращается, и только
         * потом у новой гранаты выдёргивается чека.
         */
        private boolean pull() {
            if (pinPullDuration <= 0.0f) {
                return false;
            }

            stop();
            requipFrame = 0;
            delay = REQUIP_PULL_DELAY;
            return true;
        }

        /** Запускает клип, до которого отсчитал {@link #delay}. */
        private void start() {
            current = PIN_PULL;
            age = 0;
            preciseAge = 0.0;
            markers = GrenadeIfAnimation.sounds(PIN_PULL, pinPullDuration);
            markerCursor = 0;
        }

        /** Доводит клип до конца и проигрывает звуки, до которых дошла анимация. */
        private void advance() {
            advanceRequip();

            if (delay > 0) {
                if (--delay == 0) {
                    start();
                }
                return;
            }

            if (current == null || finished) {
                return;
            }

            age++;
            preciseAge += 1.0;

            if (age >= pinPullDuration * 20.0f) {
                // Конец клипа — не конец позы: граната остаётся с выдернутой
                // чекой до тех пор, пока она лежит в руке.
                finished = true;
                return;
            }

            playDueSounds();
        }

        /** Идёт ли жест «взял предмет» и насколько глубоко сейчас опущен предмет. */
        private void advanceRequip() {
            if (requipFrame >= REQUIP_FRAMES.length) {
                requip = 0.0F;
                return;
            }
            requip = REQUIP_FRAMES[requipFrame++];
        }

        /**
         * Отрывает клип, не забывая предмета.
         * <p>
         * Именно так останавливается анимация при открытии экрана: клип замолкает,
         * но «граната уже в руке» остаётся правдой, и закрытие инвентаря не
         * выглядит повторным взятием.
         */
        private void stop() {
            current = null;
            finished = false;
            delay = 0;
            requip = 0.0F;
            requipFrame = REQUIP_FRAMES.length;
            age = 0;
            preciseAge = 0.0;
            markers = List.of();
            markerCursor = 0;
        }

        /** Полный сброс руки: и клип, и память о предмете. */
        private void clear() {
            held = null;
            stop();
        }

        /**
         * Проигрывает помеченные кадры, до которых дошла анимация.
         * <p>
         * Отбор идёт по интервалу {@code (было, стало]}, а не по флажку «уже
         * проиграно»: кадр срабатывает ровно один раз, даже если между тиками
         * анимация перескочила сразу через два. Нижней границы нет намеренно —
         * курсор и так не даёт кадру сработать дважды, а маркер на 0.0 при
         * строгом сравнении отсекался бы навсегда.
         */
        private void playDueSounds() {
            if (markers.isEmpty()) {
                return;
            }

            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            float now = (float) (preciseAge / 20.0);
            while (markerCursor < markers.size()) {
                GrenadeIfAnimation.Marker marker = markers.get(markerCursor);
                if (marker.time() > now) {
                    break;
                }

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
    }
}