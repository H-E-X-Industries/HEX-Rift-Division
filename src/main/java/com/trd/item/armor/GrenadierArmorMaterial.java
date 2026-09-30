package com.trd.item.armor;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;

/**
 * Материал очков гренадёра.
 * <p>
 * В 1.20.1 это был enum, реализующий интерфейс {@link ArmorMaterial}. В 1.21.1
 * {@code ArmorMaterial} стал record'ом, а {@link ArmorItem} принимает
 * {@link Holder} — поэтому здесь просто оборачиваем единственный экземпляр
 * в {@link Holder#direct}. Реестра материалов брони в NeoForge 21.1 нет,
 * так что фабрика-постановщик не нужна.
 * <p>
 * Прочность в 1.20.1 считалась как {@code HEALTH_PER_SLOT[type] * 15} = 195 для
 * шлема; в 1.21.1 её указывают вручную в {@code Item.Properties#durability}.
 */
public final class GrenadierArmorMaterial {

    /** Прочность очков (13 * 15 из 1.20.1). */
    public static final int DURABILITY = 195;

    /** Защита по слотам как в 1.20.1: только шлем, 1 очко. */
    private static final EnumMap<ArmorItem.Type, Integer> DEFENSE = new EnumMap<>(ArmorItem.Type.class);

    public static final Holder<ArmorMaterial> GRENADIER;

    static {
        DEFENSE.put(ArmorItem.Type.HELMET, 1);
        GRENADIER = Holder.direct(new ArmorMaterial(
                DEFENSE,
                15,                              // enchantmentValue
                SoundEvents.ARMOR_EQUIP_GENERIC,
                () -> Ingredient.EMPTY,
                List.of(),                        // слои не нужны: очки рисует GeckoLib
                0.0F,                            // toughness
                0.0F                             // knockbackResistance
        ));
    }

    private GrenadierArmorMaterial() {
    }
}
