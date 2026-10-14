package com.trd.client.gltf.item.tools;

import com.mojang.logging.LogUtils;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
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
 * Модель литой кирки на glTF и всё, что из неё вытаскивается один раз.
 *
 * <h2>Одна модель на обе кирки</h2>
 * Обе литые кирки — одна и та же геометрия, различаются они только текстурой, так
 * что файл модели общий: {@code assets/trd/models/item/cast_pickaxe.gltf}. Текстура
 * в модель не зашита, её подставляет рендерер на лету через
 * {@link CastPickaxeGltfAppearance}, поэтому дублировать файл ради второй
 * текстуры не нужно.
 *
 * <h2>Кэш</h2>
 * Кэш keyed по {@link ResourceLocation}, а не по самой модели: после перезагрузки
 * ресурсов модель приезжает заново, и клипы, сохранённые при старой, держали бы
 * в себе индексы костей из прошлой генерации. Пересбор кэша ловится по
 * {@link GemRenderModels#generation()}.
 */
public final class CastPickaxeGltfModels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Насколько отличается перемещение кости от её покоя, чтобы считать это
     * продублированным смещением корня. Тот же допуск, что у гранат.
     */
    private static final float FLOAT_TOLERANCE = 1.0E-5F;

    /** Общий файл модели обеих кирок. */
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/cast_pickaxe.gltf");

    /** Вытащенное из модели: клипы, из которых убран сдвиг корня. */
    private static final class Derived {
        @Nullable
        GemRenderGltfModel model;
        int generation = -1;
        boolean seen;
        @Nullable
        float[] rootOffset;
        final Map<String, GltfAnimation> clips = new HashMap<>();
    }

    private static final Map<ResourceLocation, Derived> DERIVED = new HashMap<>();

    private CastPickaxeGltfModels() {
    }

    /**
     * Модель кирки либо {@code null}, пока она грузится.
     *
     * <p>Пока модели нет, рендер ничего не рисует — это штатное состояние на
     * первых кадрах после входа в мир, а не поломка.
     */
    @Nullable
    public static synchronized GemRenderGltfModel model() {
        Derived derived = DERIVED.computeIfAbsent(MODEL, key -> new Derived());
        GemRenderGltfModel loaded = GemRenderModels.get(MODEL);
        int generation = GemRenderModels.generation();

        if (loaded != derived.model || generation != derived.generation) {
            derived.model = loaded;
            derived.generation = generation;
            derived.clips.clear();
            derived.rootOffset = null;

            if (loaded != null) {
                derived.seen = true;
                derived.rootOffset = rootTranslation(loaded);
            } else if (derived.seen) {
                LOGGER.error("Cast pickaxe model {} stopped loading after the resource reload;"
                        + " it draws nothing until the game is restarted", MODEL);
            }
        }
        return loaded;
    }

    /**
     * Сдвиг корневой ноды модели — тот, что блокбенч записывает в анимацию
     * повторно, из-за чего модель прыгает на пол-блока при старте клипа.
     *
     * <p>Берётся у первой ноды с мешем: в этой модели меш сидит на узле со
     * сдвигом {@code [0, 0.14375, 0]}, а анимированная кость — его ребёнок без
     * собственного сдвига.
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
     * Клип из модели, из которого убран сдвиг корня.
     *
     * @return {@code null}, если модели ещё нет либо такого клипа в ней нет
     */
    @Nullable
    public static synchronized GltfAnimation clip(String name) {
        GemRenderGltfModel loaded = model();
        if (loaded == null) return null;

        Derived derived = DERIVED.get(MODEL);
        if (derived == null) return null;

        GltfAnimation cached = derived.clips.get(name);
        if (cached != null) return cached;

        GltfAnimation source = loaded.animation(name);
        if (source == null) {
            LOGGER.warn("Cast pickaxe model {} has no clip named '{}'", MODEL, name);
            return null;
        }

        GltfAnimation fixed = stripRootOffset(loaded, derived, source);
        derived.clips.put(name, fixed);
        return fixed;
    }

    /**
     * Убирает из клипа перемещение, повторяющее сдвиг корневой ноды.
     *
     * <p>Экспорт из блокбенча пишет в канал перемещения анимированной кости её
     * собственное положение в иерархии. В покое такая кость стоит в нуле, а
     * клип переносит её наверх — и модель прыгает вверх ровно на этот сдвиг.
     * {@code holding} так и ведёт себя: первая его точка — 0.14375 блока, то
     * есть 9 пикселей из 34, на которые кирка подскакивает при полном заряде.
     *
     * <p>Вычитается только та часть, что в точности совпадает со сдвигом корня
     * и тем самым является его повтором. Всё остальное движение — авторское и
     * остаётся как есть: у {@code holding} поверх сдвига есть ещё лёгкое
     * покачивание почти в пиксель, и оно нужно.
     */
    private static GltfAnimation stripRootOffset(GemRenderGltfModel loaded, Derived derived, GltfAnimation clip) {
        float[] root = derived.rootOffset;
        if (root == null) return clip;

        NodeTable table = loaded.layout().nodeTable();
        float[] state = table.newScratch();
        table.resetToRest(state);
        clip.apply(0.0F, state);

        List<PoseDriver> drivers = null;
        for (int slot = 0; slot < table.nodeCount(); slot++) {
            int offset = table.offsetFor(slot, "translation");
            if (offset < 0 || !table.isPosable(slot)) continue;

            float dx = state[offset] - table.restTranslation(slot, 0);
            float dy = state[offset + 1] - table.restTranslation(slot, 1);
            float dz = state[offset + 2] - table.restTranslation(slot, 2);

            boolean echoesRoot = Math.abs(dx - root[0]) < FLOAT_TOLERANCE
                    && Math.abs(dy - root[1]) < FLOAT_TOLERANCE
                    && Math.abs(dz - root[2]) < FLOAT_TOLERANCE;
            if (!echoesRoot) continue;

            if (drivers == null) drivers = new ArrayList<>(clip.drivers());
            // Драйвер добавляется после остальных, а не заменяет их: иначе
            // покачивание из той же поправки вычиталось бы само.
            drivers.add(new TranslationFix(offset, -dx, -dy, -dz));
            LOGGER.info("Cast pickaxe clip {}: dropped a duplicated root offset ({}, {}, {})",
                    clip.name(), dx, dy, dz);
        }
        return drivers == null ? clip
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
}