package com.trd.item.weapons.missiles;

/**
 * Снаряд для тромбона: тип боезаряда, урон и скорость.
 */
public interface IMissileItem {

    /**
     * Тип боезаряда: "standard", "he" или "fire".
     */
    String getMissileType();

    /**
     * Урон снаряда.
     */
    float getMissileDamage();

    /**
     * Скорость полёта снаряда.
     */
    float getMissileSpeed();
}
