package com.trd.client.gecko.item.grenades;

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
 * <p><b>Это временная мера, а не дизайн.</b> Своих моделей у этих гранат нет и
 * делать их некогда, поэтому все семь видов рисуются на геометрии ударной
 * гранаты — у них одна и та же форма — и различаются только текстурой, зашитой в
 * файл. Из-за этого в названии каждого вида и стоит пометка о временной модели:
 * она видна игроку в тултипе и честно говорит, что это подставка, а не настоящий
 * вид оружия. Когда появится настоящая графика, этот класс удаляется целиком
 * вместе с {@code ClassicGrenadeGltfAppearance}, {@code ClassicGrenadeRenderer},
 * {@code ClassicGrenadeClientItem} и {@code ClassicGrenadeGltfRenderer}, а
 * предметы возвращаются к 2D-спрайтам.
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
        HE("grenadehe", ModItems.GRENADEHE::get, ModEntities.GRENADEHE_PROJECTILE::get),

        /** Зажигательная. */
        FIRE("grenadefire", ModItems.GRENADEFIRE::get, ModEntities.GRENADEFIRE_PROJECTILE::get),

        /** Липкая. */
        SLIME("grenadeslime", ModItems.GRENADESLIME::get, ModEntities.GRENADESLIME_PROJECTILE::get),

        /** Умная. */
        SMART("grenadesmart", ModItems.GRENADESMART::get, ModEntities.GRENADESMART_PROJECTILE::get),

        /** Водород-кремирующая. */
        HYDROGEN("grenade_nuc", ModItems.GRENADE_NUC::get, ModEntities.GRENADE_NUC_PROJECTILE::get),

        /** Гравитационная. */
        GRAVITY("gravity_grenade", ModItems.GRAVITY_GRENADE::get, ModEntities.GRAVITY_GRENADE_PROJECTILE::get);

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