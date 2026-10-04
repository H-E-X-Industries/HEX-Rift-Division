package com.trd.item.weapons.ammo;

import net.minecraft.world.item.Item;

/**
 * Патрон 20 мм для пушки и турелей.
 *
 * <p>Обычный предмет с двумерным спрайтом, а не GeckoLib-модель: патрону нечего
 * анимировать. Раньше это был {@code GeoItem} с общей geo-моделью на все
 * патроны ({@code geo/ammo_turret.geo.json}) и контроллером с анимацией flip,
 * которая, кстати, никогда и не запускалась — {@code triggerAnim} по патронам
 * не звал никто, а был он только у литой кирки. То есть библиотека тащилась в
 * предмет ради одного статичного меша; теперь она не нужна вовсе.
 *
 * <p>Иконка называется ровно как предмет: {@code turret_ammo_ap_tracer} →
 * {@code assets/trd/models/item/turret_ammo_ap_tracer.json} →
 * {@code assets/trd/textures/item/ammo/turret_ammo_ap_tracer.png}. Правило
 * «спрайт лежит рядом с id» позволяет завести новый патрон, не написав ни
 * строки Java, — достаточно бросить картинку и json в эти каталоги.
 */
public class AmmoTurretItem extends Item implements IAmmoItem {

    private final float damage;
    private final float speed;
    private final boolean isPiercing;
    private final boolean tracer;

    public AmmoTurretItem(Properties properties, float damage, float speed, boolean isPiercing) {
        this(properties, damage, speed, isPiercing, false);
    }

    public AmmoTurretItem(Properties properties, float damage, float speed, boolean isPiercing,
                          boolean tracer) {
        super(properties);
        this.damage = damage;
        this.speed = speed;
        this.isPiercing = isPiercing;
        this.tracer = tracer;
    }

    @Override
    public String getCaliber() { return "20mm_turret"; }

    @Override
    public float getDamage() { return this.damage; }

    @Override
    public float getSpeed() { return this.speed; }

    @Override
    public boolean isPiercing() { return this.isPiercing; }

    /**
     * Трассерный ли патрон: от флага зависят модель пули и её яркость, сам
     * патрон стреляет ровно тем же, чем его обычный собрат.
     */
    @Override
    public boolean isTracer() { return this.tracer; }
}