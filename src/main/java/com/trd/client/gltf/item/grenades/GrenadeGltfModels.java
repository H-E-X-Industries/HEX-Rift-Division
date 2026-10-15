package com.trd.client.gltf.item.grenades;

import com.mojang.logging.LogUtils;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Общая часть glTF-моделей гранат: по {@link ResourceLocation} отдаёт саму модель,
 * клипы с поправками и позу покоя со спрятанной чекой.
 *
 * <p>Раньше всё это лежало в {@link GrenadeIfVariants} и было заточено под один
 * перечис. Обычным гранатам нужна ровно та же механика — они рисуются той же
 * геометрией со своей зашитой текстурой, — но перечис у них другой и заводить
 * ради него второй почти такой же класс было бы дублированием. Здесь ключом служит
 * сам путь к модели, а перечис остаётся на стороне выбора: что за вид пришёл.
 *
 * <p><b>Почему у каждого вида свой файл.</b> У всех семи видов классических гранат
 * геометрия одна, и отличать их можно было бы полосой в атласе, который GemRender
 * собирает при загрузке. На практике это вышло ненадёжно: базовая граната брала
 * нулевую полосу и была в порядке, а остальные виды не отличались от плоского
 * спрайта. Поэтому у каждого вида свой файл, а текстура в нём прописана явно — в
 * {@code images.extras.resourceLocation}, то есть обычным путём, которым GemRender
 * ищет текстуру без всякой сборки атласа. Геометрия у файлов одна, и отличаются
 * они одной строкой — путём к текстуре.
 *
 * <p><b>Смещение корня в клипах.</b> Экспорт glTF из Blockbench кладёт
 * <b>собственное смещение корневой кости</b> в каналы перемещения анимации — там,
 * где в её же json стоит честный ноль. Смещение уже лежит в узле с мешем, так что
 * в любой анимированной позе оно применяется дважды, и граната улетает на полблока
 * вверх, а к концу клипа падает обратно. {@link #poseClip} это снимает, поэтому
 * правки не нужно повторять после каждого переэкспорта.
 *
 * <p><b>Чека.</b> У предмета чека есть — её видно, и её выдёргивает клип
 * {@code pin_pull}. У летящей сущности её нет: чеку выдернули при броске, обратно
 * она не вернётся. Прятать кость иначе нельзя — её вершины остаются в скине, и
 * «убрать» их можно только позой: {@link NodeHide} обнуляет масштаб кости, и всё,
 * что к ней привязано, схлопывается в точку.
 */
public final class GrenadeGltfModels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Имя кости чеки в модели. */
    private static final String PIN_BONE = "pin";

    /** Насколько анимированная поза расходится с покойной, если совпадений не нашлось. */
    private static final float FLOAT_TOLERANCE = 1.0E-5F;

    /** Всё, что строится поверх модели и живёт до перезагрузки ресурсов. */
    private static final class Derived {

        @Nullable
        GemRenderGltfModel model;

        /** Поколение кеша, под которым собраны клипы и слот чеки. */
        int generation = -1;

        /** Загружалась ли модель хоть раз: нужно, чтобы отличить старт от провала. */
        boolean seen;

        /** Слот кости чеки; {@code -1}, если её нет в модели или она не двигается. */
        int pinSlot = -1;

        /** Смещение корневой кости, снятое с клипов; {@code null}, если снимать нечего. */
        @Nullable
        float[] rootOffset;

        final Map<String, GltfAnimation> clips = new HashMap<>();

        @Nullable
        GltfAnimation hiddenPin;
    }

    /**
     * Кеш производных — по пути к модели, а не по ссылке на неё: после перезагрузки
     * ресурсов модель приезжает заново с обновлённой текстурой, и закешированные
     * клипы остались бы со старой нумерацией слотов.
     */
    private static final Map<ResourceLocation, Derived> DERIVED = new HashMap<>();

    private GrenadeGltfModels() {
    }

    /**
     * Путь к glTF-модели гранаты по её имени.
     * <p>
     * Имя совпадает с именем текстуры и файла модели, поэтому расходиться им
     * негде: текстуры приходят из того же блока в Blockbench, и всё, что нужно
     * поменять при появлении своей, — одну строку в вызывающем коде.
     *
     * @param name имя вида, например {@code grenade_if} или {@code grenadesmart}
     */
    public static ResourceLocation modelId(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/" + name + ".gltf");
    }

    /** Модель по пути, либо {@code null}, пока она грузится. */
    @Nullable
    public static synchronized GemRenderGltfModel model(ResourceLocation id) {
        Derived derived = DERIVED.computeIfAbsent(id, key -> new Derived());
        GemRenderGltfModel loaded = GemRenderModels.get(id);
        int generation = GemRenderModels.generation();

        if (loaded != derived.model || generation != derived.generation) {
            derived.model = loaded;
            derived.generation = generation;
            derived.clips.clear();
            derived.hiddenPin = null;
            derived.pinSlot = -1;
            derived.rootOffset = null;

            if (loaded != null) {
                derived.seen = true;

                NodeTable table = loaded.layout().nodeTable();
                int slot = table.slotOfName(PIN_BONE);
                derived.pinSlot = slot >= 0 && table.isPosable(slot) ? slot : -1;
                derived.rootOffset = rootTranslation(loaded);
            } else if (derived.seen) {
                // Модель грузится асинхронно, и к первому кадру её может ещё не
                // быть — это молчание нормально. А вот пропажа модели, которая уже
                // была загружена, — это провал кеша на перезагрузке ресурсов: сам
                // он не рассосётся, и вид до следующего перезапуска молча рисуется
                // плоским спрайтом из models/item. Поэтому такое уже не молчим.
                LOGGER.error("Grenade model {} stopped loading after the resource reload;"
                        + " it draws nothing until the game is restarted", id);
            }
        }
        return loaded;
    }

    /**
     * Смещение корневой кости — узла, на котором висит меш.
     * <p>
     * Оно нужно ровно одно: чтобы отличить «канал анимации честно двигает кость»
     * от «канал анимации повторяет смещение корня».
     */
    @Nullable
    private static float[] rootTranslation(GemRenderGltfModel loaded) {
        for (var node : loaded.layout().nodes()) {
            if (node.getMeshModels() != null && !node.getMeshModels().isEmpty()) {
                float[] translation = node.getTranslation();
                return translation == null || translation.length < 3 ? null : translation.clone();
            }
        }
        return null;
    }

    /**
     * Клип из модели, с которого снято смещение корня.
     *
     * @param id   путь к модели
     * @param clip имя клипа, как в glTF
     */
    @Nullable
    public static synchronized GltfAnimation poseClip(ResourceLocation id, String clip) {
        GemRenderGltfModel loaded = model(id);
        if (loaded == null) {
            return null;
        }

        Derived derived = DERIVED.get(id);
        if (derived == null) {
            return loaded.animation(clip);
        }

        GltfAnimation cached = derived.clips.get(clip);
        if (cached != null) {
            return cached;
        }

        GltfAnimation source = loaded.animation(clip);
        if (source == null) {
            return null;
        }

        GltfAnimation fixed = stripRootOffset(loaded, derived, source);
        derived.clips.put(clip, fixed);
        return fixed;
    }

    /**
     * Убирает из клипа повтор смещения корневой кости.
     * <p>
     * Канал перемещения, который в любой свой кадр совпадает со смещением корня,
     * не двигает кость, а дублирует то, что и так уже лежит в узле с мешем.
     * Таких каналов в модели ровно один, и он записан экспортом Blockbench: в её
     * же json на этом месте честный ноль. Из-за него анимация играла не на месте,
     * а предмет на её конце сидел выше, чем в покое.
     * <p>
     * Правка дописывается <b>после</b> всех драйверов клипа, а не вместо них:
     * вычитать смещение нужно уже из того, что канал успел записать, иначе
     * нулевой кадр перебил бы коррекцию.
     */
    private static GltfAnimation stripRootOffset(GemRenderGltfModel loaded, Derived derived, GltfAnimation clip) {
        float[] root = derived.rootOffset;
        if (root == null) {
            return clip;
        }

        NodeTable table = loaded.layout().nodeTable();
        float[] state = table.newScratch();
        table.resetToRest(state);
        clip.apply(0.0F, state);

        List<PoseDriver> drivers = null;
        for (int slot = 0; slot < table.nodeCount(); slot++) {
            int offset = table.offsetFor(slot, "translation");
            if (offset < 0 || !table.isPosable(slot)) {
                continue;
            }

            float dx = state[offset] - table.restTranslation(slot, 0);
            float dy = state[offset + 1] - table.restTranslation(slot, 1);
            float dz = state[offset + 2] - table.restTranslation(slot, 2);

            boolean echoesRoot = Math.abs(dx - root[0]) < FLOAT_TOLERANCE
                    && Math.abs(dy - root[1]) < FLOAT_TOLERANCE
                    && Math.abs(dz - root[2]) < FLOAT_TOLERANCE;
            if (!echoesRoot) {
                continue;
            }

            if (drivers == null) {
                drivers = new ArrayList<>(clip.drivers());
            }
            drivers.add(new TranslationFix(offset, -dx, -dy, -dz));
            LOGGER.info("Grenade clip {}: dropped a duplicated root offset ({}, {}, {})",
                    clip.name(), dx, dy, dz);
        }

        return drivers == null
                ? clip
                : GltfAnimation.procedural(clip.name(), clip.duration(), drivers.toArray(new PoseDriver[0]));
    }

    /** Вычитает из перемещения кости заранее найденную дельту. */
    private record TranslationFix(int offset, float x, float y, float z) implements PoseDriver {
        @Override
        public void apply(float time, float[] state) {
            state[offset] += x;
            state[offset + 1] += y;
            state[offset + 2] += z;
        }

        @Override
        public float cycleSeconds() {
            return 0.0F;
        }

        @Override
        public int offset() {
            return offset;
        }
    }

    /**
     * Поза покоя модели с невидимой чекой — то, чем рисуется летящая граната.
     * <p>
     * Клип нужен вместо {@code null}, хотя анимация не идёт: {@code null} у
     * GemRender означает исходные позы костей без драйверов, то есть с чекой на
     * месте, а спрятать её можно только драйвером. Взятый на нулевой секунде
     * клипе выдёргивания он совпадает с позой покоя во всём, кроме чеки: все ключи
     * такого клипа на 0.0 — нули и единицы, то есть ровно исходные значения.
     *
     * @param id       путь к модели
     * @param pinClip  клип, с которого берётся поза (обычно {@code pin_pull})
     */
    @Nullable
    public static synchronized GltfAnimation hiddenPin(ResourceLocation id, String pinClip) {
        GemRenderGltfModel loaded = model(id);
        if (loaded == null) {
            return null;
        }

        Derived derived = DERIVED.get(id);
        if (derived == null || derived.hiddenPin != null) {
            return derived != null ? derived.hiddenPin : null;
        }

        GltfAnimation source = poseClip(id, pinClip);
        if (source == null) {
            return null;
        }
        if (derived.pinSlot < 0) {
            // Кости чеки в модели нет — прятать нечего.
            derived.hiddenPin = source;
            return source;
        }

        // NodeHide ставится последним: драйверы клипа применяются по порядку, и
        // обнуление масштаба после них гарантирует, что чека не вернётся ни на
        // одном кадре — включая те, где анимация трогает её сама.
        derived.hiddenPin = source.with(NodeHide.of(loaded.layout().nodeTable(), derived.pinSlot));
        return derived.hiddenPin;
    }

    /**
     * Секунда последнего кадра клипа.
     * <p>
     * Нужна там, где предмет должен просто <b>стоять</b>. Передача времени
     * {@code null}-ом здесь не годится: {@code null} у GemRender — это поза покоя,
     * то есть ровно то, что нужно, когда анимации не идёт.
     */
    public static float endSeconds(@Nullable GltfAnimation clip) {
        return clip == null ? 0.0F : Math.max(0.0F, clip.duration() - 1.0E-3F);
    }
}
