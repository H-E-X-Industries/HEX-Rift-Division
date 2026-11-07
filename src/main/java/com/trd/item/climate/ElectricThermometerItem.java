package com.trd.item.climate;

import com.trd.capability.ModCapabilities;
import com.trd.item.industrial.energy.ModBatteryItem;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Электронный нательный термометр.
 * <p>
 * Работает как переносной аккумулятор: хранит энергию в компонентах стека
 * (CUSTOM_DATA -> "energy"), имеет полоску заряда и тултип заряда, унаследованные от {@link ModBatteryItem}.
 * <p>
 * Пока прибор лежит в любом слоте инвентаря игрока и заряжен — на HUD по центру экрана
 * отображается цифровое значение температуры тела (см. {@link com.trd.climate.client.TemperatureHudOverlay}).
 * Энергия расходуется по 10 JE раз в 3 секунды (см. {@link com.trd.climate.event.ThermometerEnergyDrainHandler}).
 */
public class ElectricThermometerItem extends ModBatteryItem {

    /** Буфер энергии по умолчанию, JE. */
    public static final int DEFAULT_CAPACITY = 10_000;
    /** Резерв JE, снимаемый раз в 3 секунды за работу. */
    public static final int COST_PER_UPDATE = 10;

    public ElectricThermometerItem(Properties properties, int capacity, int maxReceive, int maxExtract) {
        super(properties, capacity, maxReceive, maxExtract);
    }

    /**
     * Текущий заряд термометра (0 если стек пуст или это не термометр).
     * Работает и на клиенте (стак синхронизируется вместе с компонентом CUSTOM_DATA).
     */
    public static long getEnergy(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ElectricThermometerItem)) {
            return 0;
        }
        var provider = stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
        return provider != null ? provider.getEnergyStored() : 0;
    }
}
