package com.trd.item.weapons.guns;

import com.trd.main.MainRegistry;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Клиентский автомат анимаций пушки.
 * <p>
 * Раньше состояние анимаций держал геколибовский {@code AnimationController}:
 * предикат выбирал клип, а {@code SoundKeyframeHandler} проигрывал звуки в
 * keyframe-точках. С переходом на glTF тикать контроллеры больше некому —
 * рендерером стал GemRender, который про геколиб ничего не знает. Поэтому
 * автомат держит своё состояние сам.
 * <p>
 * <b>Правило автомата: один клип за раз.</b> Клип, начавшись, доигрывается
 * целиком и не прерывается ничем: следующий запрос либо ждёт в очереди, либо
 * отбрасывается. Раньше клип стрельбы крутился циклом, пока зажат огонь, и
 * reload/flip стартовали поверх него — это и давало наслоение и дёрганье.
 * Теперь каждый выстрел и каждая перезарядка приходят с сервера отдельным
 * запросом, поэтому анимация привязана к реальным событиям, а не к нажатию.
 * <p>
 * Звуки и тайминги перенесены из {@code animations/machinegun.animation.json}
 * один в один: gunpull на 0.4583 с, heavy_gunclick на 3.6667 с, gunclick на
 * 4.625 с — это 9, 73 и 92 тика.
 */
public final class MachineGunClientAnim {

    /** Клип стрельбы: короткий отскок ствола. */
    public static final String SHOT = "shot";
    /** Перезарядка из патронов. */
    public static final String RELOAD = "reload";
    /** Проверка/разрядка магазина, когда перезаряжаться нечем. */
    public static final String FLIP = "flip";

    /**
     * Длины клипов. Инициализируются один раз из самого glTF в
     * {@link #syncDurations}. Держать эти числа вручную опасно: значение больше
     * реальной длины клипа обрезает анимацию, и она выглядит «дёрганой».
     */
    private static float shotDuration = 0.3333f;
    private static float reloadDuration = 3.125f;
    private static float flipDuration = 3.9167f;

    /**
     * Моменты затворных звуков как доля длины клипа, а не как тики.
     * <p>
     * Тики привязывали звуки к прежним пятисекундным анимациям. Нынешний
     * {@code reload} длится 3.125 с (62 тика), а {@code flip} — 3.9167 с
     * (78 тиков), и жёсткие 73/92 тика туда просто не влезали: звуков не было
     * вообще. Доли переживают переэкспорт модели.
     */
    private static final float MAG_PULL_AT = 0.20f;
    private static final float MAG_IN_AT = 0.34f;
    private static final float BOLT_AT = 0.82f;

    private static float magPullTick;
    private static float magInTick;
    private static float boltTick;

    @Nullable
    private static String current;
    private static int age;

    /**
     * Дробный возраст клипа. Растёт на {@link #tick}, но рендер читает его же с
     * добавлением {@code partialTick}, поэтому анимация идёт плавно, а не
     * ступеньками по 20 в секунду. Именно из-за целочисленного счёта анимация
     * выглядела как 30 fps.
     */
    private static double preciseAge;

    /**
     * Клип, который не влез в текущий и ждёт своего черёда. Держим ровно один
     * запрос: пока играет перезарядка, ждать больше нечего — сервер всё равно
     * не даст выстрелить.
     */
    @Nullable
    private static String queued;

    /** Сколько из трёх затворных звуков уже прозвучало в текущем клипе. */
    private static int played;

    private MachineGunClientAnim() {
    }

    /**
     * Подтягивает реальные длины клипов из загруженной модели.
     * <p>
     * Должны вызываться только с клиента: {@link GemRenderGltfModel} живёт в
     * GemRender, а на сервере той нет.
     */
    public static void syncDurations(GemRenderGltfModel model) {
        GltfAnimation shot = model.animation(SHOT);
        if (shot != null) {
            shotDuration = shot.duration();
        }

        GltfAnimation reload = model.animation(RELOAD);
        if (reload != null) {
            reloadDuration = reload.duration();
        }

        GltfAnimation flip = model.animation(FLIP);
        if (flip != null) {
            flipDuration = flip.duration();
        }

        // Звуки целимся в узлы анимации: у reload это уход магазина вниз
        // (тик ~15) и его возврат (тик ~50), у flip — доворот патронника
        // (тик ~46) и возврат магазина (тик ~70).
        float reloadTicks = reloadDuration * 20.0f;
        magPullTick = Math.min(reloadTicks * 0.24f, 16.0f);
        magInTick = Math.min(reloadTicks * 0.40f, 26.0f);
        boltTick = Math.min(reloadTicks * 0.80f, 51.0f);
    }

    /**
     * Запрос на проигрывание клипа. Возвращает {@code false}, если клип уже
     * играет и повтор того же клипа начать нельзя.
     */
    public static boolean trigger(String anim) {
        if (durationOf(anim) <= 0.0f) return false;

        if (anim.equals(current)) {
            // Тот же клип уже идёт: перезапуск с нуля и есть главный источник
            // дёрганья, поэтому игнорируем повтор.
            return false;
        }

        if (current != null) {
            // Занято. Долгую перезарядку не рубим ради выстрела — иначе она
            // не доиграет и патроны будут добавляться на середине жеста.
            // Короткий выстрел, наоборот, лучше пропустить.
            if (durationOf(anim) < durationOf(current)) {
                return false;
            }
            queued = anim;
            return true;
        }

        begin(anim);
        return true;
    }

    private static void begin(String anim) {
        current = anim;
        age = 0;
        preciseAge = 0.0;
        played = 0;
    }

    /** Тик клиента: доводит текущий клип до конца и подхватывает очередной. */
    public static void tick() {
        if (current == null) return;

        age++;
        preciseAge += 1.0;

        if (age >= durationOf(current) * 20.0f) {
            current = null;
            age = 0;
            preciseAge = 0.0;
            played = 0;

            if (queued != null) {
                String next = queued;
                queued = null;
                begin(next);
            }
            return;
        }

        // Затворные звуки есть только у reload/flip.
        playReloadSound(age);
    }

    /** Сброс состояния — пушка убрана из руки или открыт какой-то экран. */
    public static void reset() {
        current = null;
        age = 0;
        preciseAge = 0.0;
        queued = null;
        played = 0;
    }

    /** Имя клипа для рендера либо {@code null}, если пушка в покое. */
    @Nullable
    public static String current() {
        return current;
    }

    /**
     * Время клипа в секундах — GemRender ждёт именно секунды.
     * <p>
     * {@code partialTick} (0..1) добавляется к накопленному возрасту, поэтому
     * между тиками кадр всё равно сдвигается — на высоком FPS анимация идёт
     * плавно, а не ступенями по 20 раз в секунду.
     */
    public static float seconds(float partialTick) {
        float seconds = (float) ((preciseAge + partialTick) / 20.0);

        // Время обрезаем по длине клипа, а не пропускаем через него.
        // GemRender внутри DirectRenderer.stagePalette зовёт GltfAnimation.loop(),
        // а это остаток от деления на длину: любое время сверх конца клипа
        // заворачивается в начало. Для shot (0.3333 с) последний кадр выдавал
        // ~0.35 с, который превращался в ~0.017 с — удар в пик отдачи, и ствол
        // дёргался назад вместо того, чтобы спокойно вернуться в покой.
        float duration = durationOf(current);
        if (duration > 0.0f && seconds > duration) {
            seconds = duration;
        }
        return seconds;
    }

    private static float durationOf(@Nullable String anim) {
        if (SHOT.equals(anim)) return shotDuration;
        if (RELOAD.equals(anim)) return reloadDuration;
        if (FLIP.equals(anim)) return flipDuration;
        return 0.0f;
    }

    private static void playReloadSound(int tick) {
        if (SHOT.equals(current)) return;

        String name;
        if (tick >= magPullTick && played < 1) {
            name = "gunpull";
            played = 1;
        } else if (tick >= magInTick && played < 2) {
            name = "heavy_gunclick";
            played = 2;
        } else if (tick >= boltTick && played < 3) {
            name = "gunclick";
            played = 3;
        } else {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name));
        if (sound == null) return;

        player.playSound(sound, 1.0F, 1.0F);
    }
}