package com.trd.client.gecko.item.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeHide;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.resources.ResourceLocation;

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
 * <h2>Чека</h2>
 * Чека на предмете есть — её видно, как её и выдёргивает клип {@code pin_pull}, —
 * и спрятана она только у летящей сущности, где уже нечего выдёргивать.
 * Скрыть кость иначе нельзя: её вершины остаются в скине, и «спрятать» их можно
 * только позой — {@link NodeHide} обнуляет масштаб кости, и всё, что к ней
 * привязано, схлопывается в точку. Подробности в {@link #hiddenPin}.
 */
public final class GrenadeIfVariants {

    /** Модель ударной гранаты: геометрия и оба клипа анимаций общие для всех видов. */
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/grenade_if.gltf");

    /** Текстура, прописанная в самой модели; она же нулевая полоса атласа. */
    private static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/grenade_if.png");

    /** Имя кости чеки в модели. */
    private static final String PIN_BONE = "pin";

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    /** Текстура на каждый вид гранаты. */
    private static final Map<GrenadeIfType, ResourceLocation> TEXTURES = new EnumMap<>(GrenadeIfType.class);

    /** Варианты в порядке {@link GrenadeIfType#ordinal()}: индекс списка = полоса в атласе. */
    private static final List<Map<ResourceLocation, ResourceLocation>> VARIANTS;

    @Nullable
    private static ModelCache.Handle<GemRenderGltfModel> handle;

    /** Модель из кеша; нужна, чтобы строить клип со скрытой чекой. */
    @Nullable
    private static GemRenderGltfModel model;

    /** Слот кости чеки; {@code -1}, если в модели такой кости нет или её нельзя двигать. */
    private static int pinSlot = -1;

    /** Кеш клипа с прибитой чекой. Живёт до перезагрузки ресурсов. */
    private static final Map<String, GltfAnimation> HIDDEN_PIN = new HashMap<>();

    /** Виды, для которых уже пожаловались на отсутствие полосы в атласе. */
    private static final java.util.Set<GrenadeIfType> WARNED_BANDS = java.util.EnumSet.noneOf(GrenadeIfType.class);

    /** Поколение модели, под которым собраны {@link #HIDDEN_PIN} и {@link #pinSlot}. */
    private static int cachedGeneration = -1;

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
     * модель у кеша GemRender, а производные от неё вещи пересоберутся в
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

        // Кеш производных живёт по поколению кеша GemRender, а не по ссылке на
        // модель: после перезагрузки ресурсов та же самая модель приезжает
        // заново с обновлёнными текстурами, и закешированные клипы обнулили бы
        // масштаб кости по старой нумерации слотов.
        int generation = GemRenderModels.generation();
        if (loaded != model || generation != cachedGeneration) {
            model = loaded;
            cachedGeneration = generation;
            HIDDEN_PIN.clear();
            WARNED_BANDS.clear();
            pinSlot = -1;

            if (loaded != null) {
                NodeTable table = loaded.layout().nodeTable();
                int slot = table.slotOfName(PIN_BONE);
                pinSlot = slot >= 0 && table.isPosable(slot) ? slot : -1;

                // Полос в атласе должно быть ровно столько, сколько видов у
                // гранаты: номер полосы задан ordinal'ом перечисления. Меньше —
                // и часть видов молча нарисуется чужой текстурой, поэтому об этом
                // стоит сказать в лог, а не полагаться на то, что заметят.
                if (loaded.variantCount() < GrenadeIfType.values().length) {
                    LOGGER.warn("Impact grenade atlas holds {} band(s) for {} type(s): {}",
                            loaded.variantCount(), GrenadeIfType.values().length,
                            java.util.Arrays.stream(GrenadeIfType.values())
                                    .map(t -> t.name() + "=" + t.ordinal())
                                    .toList());
                }
            }
        }
        return model;
    }

    /**
     * Поза покоя модели с невидимой чекой — то, чем рисуется летящая граната.
     * <p>
     * Клип нужен вместо {@code null}, хотя анимации не идёт: {@code null} у
     * GemRender означает исходные позы костей без драйверов, то есть с чекой на
     * месте, а спрятать её можно только драйвером.
     * <p>
     * Взятый на нулевой секунде {@link GrenadeIfAnimation#PIN_PULL}, он совпадает
     * с позой покоя во всём, кроме чеки: все ключи клипа на 0.0 — нули и единицы,
     * то есть ровно исходные значения. Отдельный пустой клип ради этого не нужен,
     * а время можно оставить нулевым.
     */
    @Nullable
    public static synchronized GltfAnimation hiddenPin() {
        GemRenderGltfModel loaded = model();
        if (loaded == null) {
            return null;
        }

        GltfAnimation cached = HIDDEN_PIN.get(PIN_BONE);
        if (cached != null) {
            return cached;
        }

        GltfAnimation source = loaded.animation(GrenadeIfAnimation.PIN_PULL);
        if (source == null) {
            return null;
        }

        // Без кости чеки в модели прятать нечего, и клип отдаётся как есть.
        if (pinSlot < 0) {
            HIDDEN_PIN.put(PIN_BONE, source);
            return source;
        }

        // NodeHide ставится последним: драйверы клипа применяются по порядку, и
        // обнуление масштаба после них гарантирует, что чека не вернётся ни на
        // одном кадре — включая те, где анимация трогает её сама.
        GltfAnimation hidden = source.with(NodeHide.of(loaded.layout().nodeTable(), pinSlot));
        HIDDEN_PIN.put(PIN_BONE, hidden);
        return hidden;
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
            // Раз в кадр на каждый вид — это сотня строк в логе за секунду, а
            // диагностика тут нужна один раз: молчащая оговорка «сломанные
            // варианты» хуже, чем одна строка в лог.
            if (WARNED_BANDS.add(type)) {
                LOGGER.warn("Impact grenade variant {} for {} is out of range: the atlas holds {} band(s), " +
                        "drawing the base one. Bands are GrenadeIfType ordinals.",
                        band, type, loaded.variantCount());
            }
            return VariantUv.NONE;
        }
        return loaded.variant(band);
    }
}