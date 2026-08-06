package com.trd.api.chemistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class ChemicalPlantRecipe {
    private final ResourceLocation id;
    private final List<FluidStack> fluidInputs;
    private final List<FluidStack> fluidOutputs;
    private final List<ItemStack> itemInputs;
    private final List<ItemStack> itemOutputs;
    private final int processTime; // ticks

    public ChemicalPlantRecipe(ResourceLocation id, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs,
                               List<ItemStack> itemInputs, List<ItemStack> itemOutputs, int processTime) {
        this.id = id;
        this.fluidInputs = new ArrayList<>(fluidInputs);
        this.fluidOutputs = new ArrayList<>(fluidOutputs);
        this.itemInputs = new ArrayList<>(itemInputs);
        this.itemOutputs = new ArrayList<>(itemOutputs);
        this.processTime = processTime;
    }

    public ResourceLocation getId() { return id; }
    public List<FluidStack> getFluidInputs() { return fluidInputs; }
    public List<FluidStack> getFluidOutputs() { return fluidOutputs; }
    public List<ItemStack> getItemInputs() { return itemInputs; }
    public List<ItemStack> getItemOutputs() { return itemOutputs; }
    public int getProcessTime() { return processTime; }

    public boolean matches(List<FluidStack> fluids, List<ItemStack> items) {
        for (FluidStack required : fluidInputs) {
            int found = 0;
            for (FluidStack available : fluids) {
                if (available.getFluid() == required.getFluid()) {
                    found += available.getAmount();
                }
            }
            if (found < required.getAmount()) return false;
        }
        for (ItemStack required : itemInputs) {
            int found = 0;
            for (ItemStack available : items) {
                if (ItemStack.isSameItemSameTags(available, required)) {
                    found += available.getCount();
                }
            }
            if (found < required.getCount()) return false;
        }
        return true;
    }

    public boolean canFitOutputs(List<FluidStack> fluids, List<ItemStack> items, int fluidTankCount, int outputSlotCount) {
        for (FluidStack output : fluidOutputs) {
            int remaining = output.getAmount();
            for (FluidStack available : fluids) {
                if (!available.isEmpty() && available.getFluid() == output.getFluid()) {
                    int space = 16000 - available.getAmount();
                    remaining -= Math.min(remaining, space);
                    if (remaining <= 0) break;
                }
            }
            if (remaining > 0) {
                int emptyTanks = 0;
                for (FluidStack available : fluids) {
                    if (available.isEmpty()) emptyTanks++;
                }
                int space = emptyTanks * 16000;
                remaining -= Math.min(remaining, space);
            }
            if (remaining > 0) return false;
        }
        for (ItemStack output : itemOutputs) {
            int remaining = output.getCount();
            for (ItemStack slot : items) {
                if (slot.isEmpty()) {
                    remaining = 0;
                    break;
                } else if (ItemStack.isSameItemSameTags(slot, output)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    remaining -= Math.min(remaining, space);
                    if (remaining <= 0) break;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}