package com.trd.multiblock.industrial.coccer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class CoccerOvenRecipe {
    private final Item input;
    private final ItemStack outputItem;
    private final FluidStack outputFluid;
    private final int requiredTemp;
    private final int baseTicks;

    public CoccerOvenRecipe(Item input, ItemStack outputItem, FluidStack outputFluid, int requiredTemp, int baseTicks) {
        this.input = input;
        this.outputItem = outputItem != null ? outputItem : ItemStack.EMPTY;
        this.outputFluid = outputFluid != null ? outputFluid : FluidStack.EMPTY;
        this.requiredTemp = requiredTemp;
        this.baseTicks = baseTicks;
    }

    public Item getInput() { return input; }
    public ItemStack getOutputItem() { return outputItem; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getRequiredTemp() { return requiredTemp; }
    public int getBaseTicks() { return baseTicks; }

    public boolean hasItemOutput() { return !outputItem.isEmpty(); }
    public boolean hasFluidOutput() { return !outputFluid.isEmpty(); }
}