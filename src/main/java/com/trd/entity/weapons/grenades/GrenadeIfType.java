package com.trd.entity.weapons.grenades;

import com.trd.entity.ModEntities;
import com.trd.item.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Типы гранат GrenadeIfProjectileEntity (инерционный взрыватель, фиксированная задержка 4с).
 * <p>explosionPower — единственный источник правды о радиусе взрыва, customDamage — максимальный урон,
 * с линейным спадом к нулю на краю радиуса. Ударные гранаты не отскакивают от блоков по своему
 * BOUNCE_MULTIPLIER — отскок задаёт GrenadeIfProjectileEntity.
 */
public enum GrenadeIfType {
    // Осколочная ударная: радиус 5.0, урон 45
    GRENADE_IF(5.0f, 45.0f, ModItems.GRENADE_IF::get, ModEntities.GRENADE_IF_PROJECTILE::get),

    // Фугасная ударная: радиус 8.0, урон 80
    GRENADE_IF_HE(8.0f, 80.0f, ModItems.GRENADE_IF_HE::get, ModEntities.GRENADE_IF_HE_PROJECTILE::get),

    // Липучка ударная: радиус 6.0, урон 60, прилипает к блокам и сущностям
    GRENADE_IF_SLIME(6.0f, 60.0f, ModItems.GRENADE_IF_SLIME::get, ModEntities.GRENADE_IF_SLIME_PROJECTILE::get),

    // Зажигательная ударная: радиус 3.0. Реальный взрыв — ExplosionFire (30 → 6),
    // customDamage не применяется напрямую: радиус из тултипа = радиусу взрыва.
    GRENADE_IF_FIRE(3.0f, 30.0f, ModItems.GRENADE_IF_FIRE::get, ModEntities.GRENADE_IF_FIRE_PROJECTILE::get);

    private final float explosionPower;
    private final float customDamage;
    private final Supplier<Item> itemSupplier;
    private final Supplier<EntityType<? extends GrenadeIfProjectileEntity>> entityTypeSupplier;

    GrenadeIfType(float explosionPower, float customDamage, Supplier<Item> itemSupplier,
                  Supplier<EntityType<? extends GrenadeIfProjectileEntity>> entityTypeSupplier) {
        this.explosionPower = explosionPower;
        this.customDamage = customDamage;
        this.itemSupplier = itemSupplier;
        this.entityTypeSupplier = entityTypeSupplier;
    }

    public float getExplosionPower() {
        return explosionPower;
    }

    public float getCustomDamage() {
        return customDamage;
    }

    public Item getItem() {
        return itemSupplier.get();
    }

    /**
     * Тип по сущности, породившей его снаряд.
     * <p>
     * На клиенте у снаряда надёжно известен только тип сущности: поле
     * {@code grenadeType} там остаётся пустым, а переезжает по сети
     * {@code SynchedEntityData}, и любая рассинхронизация откатила бы рендер на
     * базовую текстуру — то есть все четыре вида молча выглядели бы одинаковыми.
     * Типов ровно четыре, и каждому достаётся своя сущность, так что искать
     * перебором дешевле и надёжнее, чем доверять синхронизации.
     */
    public static GrenadeIfType typeOf(EntityType<?> entityType) {
        for (GrenadeIfType type : values()) {
            if (type.entityTypeSupplier.get() == entityType) {
                return type;
            }
        }
        return GRENADE_IF;
    }
}