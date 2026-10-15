package com.trd.client.gltf.item.grenades;

import com.trd.entity.ModEntities;
import com.trd.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Виды обычных гранат и файлы их моделей.
 *
 * <p><b>Одна геометрия на всю линию, кроме водородной.</b> Форма у шести видов из
 * семи одна и та же, поэтому они рисуются на общей геометрии и различаются только
 * текстурой: файлы моделей — копии одного экспорта, отличаются они ровно одной
 * строкой, путём к текстуре, который лежит в
 * {@code images.extras.resourceLocation} каждого файла. Водородная — исключение,
 * у неё своя геометрия и своя центровка, см. {@link #centerY}.
 *
 * <p>Сама привязка текстуры к геометрии делается один раз, здесь: {@link
 * Variant#modelName()} возвращает имя вида, и оно же идёт и в имя текстуры, и в
 * имя файла модели, поэтому расходиться им негде.
 *
 * <p><b>Почему у вида сразу два ключа.</b> Предмет и снаряд — разные объекты с
 * разными типами в реестре, и связать их через поле на сущности нельзя: на
 * клиенте оно приезжает пакетом и может не совпасть с типом, тогда как тип
 * сущности известен наверняка. Поэтому вид ищется и по предмету, и по типу
 * сущности — тем же способом, что и у ударных гранат.
 */
public final class ClassicGrenadeVariants {

    /**
     * Вид обычной гранаты: своё имя модели, свой предмет и свой снаряд.
     *
     * <p>Имя — оно же имя текстуры и файла модели, поэтому расходиться им негде.
     * Модели у всех видов копии одной геометрии, отличающиеся ровно одной строкой:
     * путём к текстуре.
     */
    public enum Variant {

        /** Осколочная. */
        STANDARD("grenade", ModItems.GRENADE::get, ModEntities.GRENADE_PROJECTILE::get),

        /** Фугасная. */
        HE("grenade_he", ModItems.GRENADEHE::get, ModEntities.GRENADEHE_PROJECTILE::get),

        /** Зажигательная. */
        FIRE("grenade_fire", ModItems.GRENADEFIRE::get, ModEntities.GRENADEFIRE_PROJECTILE::get),

        /** Липкая. */
        SLIME("grenade_slime", ModItems.GRENADESLIME::get, ModEntities.GRENADESLIME_PROJECTILE::get),

        /** Умная. */
        SMART("grenade_smart", ModItems.GRENADESMART::get, ModEntities.GRENADESMART_PROJECTILE::get),

        /** Водород-кремирующая. */
        HYDROGEN("grenade_nuc", ModItems.GRENADE_NUC::get, ModEntities.GRENADE_NUC_PROJECTILE::get),

        /** Гравитационная. */
        GRAVITY("grenade_gravity", ModItems.GRAVITY_GRENADE::get, ModEntities.GRAVITY_GRENADE_PROJECTILE::get);

        private final String name;
        private final Supplier<Item> item;
        private final Supplier<EntityType<?>> entityType;

        Variant(String name, Supplier<Item> item, Supplier<EntityType<?>> entityType) {
            this.name = name;
            this.item = item;
            this.entityType = entityType;
        }

        /**
         * Имя вида: оно же имя текстуры и файла модели, поэтому расходиться им
         * негде. Текстуры приходят из того же блока в Blockbench, и всё, что нужно
         * поменять при появлении настоящей модели, — одну строку в этом перечис.
         * <p>
         * Не {@code name()}: так называется метод {@link Enum}, и перекрывать его
         * нельзя.
         */
        public String modelName() {
            return name;
        }

        /** Предмет этого вида. */
        public Item item() {
            return item.get();
        }

        /** Снаряд этого вида. */
        public EntityType<?> entityType() {
            return entityType.get();
        }
    }

    /**
     * Таблицы строятся лениво, а не в статическом блоке: они зовут
     * {@code .get()} у отложенных регистраций, и на клиенте это происходит уже
     * после регистрации предметов, но полагаться на порядок инициализации классов
     * здесь незачем.
     */
    private static final class Tables {
        private static final Map<Item, Variant> BY_ITEM = new HashMap<>();
        private static final Map<EntityType<?>, Variant> BY_ENTITY = new HashMap<>();

        static {
            for (Variant variant : Variant.values()) {
                BY_ITEM.put(variant.item(), variant);
                BY_ENTITY.put(variant.entityType(), variant);
            }
        }

        private Tables() {
        }
    }

    private ClassicGrenadeVariants() {
    }

    /** Вид по предмету; {@link Variant#STANDARD}, если предмет не из этого набора. */
    public static Variant variantOf(@Nullable Item item) {
        return item != null ? Tables.BY_ITEM.getOrDefault(item, Variant.STANDARD) : Variant.STANDARD;
    }

    /** Вид по снаряду: на клиенте тип сущности известен наверняка. */
    public static Variant variantOf(Entity entity) {
        return Tables.BY_ENTITY.getOrDefault(entity.getType(), Variant.STANDARD);
    }

    /** Файл модели вида. */
    public static ResourceLocation modelIdOf(@Nullable Variant variant) {
        return GrenadeGltfModels.modelId((variant != null ? variant : Variant.STANDARD).modelName());
    }

    /** Файл модели по предмету — то, что нужно облику предмета. */
    public static ResourceLocation modelIdOf(@Nullable Item item) {
        return modelIdOf(variantOf(item));
    }

    /**
     * Где в модели вида стоит её середина по вертикали, в единицах блока.
     *
     * <p>У общей геометрии корпус занимает Y примерно от -0.0016 до +0.2844, откуда
     * и берутся {@value #SHARED_CENTER_Y}. Меш сидит на узле {@code main} со сдвигом
     * вверх, и без этого граната висела бы над серединой хитбокса.
     *
     * <p>У водородной геометрия своя, и ось вращения выставлена не по геометрической
     * середине, а по третьему пикселю снизу, то есть {@code (0, 3, 0)}. Считать
     * строго середину меша нельзя: ось идёт через узел {@code main} со сдвигом
     * 0.1875, и визуально граната при провороте гуляла бы по высоте.
     */
    public static float centerY(@Nullable Variant variant) {
        return variant == Variant.HYDROGEN ? HYDROGEN_CENTER_Y : SHARED_CENTER_Y;
    }

    /** Середина общей геометрии, которой рисуется всё, кроме водородной. */
    public static final float SHARED_CENTER_Y = 0.1414F;

    /** Третий пиксель снизу: 3/16 блока выше основания (0.1875 + 0.1875 = 0.375). */
    public static final float HYDROGEN_CENTER_Y = 0.375F;

    /** Модель вида, либо {@code null}, пока она грузится. */
    @Nullable
    public static GemRenderGltfModel model(@Nullable Variant variant) {
        return GrenadeGltfModels.model(modelIdOf(variant));
    }

    /** Клип снятой с корня поправкой, либо {@code null}, пока модели нет. */
    @Nullable
    public static GltfAnimation poseClip(@Nullable Variant variant, String clip) {
        return GrenadeGltfModels.poseClip(modelIdOf(variant), clip);
    }

    /** Поза покоя с невидимой чекой — то, чем рисуется летящая граната. */
    @Nullable
    public static GltfAnimation hiddenPin(@Nullable Variant variant) {
        return GrenadeGltfModels.hiddenPin(modelIdOf(variant),
                com.trd.item.weapons.grenades.GrenadeIfAnimation.PIN_PULL);
    }
}
