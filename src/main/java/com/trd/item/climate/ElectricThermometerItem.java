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
 * Пока прибор лежит в любом слоте инвентаря игрока — в левом нижнем углу экрана
 * отображается дисплей прибора с показанием температуры тела и графиком её изменения
 * (см. {@link com.trd.climate.client.ElectricThermometerHud}). Если заряд кончился,
 * дисплей остаётся тёмным («выключенным»).
 * Энергия расходуется по 10 JE раз в 3 секунды (см. {@link com.trd.climate.event.ThermometerEnergyDrainHandler}).
 */
public class ElectricThermometerItem extends ModBatteryItem {

    /** Буфер энергии по умолчанию, JE. */
    public static final int DEFAULT_CAPACITY = 10_000;
    /** Резерв JE, снимаемый раз в 3 секунды за работу. */
    public static final int COST_PER_UPDATE = 5;

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
     * ПКМ — замерить блок, на который смотрит игрок (до 5 блоков):
     * его температуру, а для источников тепла — ещё и теплопроводность,
     * текущую мощность и температуру самого прибора.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            var hit = getPlayerPOVHitResult(level, player,
                    net.minecraft.world.level.ClipContext.Fluid.NONE);
            net.minecraft.core.BlockPos pos = hit.getBlockPos();

            java.util.Locale locale = java.util.Locale.ROOT;

            var source = com.trd.climate.calculation.HeatSourceHelper.resolveAt(level, pos);

            if (source == null) {
                // Обычный блок: температура САМОГО блока, а не воздуха над ним.
                float blockTemp = com.trd.climate.calculation.TemperatureCalculator.calculatePointTemp(level, pos);
                player.displayClientMessage(Component.translatable("chat.trd.thermometer.block_temp",
                                Component.literal(String.format(locale, "%.1f", blockTemp))
                                        .withStyle(ChatFormatting.AQUA)),
                        false);
                player.displayClientMessage(Component.translatable("chat.trd.thermometer.not_a_source"),
                        false);
            } else {
                var def = source.def();
                player.displayClientMessage(Component.translatable("chat.trd.thermometer.source_kind",
                                Component.literal(def.isCooling() ? "охладитель" : "нагреватель")
                                        .withStyle(ChatFormatting.GOLD),
                                Component.literal(def.isPassive() ? "пассивный" : "активный")
                                        .withStyle(ChatFormatting.AQUA)),
                        false);

                if (def.isPassive()) {
                    var state = com.trd.climate.room.ThermalStateStore.stateAt(level, pos);
                    boolean physical = state != null && state.physical();

                    // Запас не печатаем: он дублирует «мощность источника» ниже.
                    player.displayClientMessage(Component.translatable(
                                    "chat.trd.thermometer.physical_state",
                                    Component.literal(physical ? "ДА (в герметичной комнате)" : "НЕТ")
                                            .withStyle(physical ? ChatFormatting.GREEN : ChatFormatting.GRAY)),
                            false);

                    if (physical) {
                        // У физичного источника базовая и текущая температуры разные.
                        player.displayClientMessage(Component.translatable(
                                        "chat.trd.thermometer.source_temps",
                                        Component.literal(String.format(locale, "%.0f", def.surfaceTemp()))
                                                .withStyle(ChatFormatting.GRAY),
                                        Component.literal(String.format(locale, "%.0f", source.sourceTemp()))
                                                .withStyle(ChatFormatting.RED)),
                                false);
                    } else {
                        // Вне комнаты запас не тратится — одна строка «температура»
                        // (у побывавшего в комнате блока она остаётся замороженной).
                        player.displayClientMessage(Component.translatable("chat.trd.thermometer.source_temp",
                                        Component.literal(String.format(locale, "%.0f", source.sourceTemp()))
                                                .withStyle(ChatFormatting.RED)),
                                false);
                    }

                    player.displayClientMessage(Component.translatable(
                                    "chat.trd.thermometer.effective_heat",
                                    Component.literal(String.format(locale, "%.0f", source.effectiveHeat()))
                                            .withStyle(ChatFormatting.RED)),
                            false);
                } else {
                    player.displayClientMessage(Component.translatable("chat.trd.thermometer.source_temp",
                                    Component.literal(String.format(locale, "%.0f", source.sourceTemp()))
                                            .withStyle(ChatFormatting.RED)),
                            false);
                }

                player.displayClientMessage(Component.translatable("chat.trd.thermometer.conductivity",
                                Component.literal(String.format(locale, "%.0f%%", def.conductivity() * 100.0f))
                                        .withStyle(ChatFormatting.GOLD)),
                        false);
                player.displayClientMessage(Component.translatable("chat.trd.thermometer.source_power",
                                Component.literal(String.format(locale, "%.0f%%", source.power() * 100.0f))
                                        .withStyle(ChatFormatting.YELLOW)),
                        false);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
