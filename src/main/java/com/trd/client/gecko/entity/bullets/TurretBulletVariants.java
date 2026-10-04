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
 * Модели и текстуры пули, разложенные по двум видам боезаряда.
 *
 * <p><b>Трассер.</b> Пять текстур — по одной на тип патрона — одной моделью
 * {@code turret_bullet.gltf}. Это те самые полосатые шлейфы, что и раньше.
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
 * {@link #TRACER_TEXTURES}, поэтому разъехаться они не могут; сверху стоит
 * проверка по {@link GemRenderGltfModel#variantCount()}.
 *
 * <p><b>Обычный патрон.</b> Своя модель {@code turret_bullet2.gltf} и одна
 * текстура на все пять типов: различать их нечем и незачем, силуэт у боезарядов
 * без трассера одинаковый. Текстура прописана внутри самой модели
 * ({@code images.uri}), поэтому варианты тут не нужны и модель грузится обычным
 * {@link GemRenderModels#get}, как гильза, — без атласа и без сшивания.
 * <p>
 * Геометрия у моделей разная по толщине (обычная пуля вдвое шире трассирующей)
 * и обе вытянуты вдоль оси полёта, так что поворот в рендерере у них общий.
 */
public final class TurretBulletVariants {

    /** Модель трассера: геометрия общая для всех типов патрона. */
    private static final ResourceLocation TRACER_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet.gltf");

    /** Модель обычного боезаряда: одна на все типы. */
    private static final ResourceLocation PLAIN_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet2.gltf");

    /** Текстура трассера обычного патрона: она же в glTF, и она же нулевая полоса. */
    private static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/turret_bullet.png");

    private static final Map<AmmoType, ResourceLocation> TRACER_TEXTURES = new EnumMap<>(AmmoType.class);

    /** Варианты в порядке {@link AmmoType#ordinal()}: индекс списка = полоса в атласе. */
    private static final List<Map<ResourceLocation, ResourceLocation>> TRACER_VARIANTS;

    @Nullable
    private static ModelCache.Handle<GemRenderGltfModel> tracerHandle;

    static {
        TRACER_TEXTURES.put(AmmoType.NORMAL, BASE_TEXTURE);
        TRACER_TEXTURES.put(AmmoType.PIERCING, texture("turret_bullet_ap"));
        TRACER_TEXTURES.put(AmmoType.HOLLOW, texture("turret_bullet_hollow"));
        TRACER_TEXTURES.put(AmmoType.INCENDIARY, texture("turret_bullet_fire"));
        TRACER_TEXTURES.put(AmmoType.RADIO, texture("turret_bullet_radio"));
        // Оба фугасных трассирующих берут ту же полосу, что и обычный патрон: своей
        // текстуры у них нет, а отличать их на лету должен взрыв, а не цвет
        // шлейфа. Заодно это лишние полосы в атласе с копией картинки — ровно
        // поэтому новые значения добавлены в конец перечисления, их ordinal
        // задаёт номер полосы.
        TRACER_TEXTURES.put(AmmoType.HE, BASE_TEXTURE);
        TRACER_TEXTURES.put(AmmoType.HE_SAVE, BASE_TEXTURE);

        TRACER_VARIANTS = Stream.of(AmmoType.values())
                .map(TRACER_TEXTURES::get)
                .map(tex -> Map.of(BASE_TEXTURE, tex))
                .toList();
    }

    private TurretBulletVariants() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * Ставит модель трассера в очередь на загрузку. Звать достаточно один раз,
     * из клиентской регистрации рендереров.
     * <p>
     * По одной модели — {@link GemRenderModels#variants} кладёт билдер в общий
     * словарь по {@code putIfAbsent}, но каждый вызов возвращает новую ручку, а
     * нам нужна ровно одна: пока она грузится, трассеры просто не рисуются.
     * Обычная пуля здесь не участвует — она грузится сама, по {@code get}.
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
     * При перезагрузке ресурсов ничего заново вызывать не надо — ручка сама
     * перезапросит модель у кеша GemRender.
     */
    public static synchronized void load() {
        if (tracerHandle == null) {
            tracerHandle = GemRenderModels.variants(TRACER_MODEL, TRACER_MODEL, TRACER_VARIANTS);
        }
    }

    /** Модель трассера со сшитыми вариантами либо {@code null}, пока она ещё грузится. */
    @Nullable
    public static synchronized GemRenderGltfModel tracerModel() {
        load();
        return tracerHandle != null ? tracerHandle.get() : null;
    }

    /**
     * Модель обычного боезаряда либо {@code null}, пока она ещё грузится.
     * <p>
     * Без вариантов и без ручки: текстура прописана в самой модели, а
     * {@code get} и так отдаёт кешированную модель и {@code null}, пока та не
     * готова, — ровно как у гильзы.
     */
    @Nullable
    public static GemRenderGltfModel plainModel() {
        return GemRenderModels.get(PLAIN_MODEL);
    }

    /**
     * Модель для конкретной пули: трассеру своя, обычному боезаряду своя.
     * <p>
     * Пока нужная модель не загрузилась, берётся любая из двух: лучше нарисовать
     * пулю не тем, чем она должна быть, чем не нарисовать её совсем — на
     * автоматическом огне мигание читается как потеря пули, а лишняя минус
     * секунда на загрузку модели этого не стоит.
     */
    @Nullable
    public static GemRenderGltfModel modelFor(TurretBulletEntity entity) {
        return entity.isTracer() ? tracerModel() : plainModel();
    }

    /**
     * Полоса атласа трассера: своя на каждый тип патрона.
     * <p>
     * Возвращает {@link VariantUv#NONE}, пока варианты не собрались или если
     * полосы для этого типа нет: {@code NONE} — это нулевая полоса, то есть
     * обычный патрон, и пуля в худшем случае нарисуется не тем, чем должна.
     */
    public static VariantUv tracerVariant(@Nullable GemRenderGltfModel model, @Nullable AmmoType type) {
        if (model == null || type == null) return VariantUv.NONE;

        int band = type.ordinal();
        return band < model.variantCount() ? model.variant(band) : VariantUv.NONE;
    }

    /** Полоса атласа трассера для конкретной пули. */
    public static VariantUv tracerVariant(@Nullable GemRenderGltfModel model, TurretBulletEntity entity) {
        return tracerVariant(model, entity.getAmmoType());
    }
}