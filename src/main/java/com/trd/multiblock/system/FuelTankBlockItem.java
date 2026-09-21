package com.trd.multiblock.system;

import com.trd.api.fluids.system.FluidInfoHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;

public class FuelTankBlockItem extends MultiblockBlockItem {

    private final int capacity;

    public FuelTankBlockItem(Block block, int capacity, Properties properties) {
        super(block, properties);
        this.capacity = capacity;
    }

    private FluidStack readFluid(ItemStack stack) {
        if (!stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            return FluidStack.EMPTY;
        }
        CompoundTag be = stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
        String name = be.getString("FluidName");
        int amount = be.getInt("Amount");
        if (name.isEmpty() || name.equals("minecraft:empty") || amount <= 0) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(name));
        if (fluid == null || fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        return new FluidStack(fluid, amount);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return readFluid(stack).getAmount() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * readFluid(stack).getAmount() / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        FluidStack fluid = readFluid(stack);
        return fluid.isEmpty() ? 0xFFFFFF : FluidInfoHelper.getRgbColor(fluid);
    }

    public int getCapacity() { return capacity; }
}
