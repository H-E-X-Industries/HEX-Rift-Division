package com.trd.climate.client;

import com.trd.item.ModItems;
import com.trd.item.climate.ElectricThermometerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Клиентские проверки наличия термометров у игрока.
 * <p>
 * Электронный термометр считается «работающим», если он лежит в любом слоте
 * инвентаря (36 слотов + броня + offhand) и всё ещё имеет заряд.
 * Ртутный термометр показывает шкалу, когда он находится в хотбаре (в т.ч. в главной руке)
 * или в offhand.
 */
public final class ThermometerClientHelper {

    private ThermometerClientHelper() {}

    /** Электронный термометр есть в инвентаре и заряжен (энергия > 0). */
    public static boolean hasWorkingElectricThermometer(Player player) {
        return findWorkingElectricThermometer(player) != null;
    }

    /** Ищет заряженный электронный термометр во всём инвентаре игрока. */
    public static ItemStack findWorkingElectricThermometer(Player player) {
        if (player == null) return null;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()
                    && stack.getItem() instanceof ElectricThermometerItem
                    && ElectricThermometerItem.getEnergy(stack) > 0) {
                return stack;
            }
        }
        return null;
    }

    /** Ртутный термометр: хотбар (включая главную руку) или offhand. */
    public static boolean hasMercuryThermometerVisible(Player player) {
        if (player == null) return false;
        var inventory = player.getInventory();
        for (int i = 0; i <= 8; i++) {
            if (isMercuryThermometer(inventory.getItem(i))) return true;
        }
        return isMercuryThermometer(player.getOffhandItem());
    }

    private static boolean isMercuryThermometer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.MERCURY_THERMOMETER.get());
    }
}
