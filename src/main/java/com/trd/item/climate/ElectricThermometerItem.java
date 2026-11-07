package com.trd.item.climate;

import com.trd.capability.ModCapabilities;
import com.trd.item.industrial.energy.ModBatteryItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

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

    /**
     * Раз в 3 секунды полоска заряда меняет данные стека (CUSTOM_DATA -> energy).
     * Без этого переопределения предмет каждый раз «подпрыгивает» в руке
     * из-за переигровки анимации поднятия после синхронизации слота.
     * Анимация нужна только при смене реального предмета.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    /**
     * Тултип: только уровень заряда.
     * Мощность входа/выхода (переносные аккумуляторы) у термометра не показываем.
     */
    @Override
    public void appendHoverText(@Nullable ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack != null) {
            var provider = stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
            if (provider != null) {
                addEnergyTooltip(tooltip, provider.getEnergyStored(), provider.getMaxEnergyStored(), ChatFormatting.AQUA);
            }
        }
        // Не вызываем super.appendHoverText (ModBatteryItem) — он добавляет строки входа/выхода
    }

    /**
     * ПКМ — вывести в чат текущую температуру тела и скорость её изменения.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var data = player.getData(com.trd.climate.attachment.ModAttachments.PLAYER_TEMPERATURE);
            float currentTemp = data.getCurrentTemp();
            float ratePerSecond = com.trd.climate.event.TemperatureRateTracker.getRatePerSecond(serverPlayer);

            player.displayClientMessage(Component.translatable("chat.trd.thermometer.current_temp",
                            Component.literal(String.format(java.util.Locale.ROOT, "%.1f", currentTemp))
                                    .withStyle(ChatFormatting.AQUA)),
                    false);
            player.displayClientMessage(Component.translatable("chat.trd.thermometer.temp_rate",
                            Component.literal(String.format(java.util.Locale.ROOT, "%.2f", ratePerSecond))
                                    .withStyle(ChatFormatting.YELLOW)),
                    false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
