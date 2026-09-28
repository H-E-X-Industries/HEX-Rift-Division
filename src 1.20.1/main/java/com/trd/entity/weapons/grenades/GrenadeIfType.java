package com.trd.entity.weapons.grenades;


import com.trd.item.ModItems;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Типы гранат GrenadeIfProjectileEntity (инерционный взрыватель, фиксированная задержка 4с).
 * <p>
 * explosionPower — единственный источник правды о радиусе взрыва, customDamage — максимальный урон,
 * с линейным спадом к нулю на краю радиуса. Ударные гранаты не отскакивают от блоков по своему
 * BOUNCE_MULTIPLIER — отскок задаёт GrenadeIfProjectileEntity.
 */
public enum GrenadeIfType {
    // Осколочная ударная: радиус 5.0, урон 45
    GRENADE_IF(5.0f, 45.0f, ModItems.GRENADE_IF::get),

    // Фугасная ударная: радиус 8.0, урон 80
    GRENADE_IF_HE(8.0f, 80.0f, ModItems.GRENADE_IF_HE::get),

    // Липучка ударная: радиус 6.0, урон 60, прилипает к блокам и сущностям
    GRENADE_IF_SLIME(6.0f, 60.0f, ModItems.GRENADE_IF_SLIME::get),

    // Зажигательная ударная: радиус 3.0. Урон задаёт ExplosionFire (30 в ядре, спад до 6),
    // customDamage ниже не используется — радиус нужен ему как радиус ядра полного урона.
    GRENADE_IF_FIRE(3.0f, 30.0f, ModItems.GRENADE_IF_FIRE::get);

    private final float explosionPower;
    private final float customDamage;
    private final Supplier<Item> itemSupplier;

    GrenadeIfType(float explosionPower, float customDamage, Supplier<Item> itemSupplier) {
        this.explosionPower = explosionPower;
        this.customDamage = customDamage;
        this.itemSupplier = itemSupplier;
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
}
