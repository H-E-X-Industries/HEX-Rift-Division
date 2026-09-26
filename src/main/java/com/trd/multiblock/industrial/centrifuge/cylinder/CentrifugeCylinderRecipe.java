package com.trd.multiblock.industrial.centrifuge.cylinder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class CentrifugeCylinderRecipe {

    private final ResourceLocation id;
    private final FluidStack inputFluid;
    private final List<FluidStack> fluidOutputs;
    private final List<ItemStack> itemOutputs;
    private final int processTime;

    public CentrifugeCylinderRecipe(ResourceLocation id, FluidStack inputFluid,
                                    List<FluidStack> fluidOutputs, List<ItemStack> itemOutputs,
                                    int processTime) {
        this.id = id;
        this.inputFluid = inputFluid.copy();
        this.fluidOutputs = new ArrayList<>();
        for (FluidStack stack : fluidOutputs) {
            if (!stack.isEmpty()) this.fluidOutputs.add(stack.copy());
        }
        this.itemOutputs = new ArrayList<>();
        for (ItemStack stack : itemOutputs) {
            if (!stack.isEmpty()) this.itemOutputs.add(stack.copy());
        }
        this.processTime = Math.max(1, processTime);
    }

    public ResourceLocation getId() { return id; }

    public FluidStack getInputFluid() { return inputFluid; }

    public List<FluidStack> getFluidOutputs() { return fluidOutputs; }

    public List<ItemStack> getItemOutputs() { return itemOutputs; }

    public int getProcessTime() { return processTime; }

    public boolean matches(FluidStack tankFluid) {
        if (tankFluid.isEmpty()) return false;
        if (tankFluid.getFluid() != inputFluid.getFluid()) return false;
        return tankFluid.getAmount() >= inputFluid.getAmount();
    }
}
