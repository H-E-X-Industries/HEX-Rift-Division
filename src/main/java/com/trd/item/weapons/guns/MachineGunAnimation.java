package com.trd.item.weapons.guns;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Помеченные кадры пушки из её геколибовского экспорта анимаций.
 *
 * <h2>Почему этот файл, а не что-то в glTF</h2>
 * Рендер модели давно на GemRender, и геколибовских контроллеров в проекте нет.
 * Но экспорт анимаций через блокбенч-плагин геколиба — единственный формат,
 * где у кадра есть <em>и</em> время, <em>и</em> локатор, <em>и</em> путь к
 * эффекту, и Blockbench умеет их ставить прямо на костях. В glTF понятия
 * «звук на кадре» нет вовсе, а {@code extras} при экспорте ещё и вытирается.
 * Поэтому разметка живёт в файле, который блокбенч отдаёт рядом с моделью, а
 * читается без геколиба — обычным json.
 *
 * <h2>Кто что проигрывает</h2>
 * Звуки — <b>на сервере</b>: их должен слышать весь уровень, а не только
 * стрелок. Сервер берёт отсюда времена и ставит их в очередь на свои тики, так
 * что звук совпадает с анимацией у всех, у кого она играет.
 * Вспышка — <b>на клиенте</b>: её позиция берётся из локатора на кости модели,
 * а локальная матрица предмета есть только у того, кто её рисует.
 *
 * <h2>Расхождение длин</h2>
 * Длина клипа в этом файле и в glTF может разойтись на десятые доли секунды.
 * Времена эффектов приводятся к длине из glTF по коэффициенту
 * {@code glTF / json}, иначе последний звук перезарядки уезжал бы за конец
 * анимации и не прозвучал бы никогда.
 */
public final class MachineGunAnimation {

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath("trd", "animations/ap_17.animation.json");

    private static final String SOUND_EFFECTS = "sound_effects";
    private static final String PARTICLE_EFFECTS = "particle_effects";

    /** Что за эффект. */
    public enum Kind {
        SOUND,
        PARTICLE
    }

    /**
     * Помеченный кадр.
     *
     * @param time    секунда от начала клипа
     * @param id      путь эффекта, как его завели в Blockbench
     * @param locator кость, к которой он привязан; может быть пустым
     */
    public record Marker(Kind kind, float time, String id, String locator) {
    }

    private static final List<Marker> NONE = List.of();

    /** Кэш разбора: клип → маркеры, клип → длина в json. */
    private static Map<String, List<Marker>> markers = Map.of();
    private static Map<String, Float> lengths = Map.of();

    private static boolean loaded;

    private MachineGunAnimation() {
    }

    /**
     * Маркеры клипа, приведённые к его настоящей длине.
     *
     * @param clip          имя клипа, как в glTF
     * @param clipDuration  длина клипа из glTF, в секундах; на неё подгоняются
     *                      времена, потому что json может описывать клип
     *                      чуть иначе
     */
    public static List<Marker> markers(String clip, float clipDuration) {
        load();

        List<Marker> source = markers.get(clip);
        if (source == null || source.isEmpty()) {
            return NONE;
        }

        float declared = lengths.getOrDefault(clip, 0.0F);
        if (declared <= 0.0F || clipDuration <= 0.0F || Math.abs(declared - clipDuration) < 1.0E-3F) {
            return source;
        }

        float scale = clipDuration / declared;
        List<Marker> scaled = new ArrayList<>(source.size());
        for (Marker marker : source) {
            scaled.add(new Marker(marker.kind(), marker.time() * scale, marker.id(), marker.locator()));
        }
        return Collections.unmodifiableList(scaled);
    }

    /** Только звуки клипа — их ставит в очередь сервер. */
    public static List<Marker> sounds(String clip, float clipDuration) {
        return filter(markers(clip, clipDuration), Kind.SOUND);
    }

    /** Только частицы клипа — их спавнит клиент по локатору. */
    public static List<Marker> particles(String clip, float clipDuration) {
        return filter(markers(clip, clipDuration), Kind.PARTICLE);
    }

    private static List<Marker> filter(List<Marker> source, Kind kind) {
        List<Marker> out = null;
        for (Marker marker : source) {
            if (marker.kind() != kind) continue;
            if (out == null) out = new ArrayList<>();
            out.add(marker);
        }
        return out == null ? NONE : Collections.unmodifiableList(out);
    }

    /** Перечитывает файл при перезагрузке ресурсов. */
    public static synchronized void invalidate() {
        loaded = false;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        Map<String, List<Marker>> found = new LinkedHashMap<>();
        Map<String, Float> declared = new LinkedHashMap<>();

        try (BufferedReader reader = open()) {
            if (reader == null) {
                LOG.warn("{} not found: the machine gun will play no animation-driven sounds", SOURCE);
            } else {
                parse(JsonParser.parseReader(reader).getAsJsonObject(), found, declared);
            }
        } catch (Exception e) {
            LOG.warn("Failed to read {}: the machine gun will play no animation-driven sounds",
                    SOURCE, e);
            found.clear();
            declared.clear();
        }

        markers = Map.copyOf(found);
        lengths = Map.copyOf(declared);
    }

    /**
     * Открывает файл на том конце, который сейчас работает.
     * <p>
     * На клиенте ресурсы отдаёт {@code ResourceManager}, а на сервере
     * {@code Minecraft.getInstance()} — {@code null} и файла не отдаёт вовсе.
     * Поэтому там, где менеджера нет, читаем classpath'ом: анимация нужна обоим
     * концам, звуки ставит сервер, вспышку спавнит клиент.
     */
    private static BufferedReader open() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client != null && client.getResourceManager() != null) {
            var found = client.getResourceManager().getResource(SOURCE);
            if (found.isEmpty()) {
                return null;
            }
            try {
                return found.get().openAsReader();
            } catch (java.io.IOException e) {
                return null;
            }
        }

        var in = MachineGunAnimation.class.getResourceAsStream("/assets/" + SOURCE.getPath());
        return in == null ? null : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static void parse(JsonObject root, Map<String, List<Marker>> found, Map<String, Float> declared) {
        if (!root.has("animations") || !root.get("animations").isJsonObject()) {
            return;
        }

        JsonObject animations = root.getAsJsonObject("animations");
        for (String clip : animations.keySet()) {
            JsonObject body = animations.getAsJsonObject(clip);

            declared.put(clip, readLength(body));

            List<Marker> list = new ArrayList<>();
            collect(body, SOUND_EFFECTS, Kind.SOUND, list);
            collect(body, PARTICLE_EFFECTS, Kind.PARTICLE, list);
            list.sort(Comparator.comparingDouble(Marker::time));

            if (!list.isEmpty()) {
                found.put(clip, Collections.unmodifiableList(list));
            }
        }
    }

    private static float readLength(JsonObject body) {
        if (!body.has("animation_length") || !body.get("animation_length").isJsonPrimitive()) {
            return 0.0F;
        }
        try {
            return body.get("animation_length").getAsFloat();
        } catch (Exception e) {
            return 0.0F;
        }
    }

    /**
     * Разбирает блок помеченных кадров.
     * <p>
     * Ключ — строка с секундой, значение — объект с {@code effect} и, опционально,
     * {@code locator}.
     */
    private static void collect(JsonObject body, String block, Kind kind, List<Marker> out) {
        if (!body.has(block) || !body.get(block).isJsonObject()) {
            return;
        }

        JsonObject effects = body.getAsJsonObject(block);
        for (Map.Entry<String, JsonElement> entry : effects.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject marker = entry.getValue().getAsJsonObject();
            if (!marker.has("effect") || !marker.get("effect").isJsonPrimitive()) {
                continue;
            }

            String locator = marker.has("locator") && marker.get("locator").isJsonPrimitive()
                    ? marker.get("locator").getAsString()
                    : "";

            out.add(new Marker(kind, time, marker.get("effect").getAsString(), locator));
        }
    }

    /**
     * Звук по пути из блокбенча.
     * <p>
     * В блокбенче легко перепутать местами звук и частицу — они ставятся через
     * один и тот же диалог, и в слоте {@code sound_effects} спокойно оказывается
     * {@code trd:shot}. Такой пути среди звуков нет, и клик просто не играл.
     * Поэтому неизвестный путь в слоте звука заменяется на звук выстрела самой
     * пушки: лучше лишний щелчок, чем тишина.
     */
    public static SoundEvent sound(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            SoundEvent found = BuiltInRegistries.SOUND_EVENT.get(location);
            if (found != null) {
                return found;
            }
        }
        return com.trd.sound.ModSounds.TURRET_FIRE.isBound()
                ? com.trd.sound.ModSounds.TURRET_FIRE.get()
                : SoundEvents.GENERIC_EXPLODE.value();
    }
}
