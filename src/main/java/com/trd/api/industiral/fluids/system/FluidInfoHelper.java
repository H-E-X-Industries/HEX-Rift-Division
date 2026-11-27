package com.trd.api.industiral.fluids.system;

import com.trd.api.capabilities.ModDataComponents;
import com.trd.api.nature.ore.oil.OilFraction;
import com.trd.api.nature.ore.oil.ReservoirType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Единый хелпер для получения свойств жидкости (коррозионность, температура,
 * цвет и отображаемое имя) из стека жидкости. Логика согласована с бочками и каплями.
 */
public class FluidInfoHelper {

    /** Температура жидкости в градусах Цельсия (с учётом NBT-переопределения). */
    public static int getTemperature(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 20;
        int nbtTemp = FluidPropertyHelper.getTemperature(stack);
        Fluid fluid = stack.getFluid();
        int defaultTemp = fluid.getFluidType().getTemperature();
        if (nbtTemp != defaultTemp) return nbtTemp;
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) return 20;
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return 1000;
        if (fluid.getFluidType() instanceof BaseFluidType base) return base.getDisplayTemperature();
        return defaultTemp - 273;
    }

    /** Коррозионность жидкости (с учётом NBT-переопределения). */
    public static int getCorrosivity(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int nbt = FluidPropertyHelper.getCorrosivity(stack);
        if (nbt > 0) return nbt;
        if (stack.getFluid().getFluidType() instanceof BaseFluidType base) return base.getCorrosivity();
        return 0;
    }

    /** ARGB-цвет жидкости в стейке. */
    public static int getColor(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0xFFFFFFFF;
        Fluid fluid = stack.getFluid();
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return 0xFFE64306;
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) return 0xFF3F76E4;
        try {
            return IClientFluidTypeExtensions.of(fluid.getFluidType()).getTintColor(stack) | 0xFF000000;
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    /** RGB-цвет жидкости (без альфа-канала). */
    public static int getRgbColor(FluidStack stack) {
        return getColor(stack) & 0xFFFFFF;
    }

    /**
     * Отображаемое имя жидкости. Пробует взять имя из предмета-капли
     * (чтобы использовать уже переведённые названия), иначе формирует ключ fluid.*.
     */
    public static Component getDisplayName(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.translatable("tooltip.trd.fluid_barrel.empty").withStyle(ChatFormatting.GRAY);
        }
        Supplier<Component> coloredName = () -> getColoredName(stack);
        return coloredName.get();
    }

    private static Component getColoredName(FluidStack stack) {
        String id = net.minecraft.core.registries.BuiltInRegistries.FLUID
                .getKey(stack.getFluid()).toString();
        int color = getRgbColor(stack);
        Component comp;
        try {
            String dropId = id.replace(":", ":fluid_drop_");
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse(dropId));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                comp = item.getDescription().copy();
            } else {
                comp = Component.translatable("fluid." + id.replace(":", "."));
            }
        } catch (Exception e) {
            comp = Component.literal(id);
        }
        return comp.copy().withStyle(style -> style.withColor(color));
    }

    /** Добавляет строки параметров скважинного флюида из DataComponentType в тултип. */
    public static void appendWellheadFluidTooltip(FluidStack stack, List<Component> tooltip) {
        if (stack == null || stack.isEmpty()) return;
        ModDataComponents.WellheadFluidData data = stack.get(ModDataComponents.WELLHEAD_FLUID_DATA.get());
        if (data != null) {
            appendWellheadDataTooltip(data, tooltip);
        }
    }

    public static void appendWellheadDataTooltip(ModDataComponents.WellheadFluidData data, List<Component> tooltip) {
        tooltip.add(Component.literal("§6=== [Характеристики скважинной жидкости] ==="));
        appendOilGeochemistryTooltip(
                tooltip,
                data.getReservoirTypeEnum(),
                true,
                data::getFractionPercent,
                data.gasRatio(),
                data.pressure(),
                data.sulfur()
        );
    }

    /**
     * Общий подробный геохимический тултип (единые цвета и детали для пробы грунта и скважинного флюида).
     */
    public static void appendOilGeochemistryTooltip(List<Component> tooltip,
                                                    ReservoirType resType,
                                                    boolean showFractions,
                                                    ToIntFunction<OilFraction> fractionLookup,
                                                    float gasRatio,
                                                    float pressure,
                                                    float sulfur) {
        tooltip.add(Component.literal("§7Коллектор: §f" + resType.getDisplayNameRu()));

        if (showFractions) {
            tooltip.add(Component.literal("§eУглеводородный спектр:"));
            for (OilFraction f : OilFraction.values()) {
                int pct = fractionLookup.applyAsInt(f);
                MutableComponent line = Component.literal("  • ")
                        .withStyle(style -> style.withColor(f.getColor()))
                        .append(Component.literal(f.getDisplayNameRu() + ": ")
                                .withStyle(style -> style.withColor(f.getColor())))
                        .append(Component.literal(pct + "%")
                                .withStyle(ChatFormatting.WHITE));
                tooltip.add(line);
            }
        }

        float corrosiveness = Math.min(1.0f, Math.max(0.0f, sulfur * 20.0f)) * 100.0f;

        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Газовый фактор (GOR): §f%.1f%%", gasRatio * 100.0f)));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Пластовое давление: §f%.1f bar", pressure)));

        if (sulfur > 0.0f) {
            String corrSeverity = corrosiveness >= 65.0f ? "§4Критическая"
                    : (corrosiveness >= 35.0f ? "§cВысокая" : "§eУмеренная");

            tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Сернистость: §cКислая (%.1f%% S)", sulfur * 100.0f)));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Коррозийность сырья: %s (%.1f%%)", corrSeverity, corrosiveness)));
        } else {
            tooltip.add(Component.literal("§7Сернистость: §aСладкая (0.0% S)"));
            tooltip.add(Component.literal("§7Коррозийность сырья: §aИнертная (0.0%)"));
        }

        tooltip.add(Component.literal("§dТехнологические требования:"));
        boolean hasWarning = false;

        if (resType != ReservoirType.TAR_SANDS && (pressure >= 150.0f || (gasRatio >= 0.40f && pressure >= 80.0f))) {
            tooltip.add(Component.literal(" §c⚠ ТРЕБУЕТСЯ ПРЕВЕНТОР (BOP): Критическое давление газа! Скважину сдетонирует без устьевого превентора."));
            hasWarning = true;
        }

        if (sulfur > 0.0f) {
            tooltip.add(Component.literal(String.format(Locale.ROOT,
                    " §e⚠ ТРЕБУЕТСЯ МОДУЛЬ ГИДРООЧИСТКИ: Высокое содержание H₂S (Коррозия %.1f%%). Без десульфуризации вызывает ускоренный износ бура и разрушение труб при выкачке.", corrosiveness)));
            hasWarning = true;
        }

        if (resType == ReservoirType.TIGHT_SHALE) {
            tooltip.add(Component.literal(" §6⚠ ТРЕБУЕТСЯ МОДУЛЬ ФРЕКИНГА: Низкопроницаемый сланец. Требуется гидроразрыв и подача бурового раствора."));
            hasWarning = true;
        } else if (resType == ReservoirType.TAR_SANDS) {
            tooltip.add(Component.literal(" §b⚠ ТРЕБУЕТСЯ ПАРОВОЙ ИНЖЕКТОР (EOR): Застывший битум. Требуется нагнетание пара для снижения вязкости."));
            hasWarning = true;
        }

        if (!hasWarning) {
            tooltip.add(Component.literal(" §a✓ Стандартная добыча: Осложнений нет, достаточно базового насоса."));
        }
    }

    /** Извлекает WellheadFluidData из сериализованного NBT-тега FluidTank (поле "Fluid"). */
    @Nullable
    public static ModDataComponents.WellheadFluidData parseWellheadDataFromFluidTag(@Nullable CompoundTag fluidTag) {
        if (fluidTag == null || fluidTag.isEmpty()) return null;
        if (!fluidTag.contains("components", CompoundTag.TAG_COMPOUND)) return null;
        CompoundTag comps = fluidTag.getCompound("components");
        if (!comps.contains("trd:wellhead_fluid_data")) return null;
        return ModDataComponents.WELLHEAD_FLUID_DATA_CODEC
                .parse(NbtOps.INSTANCE, comps.get("trd:wellhead_fluid_data"))
                .result()
                .orElse(null);
    }
}
