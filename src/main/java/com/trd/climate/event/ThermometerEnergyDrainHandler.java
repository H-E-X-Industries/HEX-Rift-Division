package com.trd.climate.event;

import com.trd.item.climate.ElectricThermometerItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Серверный расход энергии электронного термометра.
 * <p>
 * Пока термометр лежит в любом слоте инвентаря игрока, он «работает» и один раз
 * в 3 секунды потребляет 10 JE из своего буфера (10 000 JE → ~50 минут работы).
 * При полном разряде прибор перестаёт показывать температуру на HUD.
 * <p>
 * В креативе и спектаторе расход не начисляется (климат в этих режимах не тикает,
 * см. {@link PlayerTemperatureTickHandler}).
 */
@EventBusSubscriber(modid = "trd")
public class ThermometerEnergyDrainHandler {

    /** Интервал списания энергии, тиков (20 тиков = 1 сек). */
    private static final int INTERVAL_TICKS = 60; // раз в 3 секунды

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();

        if (player.level().isClientSide() || player.isSpectator() || player.isCreative()) {
            return;
        }

        // Раз в 3 секунды, со сдвигом по ID игрока (как в секундной логике климата)
        if ((player.tickCount + player.getId()) % INTERVAL_TICKS != 0) {
            return;
        }

        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ElectricThermometerItem)) {
                continue;
            }
            var provider = stack.getCapability(com.trd.capability.ModCapabilities.ENERGY_PROVIDER_ITEM);
            if (provider != null) {
                provider.extractEnergy(ElectricThermometerItem.COST_PER_UPDATE, false);
            }
        }
    }
}
