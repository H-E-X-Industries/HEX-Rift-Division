package com.trd.item.weapons.missiles;

import net.minecraft.world.item.Item;

/**
 * Снаряд 100 мм для тромбона. Типы: standard (фугасный), he и fire.
 */
public class MissileItem extends Item implements IMissileItem {

    private final String missileType;
    private final float damage;
    private final float speed;

    public MissileItem(Properties properties, String missileType, float damage, float speed) {
        super(properties);
        this.missileType = missileType;
        this.damage = damage;
        this.speed = speed;
    }

    @Override
    public String getMissileType() {
        return missileType;
    }

    @Override
    public float getMissileDamage() {
        return damage;
    }

    @Override
    public float getMissileSpeed() {
        return speed;
    }

    public boolean isStandard() {
        return "standard".equals(missileType);
    }

    public boolean isHE() {
        return "he".equals(missileType);
    }

    public boolean isFire() {
        return "fire".equals(missileType);
    }
}
