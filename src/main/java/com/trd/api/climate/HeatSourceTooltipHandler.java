package com.trd.api.climate;

import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Locale;

/**
 * Тултипы источников тепла/холода — аналог {@code FuelTooltipHandler}.
 * <p>
 * По предмету находит {@link HeatSourceDef} в реестре и показывает паспорт
 * источника: роль/тип, температуру, теплопроводность, радиус прямого действия
 * и расход запаса. Значения выделены цветом, чтобы ключевая информация
 * читалась сразу.
 * <p>
 * Для блоков мода (приборы с блок-сущностью) деф живёт по id и находится через
 * {@code byId}; для ванильных — через {@link HeatSourceRegistry#defsForBlock(Block)}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class HeatSourceTooltipHandler {

    private HeatSourceTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Block block = Block.byItem(stack.getItem());
        if (block == null) return;

        HeatSourceDef def = HeatSourceRegistry.defForBlock(block);
        if (def == null) return;

        List<Component> tip = event.getToolTip();

        // 1) Активный/Пассивный Нагреватель/Охладитель
        String kind = def.isPassive() ? "пассивный" : "активный";
        String role = def.isCooling() ? "охладитель" : "нагреватель";
        tip.add(Component.translatable("tooltip.trd.heat_source.type",
                Component.literal(kind).withStyle(ChatFormatting.AQUA),
                Component.literal(role).withStyle(def.isCooling() ? ChatFormatting.BLUE : ChatFormatting.RED)));

        // 2) Температура
        tip.add(Component.translatable("tooltip.trd.heat_source.temp",
                value(String.format(Locale.ROOT, "%.0f°C", def.surfaceTemp()))));

        // 3) Теплопроводность
        tip.add(Component.translatable("tooltip.trd.heat_source.conductivity",
                value(String.format(Locale.ROOT, "%.0f%%", def.conductivity() * 100.0f))));

        // 4) Радиус прямого воздействия
        tip.add(Component.translatable("tooltip.trd.heat_source.radius",
                value(String.format(Locale.ROOT, "%.1f", def.radius()))));

        // 5) Расход запаса в процентах в секунду (только для пассивных)
        if (def.isPassive()) {
            tip.add(Component.translatable("tooltip.trd.heat_source.drain",
                    value(String.format(Locale.ROOT, "%.2f%%/сек", def.drainRate() * 100.0f))));
        }
    }

    /** Значение, выделенное цветом. */
    private static Component value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }
}
