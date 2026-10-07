package com.trd.item.weapons.grenades;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.trd.main.MainRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
 * Звуки ударной гранаты, размеченные прямо в её экспорте анимаций.
 *
 * <p>Это общий код: файл нужен и серверу, и клиенту — см. раздел «Кто что
 * проигрывает» ниже.
 *
 * <h2>Почему этот файл, а не что-то в glTF</h2>
 * Модель рисуется через GemRender, и геколибовских контроллеров в проекте нет.
 * Экспорт анимаций через плагин геколиба — единственный формат, где у кадра есть
 * и время, и путь к звуку, и Blockbench умеет ставить их прямо на костях.
 * В glTF понятия «звук на кадре» нет вовсе. Поэтому разметка живёт в файле,
 * который блокбенч отдаёт рядом с моделью, а читается без геколиба — обычным json.
 * Та же схема, что у пушки в {@code MachineGunAnimation}; здесь из неё нужен
 * только блок {@code sound_effects} — частиц у гранаты нет.
 *
 * <h2>Кто что проигрывает</h2>
 * Звук чеки ({@link #PIN_PULL}) — <b>на клиенте</b>, и только у того, кто держит
 * гранату: момент «взял в руку» на сервере вообще не существует, а слышать
 * чужие щёлкающие чеки никому не нужно.
 * Звук броска ({@link #THROW}) — <b>на сервере</b>: бросок слышат все вокруг, и
 * {@code level.playSound} рассылает его nearby-игрокам. Сервер не знает, когда у
 * стрелка на экране проиграется клип, но знает, в каком тике игрок кинул, а
 * клип у обоих стартует в этот же тик — поэтому звук просто ставится в очередь
 * на свои тики.
 *
 * <h2>Почему времена не подгоняются под длину glTF</h2>
 * {@code animation_length} в геколибовском экспорте — это не конец движения, а
 * окно показа: у {@code pin_pull} там стоит {@code loop: hold_on_last_frame} и
 * 1.25 с, тогда как последний ключ в обеих форматах стоит на 1.0833 с. Ключи
 * лежат по одним и тем же секундам, так что масштабировать времена по
 * {@code glTF / json} нельзя — щелчок уехал бы к началу выдергивания чеки.
 * Единственная страховка — обрезка по длине клипа, и то на случай, если после
 * переэкспорта кадр окажется за концом анимации.
 */
public final class GrenadeIfAnimation {

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "animations/grenade_if.animation.json");

    private static final String SOUND_EFFECTS = "sound_effects";

    /** Выдёргивание чеки: играет, когда граната взята в руку. */
    public static final String PIN_PULL = "pin_pull";
    /** Замах и бросок: играет в момент броска. */
    public static final String THROW = "throw";

    /**
     * Помеченный кадр со звуком.
     *
     * @param time секунда от начала клипа
     * @param id   путь эффекта, как его завели в Blockbench
     */
    public record Marker(float time, String id) {
    }

    private static final List<Marker> NONE = List.of();

    /** Кэш разбора: клип → маркеры. */
    private static Map<String, List<Marker>> markers = Map.of();

    private static boolean loaded;

    private GrenadeIfAnimation() {
    }

    /**
     * Помеченные кадры со звуком, попавшие в клип.
     *
     * @param clip         имя клипа, как в glTF
     * @param clipDuration длина клипа из glTF, в секундах
     */
    public static List<Marker> sounds(String clip, float clipDuration) {
        load();

        List<Marker> source = markers.get(clip);
        if (source == null || source.isEmpty()) {
            return NONE;
        }

        // Клип короче, чем кадр, — звука на такой секунде уже не существует.
        // Без проверки он проигрался бы в момент, когда анимации ещё нет.
        List<Marker> out = new ArrayList<>(source.size());
        for (Marker marker : source) {
            if (marker.time() > clipDuration) {
                continue;
            }
            out.add(marker);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Проигрывает звуки клипа сразу, здесь и сейчас.
     * <p>
     * Раньше звуки броска ставились в очередь на серверные тики, и очередь нужна
     * была ровно для одного: попасть в ту же секунду клипа, в которую кадр
     * помечен в разметке. Клипа броска у модели нет и не планируется — бросок
     * мгновенный, граната исчезает из руки в тот же тик, — так что
     * синхронизироваться больше не с чем, а очередь только теряла звук: она
     * живёт в статике и целиком пропадала на перезагрузке ресурсов, ровно
     * посреди броска.
     * <p>
     * Позиция берётся здесь же: за четверть секунды, которую звук ждал бы в
     * очереди, игрок успевает уйти, и звук уехал бы вместе с ним — слышно было
     * бы не оттуда, откуда бросили.
     *
     * @param level  мир, из которого будет проигран звук
     * @param player бросивший; по нему же берётся слышимость
     * @param clip   имя клипа, как в glTF
     */
    public static void play(ServerLevel level, Player player, String clip) {
        for (Marker marker : sounds(clip, Float.MAX_VALUE)) {
            SoundEvent sound = sound(marker.id());
            if (sound == null) {
                continue;
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                    SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }

private static float soundPitch(ServerLevel level) {
        return 0.9F + level.random.nextFloat() * 0.2F;
    }

    /**
     * Звук по пути из блокбенча, либо {@code null}, если такого события нет.
     * <p>
     * В блокбенче легко перепутать местами звук и частицу — они ставятся через
     * один и тот же диалог, и в слоте {@code sound_effects} спокойно оказывается
     * путь несуществующего эффекта. Такой кадр просто пропускается: лучше
     * молчание, чем щелчок не в тот момент.
     */
    public static SoundEvent sound(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.get(location);
    }

    /** Перечитывает файл при перезагрузке ресурсов. */
    public static synchronized void invalidate() {
        loaded = false;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        Map<String, List<Marker>> found = new LinkedHashMap<>();

        try (BufferedReader reader = open()) {
            if (reader == null) {
                LOG.warn("{} not found: the impact grenade will play no animation-driven sounds", SOURCE);
            } else {
                parse(JsonParser.parseReader(reader).getAsJsonObject(), found);
            }
        } catch (Exception e) {
            LOG.warn("Failed to read {}: the impact grenade will play no animation-driven sounds",
                    SOURCE, e);
            found.clear();
        }

        markers = Map.copyOf(found);
    }

    /**
     * Открывает файл на том конце, который сейчас работает.
     * <p>
     * На клиенте ресурсы отдаёт {@code ResourceManager}, а на сервере
     * {@code Minecraft.getInstance()} — {@code null} и файла не отдаёт вовсе.
     * Поэтому там, где менеджера нет, читаем classpath'ом: файл нужен обоим
     * концам, бросок ставит в очередь сервер, чеку играет клиент.
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

        var in = GrenadeIfAnimation.class.getResourceAsStream("/assets/" + SOURCE.getPath());
        return in == null ? null : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static void parse(JsonObject root, Map<String, List<Marker>> found) {
        if (!root.has("animations") || !root.get("animations").isJsonObject()) {
            return;
        }

        JsonObject animations = root.getAsJsonObject("animations");
        for (String clip : animations.keySet()) {
            List<Marker> list = new ArrayList<>();
            collect(animations.getAsJsonObject(clip), list);
            list.sort(Comparator.comparingDouble(Marker::time));

            if (!list.isEmpty()) {
                found.put(clip, Collections.unmodifiableList(list));
            }
        }
    }

    /**
     * Разбирает блок помеченных кадров со звуком.
     * <p>
     * Ключ — строка с секундой, значение — объект с {@code effect}.
     */
    private static void collect(JsonObject body, List<Marker> out) {
        if (!body.has(SOUND_EFFECTS) || !body.get(SOUND_EFFECTS).isJsonObject()) {
            return;
        }

        JsonObject effects = body.getAsJsonObject(SOUND_EFFECTS);
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

            out.add(new Marker(time, marker.get("effect").getAsString()));
        }
    }
}