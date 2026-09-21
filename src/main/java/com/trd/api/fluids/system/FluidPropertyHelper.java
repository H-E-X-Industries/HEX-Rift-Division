package com.trd.api.fluids.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidPropertyHelper {
    private static final String TAG_CORROSIVITY = "Corrosivity";
    private static final String TAG_TEMPERATURE = "Temperature";

    public static FluidStack setProperties(FluidStack stack, int corrosivity, int temperature) {
        if (stack.isEmpty()) return stack;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData newData = customData.update(nbt -> {
            nbt.putInt(TAG_CORROSIVITY, Math.max(0, corrosivity));
            nbt.putInt(TAG_TEMPERATURE, temperature);
        });
        stack.set(DataComponents.CUSTOM_DATA, newData);
        return stack;
    }

    public static int getCorrosivity(FluidStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 0;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TAG_CORROSIVITY);
    }

    public static int getTemperature(FluidStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return stack.getFluid().getFluidType().getTemperature();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG_TEMPERATURE)) {
            return tag.getInt(TAG_TEMPERATURE);
        }
        return stack.getFluid().getFluidType().getTemperature();
    }

    public static FluidStack createSulfuricAcid(int amount) {
        return setProperties(new FluidStack(com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get(), amount), 80, 20);
    }

    public static FluidStack createSteam(int amount) {
        return setProperties(new FluidStack(com.trd.api.fluids.ModFluids.STEAM_SOURCE.get(), amount), 0, 100);
    }
}
