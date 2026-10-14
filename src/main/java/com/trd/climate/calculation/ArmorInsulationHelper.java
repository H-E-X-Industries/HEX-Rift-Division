package com.trd.climate.calculation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Рассчитывает тепловую защиту от брони игрока.
 * Кожаная броня согревает, железная - немного, другие - почти нет.
 */
public class ArmorInsulationHelper {

    /**
     * Рассчитывает суммарную тепловую защиту от надетой брони.
     * Возвращает бонус к температуре в градусах Цельсия.
     */
    public static float calculateArmorInsulation(Player player) {
        float totalInsulation = 0.0f;
        
        // Проверяем каждый слот брони
        ItemStack helmet = player.getInventory().getArmor(3); // Шлем
        ItemStack chestplate = player.getInventory().getArmor(2); // Нагрудник
        ItemStack leggings = player.getInventory().getArmor(1); // Поножи
        ItemStack boots = player.getInventory().getArmor(0); // Ботинки
        
        // Кожаная броня (максимальная защита)
        totalInsulation += getItemInsulation(helmet, Items.LEATHER_HELMET, 2.0f);
        totalInsulation += getItemInsulation(chestplate, Items.LEATHER_CHESTPLATE, 3.0f);
        totalInsulation += getItemInsulation(leggings, Items.LEATHER_LEGGINGS, 2.5f);
        totalInsulation += getItemInsulation(boots, Items.LEATHER_BOOTS, 1.5f);
        
        // Железная броня (средняя защита)
        totalInsulation += getItemInsulation(helmet, Items.IRON_HELMET, 1.0f);
        totalInsulation += getItemInsulation(chestplate, Items.IRON_CHESTPLATE, 1.5f);
        totalInsulation += getItemInsulation(leggings, Items.IRON_LEGGINGS, 1.25f);
        totalInsulation += getItemInsulation(boots, Items.IRON_BOOTS, 0.75f);
        
        // Золотая броня (слабая защита)
        totalInsulation += getItemInsulation(helmet, Items.GOLDEN_HELMET, 0.5f);
        totalInsulation += getItemInsulation(chestplate, Items.GOLDEN_CHESTPLATE, 0.75f);
        totalInsulation += getItemInsulation(leggings, Items.GOLDEN_LEGGINGS, 0.6f);
        totalInsulation += getItemInsulation(boots, Items.GOLDEN_BOOTS, 0.4f);
        
        // Алмазная броня (плохая теплопроводность, но мало защищает от холода)
        totalInsulation += getItemInsulation(helmet, Items.DIAMOND_HELMET, 0.3f);
        totalInsulation += getItemInsulation(chestplate, Items.DIAMOND_CHESTPLATE, 0.5f);
        totalInsulation += getItemInsulation(leggings, Items.DIAMOND_LEGGINGS, 0.4f);
        totalInsulation += getItemInsulation(boots, Items.DIAMOND_BOOTS, 0.25f);
        
        // Незеритовая броня (минимальная защита)
        totalInsulation += getItemInsulation(helmet, Items.NETHERITE_HELMET, 0.2f);
        totalInsulation += getItemInsulation(chestplate, Items.NETHERITE_CHESTPLATE, 0.3f);
        totalInsulation += getItemInsulation(leggings, Items.NETHERITE_LEGGINGS, 0.25f);
        totalInsulation += getItemInsulation(boots, Items.NETHERITE_BOOTS, 0.15f);
        
        return totalInsulation;
    }

    /**
     * Проверяет, надет ли предмет определенного типа и возвращает его изоляцию.
     */
    private static float getItemInsulation(ItemStack stack, net.minecraft.world.item.Item targetItem, float insulation) {
        return stack.is(targetItem) ? insulation : 0.0f;
    }
}
