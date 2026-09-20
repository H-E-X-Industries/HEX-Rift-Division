package com.trd.api.fluids.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

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
        String id = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                .getKey(stack.getFluid()).toString();
        int color = getRgbColor(stack);
        Component comp;
        try {
            String dropId = id.replace(":", ":fluid_drop_");
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(new net.minecraft.resources.ResourceLocation(dropId));
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
}
