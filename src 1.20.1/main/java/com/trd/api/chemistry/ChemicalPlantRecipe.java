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
    private final int minTemperature; // minimum required temperature to process

    public ChemicalPlantRecipe(ResourceLocation id, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs,
                               List<ItemStack> itemInputs, List<ItemStack> itemOutputs, int processTime, int minTemperature) {
        this.id = id;
        this.fluidInputs = new ArrayList<>(fluidInputs);
        this.fluidOutputs = new ArrayList<>(fluidOutputs);
        this.itemInputs = new ArrayList<>(itemInputs);
        this.itemOutputs = new ArrayList<>(itemOutputs);
        this.processTime = processTime;
        this.minTemperature = minTemperature;
    }

    public ResourceLocation getId() { return id; }
    public List<FluidStack> getFluidInputs() { return fluidInputs; }
    public List<FluidStack> getFluidOutputs() { return fluidOutputs; }
    public List<ItemStack> getItemInputs() { return itemInputs; }
    public List<ItemStack> getItemOutputs() { return itemOutputs; }
    public int getProcessTime() { return processTime; }
    public int getMinTemperature() { return minTemperature; }

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
        int emptyTanks = 0;
        for (FluidStack available : fluids) {
            if (available.isEmpty()) emptyTanks++;
        }

        for (FluidStack output : fluidOutputs) {
            int remaining = output.getAmount();
            boolean foundExisting = false;
            for (FluidStack available : fluids) {
                if (!available.isEmpty() && available.getFluid() == output.getFluid()) {
                    foundExisting = true;
                    int space = 16000 - available.getAmount();
                    remaining -= space;
                    break;
                }
            }
            if (remaining > 0) {
                if (!foundExisting && emptyTanks > 0) {
                    emptyTanks--;
                    remaining -= 16000;
                }
            }
            if (remaining > 0) return false;
        }

        List<ItemStack> simulatedItems = new ArrayList<>();
        for (ItemStack stack : items) {
            simulatedItems.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }

        for (ItemStack output : itemOutputs) {
            int remaining = output.getCount();
            for (int i = 0; i < simulatedItems.size(); i++) {
                ItemStack slot = simulatedItems.get(i);
                if (slot.isEmpty()) {
                    ItemStack newStack = output.copy();
                    newStack.setCount(Math.min(remaining, output.getMaxStackSize()));
                    simulatedItems.set(i, newStack);
                    remaining -= newStack.getCount();
                } else if (ItemStack.isSameItemSameTags(slot, output)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int toAdd = Math.min(remaining, space);
                    slot.grow(toAdd);
                    remaining -= toAdd;
                }
                if (remaining <= 0) break;
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}