package com.trd.client.gecko.entity.bullets;

import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.entity.weapons.bullets.TurretBulletEntity.AmmoType;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Пять текстур пули — по одной на тип патрона — одной моделью.
 * <p>
 * В геколибе это было пять geo-моделей, и тип пули выбирался прямо в
 * {@code getTextureResource}. На glTF так не выйдет: геометрия у всех вариантов
 * одна, и различаться может только текстура. GemRender для этого умеет
 * <i>варианты</i>: при загрузке модели он сшивает все варианты в один атлас и
 * возвращает по {@link VariantUv} на каждый. Смещение этого UV выбирается при
 * отрисовке, то есть смена типа патрона не стоит ни одной перезагрузки модели и
 * ни одного лишнего draw call — та же геометрия, та же палитра, другая ячейка.
 * <p>
 * Порядок полос задан {@link AmmoType#ordinal()} и жёстко зашит в модель:
 * нулевая полоса — обычный патрон, дальше по одному на бронебойный, полый,
 * зажигательный и радио. Список вариантов и палитра строятся из одной карты
 * {@link #TEXTURES}, поэтому разъехаться они не могут; сверху стоит проверка
 * по {@link GemRenderGltfModel#variantCount()}.
 */
public final class TurretBulletVariants {

    /** Модель пули: геометрия общая для всех типов патрона. */
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet.gltf");

    /** Текстура обычного патрона: она же в glTF, и она же нулевая полоса. */
    private static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/turret_bullet.png");

    private static final Map<AmmoType, ResourceLocation> TEXTURES = new EnumMap<>(AmmoType.class);

    /** Варианты в порядке {@link AmmoType#ordinal()}: индекс списка = полоса в атласе. */
    private static final List<Map<ResourceLocation, ResourceLocation>> VARIANTS;

    @Nullable
    private static ModelCache.Handle<GemRenderGltfModel> handle;

    static {
        TEXTURES.put(AmmoType.NORMAL, BASE_TEXTURE);
        TEXTURES.put(AmmoType.PIERCING, texture("turret_bullet_ap"));
        TEXTURES.put(AmmoType.HOLLOW, texture("turret_bullet_hollow"));
        TEXTURES.put(AmmoType.INCENDIARY, texture("turret_bullet_fire"));
        TEXTURES.put(AmmoType.RADIO, texture("turret_bullet_radio"));

        VARIANTS = Stream.of(AmmoType.values())
                .map(TEXTURES::get)
                .map(tex -> Map.of(BASE_TEXTURE, tex))
                .toList();
    }

    private TurretBulletVariants() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * Ставит варианты в очередь на загрузку. Звать достаточно один раз, из
     * клиентской регистрации рендереров.
     * <p>
     * Именно один — {@link GemRenderModels#variants} кладёт билдер в общий
     * словарь по {@code putIfAbsent}, но каждый вызов возвращает новую ручку, а
     * нам нужна одна: пока она грузится, пули просто не рисуются.
     * <p>
     * Оба идентификатора — это модель, и так оно и должно быть:
     * {@code variants} внутри переставляет их местами и отдаёт
     * {@code GltfImporter#load(что_читать, как_назвать_лист, варианты)}. Первый
     * параметр открывается как glTF, второй нужен только для имени сшитого
     * атласа. Собственный же {@code GltfImporter#load(id)} для модели без
     * вариантов поступает ровно так же — {@code load(id, id, ...)}.
     * <p>
     * Сюда же нельзя подставить имя несуществующего файла: чтение ресурса
     * происходит по {@code getResourceOrThrow}, и ModelCache на неудаче
     * помечает модель сломанной до следующей перезагрузки ресурсов — пуля
     * тогда просто не рисуется.
     * <p>
     * При перезагрузке ресурсов ничего заново звать не надо — ручка сама
     * перезапросит модель у кеша GemRender.
     */
    public static synchronized void load() {
        if (handle != null) return;
        handle = GemRenderModels.variants(MODEL, MODEL, VARIANTS);
    }

    /** Модель со сшитыми вариантами либо {@code null}, пока она ещё грузится. */
    @Nullable
    public static synchronized GemRenderGltfModel model() {
        load();
        return handle != null ? handle.get() : null;
    }

    /**
     * Полоса атласа для пули: своя на каждый тип патрона.
     * <p>
     * Возвращает {@link VariantUv#NONE}, пока варианты не собрались или если
     * полосы для этого типа нет: {@code NONE} — это нулевая полоса, то есть
     * обычный патрон, и пуля в худшем случае нарисуется не тем, чем должна.
     */
    public static VariantUv variant(@Nullable GemRenderGltfModel model, @Nullable AmmoType type) {
        if (model == null || type == null) return VariantUv.NONE;

        int band = type.ordinal();
        return band < model.variantCount() ? model.variant(band) : VariantUv.NONE;
    }

    /** Полоса атласа для конкретной пули. */
    public static VariantUv variant(@Nullable GemRenderGltfModel model, TurretBulletEntity entity) {
        return variant(model, entity.getAmmoType());
    }
}
