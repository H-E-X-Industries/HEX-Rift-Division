package com.trd.entity.weapons.grenades;


import com.trd.item.ModItems;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Типы гранат GrenadeProjectileEntity.
 * <p>
 * explosionPower — единственный источник правды о радиусе взрыва, customDamage — максимальный урон,
 * с линейным спадом к нулю на краю радиуса. Значения explosionPower обязаны совпадать с тем,
 * что реально подставляется в ExplosionStandard/ExplosionHE/ExplosionFire.
 */
public enum GrenadeType {
    // Осколочная: 3 отскока, радиус 3.5, урон 20
    STANDARD(3, 0.3f, 3.5f, 20.0f, false, () -> ModItems.GRENADE.get()),

    // Фугасная: 3 отскока, радиус 7.0, урон 40
    HE(3, 0.3f, 7.0f, 40.0f, false, () -> ModItems.GRENADEHE.get()),

    // Зажигательная: 3 отскока, радиус 3.0. Урон задаёт ExplosionFire (30 в ядре, спад до 6),
    // damage ниже не используется — радиус нужен ему как радиус ядра полного урона.
    FIRE(3, 0.3f, 3.0f, 30.0f, false, () -> ModItems.GRENADEFIRE.get()),

    // Липучка: 4 отскока, прилипает к блокам и сущностям, радиус 3.5, урон 30
    SLIME(4, 0.51f, 3.5f, 30.0f, false, () -> ModItems.GRENADESLIME.get()),

    // Умная: 3 отскока, взрывается при контакте с сущностью по радиусу SMART_CONTACT_RADIUS
    SMART(3, 0.3f, 3.5f, 20.0f, true, () -> ModItems.GRENADESMART.get());

    private final int maxBounces;
    private final float bounceMultiplier;
    private final float explosionPower;
    private final float customDamage;
    private final boolean explodesOnEntity;
    private final Supplier<Item> itemSupplier;

    GrenadeType(int maxBounces, float bounceMultiplier, float explosionPower, float customDamage,
                boolean explodesOnEntity, Supplier<Item> itemSupplier) {
        this.maxBounces = maxBounces;
        this.bounceMultiplier = bounceMultiplier;
        this.explosionPower = explosionPower;
        this.customDamage = customDamage;
        this.explodesOnEntity = explodesOnEntity;
        this.itemSupplier = itemSupplier;
    }

    public int getMaxBounces() { return maxBounces; }
    public float getBounceMultiplier() { return bounceMultiplier; }
    public float getExplosionPower() { return explosionPower; }
    public float getCustomDamage() { return customDamage; }
    public boolean explodesOnEntity() { return explodesOnEntity; }
    public Item getItem() { return itemSupplier.get(); }
}
