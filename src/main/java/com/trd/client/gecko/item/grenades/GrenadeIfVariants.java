package com.trd.client.gecko.item.grenades;

import com.mojang.logging.LogUtils;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Модель ударной гранаты и её текстуры, разложенные по видам.
 *
 * <p>Геометрия у всех ударных одна: та же граната, различается только цвет
 * корпуса. Один набор текстур на четыре вида поэтому собирается не в четыре
 * модели, а в варианты: при загрузке GemRender сшивает их в общий атлас и
 * отдаёт по {@link VariantUv} на каждую. Смена вида стоит ноль перезагрузок
 * модели и ноль лишних draw call — та же геометрия, та же палитра, другая ячейка.
 *
 * <p>Номер полосы задан {@link GrenadeIfType#ordinal()} и жёстко зашит в модель:
 * обычная ударная — нулевая, дальше по одной на фугасную, липучку и зажигательную.
 * Список вариантов и карта текстур строятся из одного {@link #TEXTURES},
 * поэтому разъехаться они не могут; сверху стоит проверка по
 * {@link GemRenderGltfModel#variantCount()}.
 *
 * <p><b>Липучка.</b> Своей 128×128 текстуры у неё пока нет — старая осталась
 * 16×16 спрайтом, — поэтому она берёт полосу обычной ударной. Когда текстура
 * появится, достаточно дописать её в {@link #TEXTURES}: ни список вариантов, ни
 * рендереры править не придётся.
 *
 * <h2>Смещение корня в клипах</h2>
 * Экспорт glTF из Blockbench кладёт <b>собственное смещение корневой кости</b> в
 * каналы перемещения анимации — там, где в её же json стоит честный ноль. Смещение
 * уже лежит в узле с мешем, так что в любой анимированной позе оно применяется
 * дважды, и граната улетает на полблока вверх. {@link #poseClip} это снимает,
 * поэтому правки не нужно повторять после каждого переэкспорта.
 *
 * <h2>Чека</h2>
 * У предмета чека есть — её видно, и её выдёргивает клип {@code pin_pull}. У
 * летящей сущности её нет: чеку выдернули при броске, обратно она не вернётся.
 * Прятать кость иначе нельзя — её вершины остаются в скине, и «убрать» их можно
 * только позой: {@link NodeHide} обнуляет масштаб кости, и всё, что к ней
 * привязано, схлопывается в точку.
 */
public final class GrenadeIfVariants {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Модель ударной гранаты: геометрия и клип анимации общие для всех видов. */
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/grenade_if.gltf");

    /** Текстура, прописанная в самой модели; она же нулевая полоса атласа. */
    private static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/grenade_if.png");

    /** Имя кости чеки в модели. */
    private static final String PIN_BONE = "pin";

    /** Насколько анимированная поза расходится с покойной, если совпадений не нашлось. */
    private static final float FLOAT_TOLERANCE = 1.0E-5F;

    /** Текстура на каждый вид гранаты. */
    private static final Map<GrenadeIfType, ResourceLocation> TEXTURES = new EnumMap<>(GrenadeIfType.class);

    /** Варианты в порядке {@link GrenadeIfType#ordinal()}: индекс списка = полоса в атласе. */
    private static final List<Map<ResourceLocation, ResourceLocation>> VARIANTS;

    @Nullable
    private static com.wf.gemrender.asset.ModelCache.Handle<GemRenderGltfModel> handle;

    /** Модель из кеша; нужна, чтобы строить из неё клипы. */
    @Nullable
    private static GemRenderGltfModel model;

    /** Смещение корневой кости, снятое с клипов; {@code null}, если снимать нечего. */
    @Nullable
    private static float[] rootOffset;

    /** Слот кости чеки; {@code -1}, если в модели такой кости нет или её нельзя двигать. */
    private static int pinSlot = -1;

    /** Кеш производных от модели. Живёт до перезагрузки ресурсов. */
    private static final Map<String, GltfAnimation> CLIPS = new HashMap<>();

    /** Клип покоя с прибитой чекой — то, чем рисуется летящая граната. */
    @Nullable
    private static GltfAnimation hiddenPin;

    /** Поколение модели, под которым собраны производные выше. */
    private static int cachedGeneration = -1;

    /** Виды, для которых уже пожаловались на отсутствие полосы в атласе. */
    private static final java.util.Set<GrenadeIfType> WARNED_BANDS = java.util.EnumSet.noneOf(GrenadeIfType.class);

    static {
        TEXTURES.put(GrenadeIfType.GRENADE_IF, BASE_TEXTURE);
        TEXTURES.put(GrenadeIfType.GRENADE_IF_HE, texture("grenade_if_he"));
        // Липучка: своей текстуры пока нет, см. javadoc класса.
        TEXTURES.put(GrenadeIfType.GRENADE_IF_SLIME, BASE_TEXTURE);
        TEXTURES.put(GrenadeIfType.GRENADE_IF_FIRE, texture("grenade_if_fire"));

        VARIANTS = Stream.of(GrenadeIfType.values())
                .map(TEXTURES::get)
                .map(tex -> Map.of(BASE_TEXTURE, tex))
                .toList();
    }

    private GrenadeIfVariants() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/" + name + ".png");
    }

    /**
     * Ставит модель в очередь на загрузку. Звать достаточно один раз, из
     * клиентской регистрации рендереров.
     * <p>
     * По одной модели {@code GemRenderModels.variants} кладёт билдер в общий
     * словарь по {@code putIfAbsent}, но каждый вызов возвращает новую ручку, а
     * нам нужна ровно одна: пока она грузится, гранаты просто не рисуются.
     * <p>
     * При перезагрузке ресурсов заново звать ничего не надо — ручка сама запроит
     * модель у кеша GemRender, а производные от неё пересоберутся в
     * {@link #model()} по смене поколения.
     */
    public static synchronized void load() {
        if (handle == null) {
            handle = GemRenderModels.variants(MODEL, MODEL, VARIANTS);
        }
    }

    /** Модель со сшитыми вариантами либо {@code null}, пока она ещё грузится. */
    @Nullable
    public static synchronized GemRenderGltfModel model() {
        load();
        GemRenderGltfModel loaded = handle != null ? handle.get() : null;

        // Производные живут по поколению кеша GemRender, а не по ссылке на модель:
        // после перезагрузки ресурсов та же самая модель приезжает заново с
        // обновлёнными текстурами, и закешированные клипы остались бы со старой
        // нумерацией слотов.
        int generation = GemRenderModels.generation();
        if (loaded != model || generation != cachedGeneration) {
            model = loaded;
            cachedGeneration = generation;
            CLIPS.clear();
            hiddenPin = null;
            WARNED_BANDS.clear();
            pinSlot = -1;
            rootOffset = null;

            if (loaded != null) {
                NodeTable table = loaded.layout().nodeTable();
                int slot = table.slotOfName(PIN_BONE);
                pinSlot = slot >= 0 && table.isPosable(slot) ? slot : -1;
                rootOffset = rootTranslation(loaded);

                // Полос в атласе должно быть ровно столько, сколько видов у
                // гранаты: номер полосы задан ordinal'ом перечисления. Меньше — и
                // часть видов молча нарисуется чужой текстурой.
                if (loaded.variantCount() < GrenadeIfType.values().length) {
                    LOGGER.warn("Impact grenade atlas holds {} band(s) for {} type(s): {}",
                            loaded.variantCount(), GrenadeIfType.values().length,
                            Stream.of(GrenadeIfType.values()).map(t -> t.name() + "=" + t.ordinal()).toList());
                }
            }
        }
        return model;
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
     * @param clip имя клипа, как в glTF
     */
    @Nullable
    public static synchronized GltfAnimation poseClip(String clip) {
        GemRenderGltfModel loaded = model();
        if (loaded == null) {
            return null;
        }

        GltfAnimation cached = CLIPS.get(clip);
        if (cached != null) {
            return cached;
        }

        GltfAnimation source = loaded.animation(clip);
        if (source == null) {
            return null;
        }

        GltfAnimation fixed = stripRootOffset(loaded, source);
        CLIPS.put(clip, fixed);
        return fixed;
    }

    /**
     * Убирает из клипа повтор смещения корневой кости.
     * <p>
     * Канал перемещения, который в любой свой кадр совпадает со смещением корня,
     * не двигает кость, а дублирует то, что и так уже лежит в узле с мешем.
     * Таких каналов в модели ровно один, и он записан экспортом Blockbench: в её
     * же json на этом месте честный ноль.
     * <p>
     * Правка дописывается <b>после</b> всех драйверов клипа, а не вместо них:
     * вычитать смещение нужно уже из того, что канал успел записать, иначе
     * нулевой кадр перебил бы коррекцию.
     */
    private static GltfAnimation stripRootOffset(GemRenderGltfModel loaded, GltfAnimation clip) {
        float[] root = rootOffset;
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
            LOGGER.info("Impact grenade clip {}: dropped a duplicated root offset ({}, {}, {})",
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
     * месте, а спрятать её можно только драйвером.
     * <p>
     * Взятый на нулевой секунде {@link GrenadeIfAnimation#PIN_PULL}, он совпадает
     * с позой покоя во всём, кроме чеки: все ключи клипа на 0.0 — нули и единицы,
     * то есть ровно исходные значения.
     */
    @Nullable
    public static synchronized GltfAnimation hiddenPin() {
        GemRenderGltfModel loaded = model();
        if (loaded == null) {
            return null;
        }
        if (hiddenPin != null) {
            return hiddenPin;
        }

        GltfAnimation source = poseClip(GrenadeIfAnimation.PIN_PULL);
        if (source == null) {
            return null;
        }
        if (pinSlot < 0) {
            // Кости чеки в модели нет — прятать нечего.
            hiddenPin = source;
            return source;
        }

        // NodeHide ставится последним: драйверы клипа применяются по порядку, и
        // обнуление масштаба после них гарантирует, что чека не вернётся ни на
        // одном кадре — включая те, где анимация трогает её сама.
        hiddenPin = source.with(NodeHide.of(loaded.layout().nodeTable(), pinSlot));
        return hiddenPin;
    }

    /**
     * Секунда последнего кадра клипа.
     * <p>
     * Нужна там, где предмет должен просто <b>стоять</b>: между анимациями это
     * последний кадр выдергивания чеки, а возвращать гранату в исходную позу
     * означало бы на глазах у игрока вдвигать чеку обратно. Передача времени
     * {@code null}-ом здесь не годится: {@code null} у GemRender — это поза покоя,
     * то есть ровно то, чего мы избегаем.
     */
    public static float endSeconds(@Nullable GltfAnimation clip) {
        return clip == null ? 0.0F : Math.max(0.0F, clip.duration() - 1.0E-3F);
    }

    /**
     * Полоса атласа конкретного вида гранаты.
     * <p>
     * Возвращает {@link VariantUv#NONE}, пока варианты не собрались или если
     * полосы для этого вида нет: {@code NONE} — это нулевая полоса, то есть
     * обычная ударная, и граната в худшем случае нарисуется не тем цветом.
     * <p>
     * Номер полосы берётся из {@link GrenadeIfType#ordinal()}, а не из позиции в
     * списке текстур: так его задаёт и сборочный список вариантов, и эта проверка,
     * и разъехаться они не могут.
     */
    public static VariantUv variant(@Nullable GrenadeIfType type) {
        GemRenderGltfModel loaded = model();
        if (loaded == null || type == null) return VariantUv.NONE;

        int band = type.ordinal();
        if (band >= loaded.variantCount()) {
            // Раз в кадр на каждый вид — это сотня строк в лог за секунду, а
            // диагностика нужна один раз.
            if (WARNED_BANDS.add(type)) {
                LOGGER.warn("Impact grenade variant {} for {} is out of range: the atlas holds {} band(s), " +
                        "drawing the base one. Bands are GrenadeIfType ordinals.",
                        band, type, loaded.variantCount());
            }
            return VariantUv.NONE;
        }
        return loaded.variant(band);
    }

    /** Тип снаряда по сущности — на клиенте тип сущности известен наверняка. */
    public static VariantUv variantOf(Entity entity) {
        return variant(GrenadeIfType.typeOf(entity.getType()));
    }
}