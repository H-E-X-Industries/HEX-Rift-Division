package com.trd.item.weapons.guns;

import com.trd.client.gecko.item.guns.MachineGunModel;
import com.trd.client.overlay.MachineGunScope;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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
 * <b>Звуки и частицы приходят по часам анимации.</b> Кадры, помеченные прямо в
 * glTF, разбирает {@link MachineGunModel} и проигрывает
 * {@link #playMarkedEffects()}. Никаких захардкоженных тиков и никакого
 * геколиба: пометил кадр на кости в Blockbench — и звук или вспышка поехали
 * вместе с анимацией. Частица ставится на локатор дула из самой модели.
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

    @Nullable
    private static String current;
    private static int age;

    /**
     * Помеченные частицы текущего клипа и курсор по ним.
     * <p>
     * Только частицы: звуки ставит в очередь сервер, см.
     * {@link MachineGunItem#scheduleClipSounds}. Курсор, а не флаг «уже играл», —
     * потому что один клип может нести несколько кадров, а тик за тиком их нужно
     * отбирать по времени. Так кадр срабатывает ровно один раз, даже если между
     * тиками анимация перескочит сразу через два эффекта.
     */
    private static List<MachineGunAnimation.Marker> markers = List.of();
    private static int markerCursor;

    /**
     * Клип и момент, на которых считается локатор дула для частиц.
     * <p>
     * Держим именно то, что было в момент срабатывания кадра, а не то, что
     * покажет рендером сейчас: у локатора есть смысл только в момент эффекта, а
     * к следующему тику анимация уже уехала вперёд.
     */
    @Nullable
    private static GltfAnimation markerClip;
    private static float markerSeconds;

    /** Модель, из которой берётся локатор. */
    @Nullable
    private static GemRenderGltfModel cachedModel;

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

    private MachineGunClientAnim() {
    }

    /**
     * Подтягивает реальные длины клипов из загруженной модели.
     * <p>
     * Должны вызываться только с клиента: {@link GemRenderGltfModel} живёт в
     * GemRender, а на сервере той нет.
     */
    public static void syncDurations(GemRenderGltfModel model) {
        cachedModel = model;

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
    }

    /**
     * Запрос на проигрывание клипа.
     * <p>
     * Клип выстрела — исключение: его перезапуск разрешён, и это не дёрганье, а
     * ровно то, как должна вести себя автоматика. Темп стрельбы (6 тиков) короче
     * клипа (6.67), поэтому следующий выстрел всегда приходит, пока отдача
     * ещё идёт. Если бы повтор отбрасывался, между выстрелами оставался бы
     * целый тик позы покоя, и ствол дёргался бы назад-вперёд дважды на выстрел.
     * <p>
     * Для reload и flip перезапуск по-прежнему запрещён: там он сорвал бы жест
     * на середине.
     *
     * @return {@code false}, если клип не удалось запустить
     */
    public static boolean trigger(String anim) {
        if (durationOf(anim) <= 0.0f) return false;

        if (anim.equals(current)) {
            if (!SHOT.equals(anim)) {
                return false;
            }
            // Перезапуск с нуля: пик отдачи должен приходиться на новый выстрел,
            // а не на произвольный кадр предыдущего.
            begin(anim);
            return true;
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

        // Помеченные кадры берём здесь: длины клипов к этому моменту уже
        // известны из прошлого кадра рендера.
        markers = MachineGunAnimation.particles(anim, durationOf(anim));
        markerCursor = 0;
        markerClip = null;
        markerSeconds = 0.0F;
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
                markers = List.of();
            markerCursor = 0;
            markerClip = null;
            markerSeconds = 0.0F;

            if (queued != null) {
                String next = queued;
                queued = null;
                begin(next);
            }
            return;
        }

        playMarkedParticles();
    }

    /** Сброс состояния — пушка убрана из руки или открыт какой-то экран. */
    public static void reset() {
        current = null;
        age = 0;
        preciseAge = 0.0;
        queued = null;
        markers = List.of();
        markerCursor = 0;
        markerClip = null;
        markerSeconds = 0.0F;
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

    /**
     * Проигрывает помеченные кадры, до которых дошла анимация.
     * <p>
     * Отбор идёт по интервалу {@code (было, стало]}, а не по флажку «уже
     * проиграно». Это даёт два свойства: кадр срабатывает ровно один раз, даже
     * если между тиками анимация перескочила сразу через два эффекта, и клип,
     * начатый с середины (смена длины при перезагрузке ресурсов), не выдаёт
     * всю разом.
     * <p>
     * Всё играется на клиенте — ровно как это делал GeckoLib: сервер о такой
     * метке не знает, а позиция оружия в руке у него всё равно другой.
     */
    private static void playMarkedParticles() {
        if (markers.isEmpty()) return;

        float now = (float) (preciseAge / 20.0);

        // Пока кадр не сработал, запоминаем, на какой секунде клипа мы находимся:
        // локатор дула считается именно на этом моменте, а не когда рендер
        // дойдёт до следующего тика.
        if (markerClip == null) {
            markerClip = currentClip();
            markerSeconds = now;
        }

        while (markerCursor < markers.size()) {
            MachineGunAnimation.Marker marker = markers.get(markerCursor);
            if (marker.time() > now) break;
            // Нижней границы нет намеренно: курсор и так не даёт кадру
            // сработать дважды, а вот маркер на 0.0 при строгом сравнении
            // отсекался бы навсегда — cursorTime в начале равен нулю же.
            // Именно на нуле и стоит выстрел: вспышка и щелчок в первый кадр.
            spawnParticle(marker);
            markerCursor++;
        }
    }

    @Nullable
    public static GltfAnimation currentClip() {
        GemRenderGltfModel model = cachedModel;
        return model != null && current != null ? model.animation(current) : null;
    }

    /** Модель, из которой берётся локатор. */
    @Nullable
    public static GemRenderGltfModel model() {
        return cachedModel;
    }

    private static void spawnParticle(MachineGunAnimation.Marker marker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE
                .getOptional(ResourceLocation.tryParse(marker.id()))
                .orElse(null);
        // В 1.21.1 ParticleOptions — это сам ParticleType у простых частиц,
        // отдельного класса-обёртки нет: неизвестно, SimpleParticleType ли это.
        if (!(type instanceof ParticleOptions options)) return;

        // Точка вылета берётся из модели: локатор с кости прогоняется через
        // текущий клип и домножается на матрицу, которой пушка нарисована в
        // руке. Формулой от позиции игрока она не заменяется — та на поворот
        // кисти и отдачу ствола не смотрит.
        //
        // В прицеле вспышка не нужна: точка вылета там у самой камеры, и
        // вспышка перекрыла бы весь круг прицела.
        if (MachineGunScope.isScoped()) return;

        Vec3 muzzle = MachineGunModel.worldMuzzle(cachedModel, markerClip, markerSeconds,
                marker.locator(), mc.gameRenderer.getMainCamera());
        if (muzzle == null) return;

        mc.level.addParticle(options, muzzle.x, muzzle.y, muzzle.z, 0.0D, 0.0D, 0.0D);
    }
}
