package com.trd.multiblock.industrial.centrifuge.cylinder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Рецепт жидкостной насадки центрифуги (centrifuge_cylinder):
 * жидкость из входного буфера разделяется на другие жидкости и/или предметы.
 */
public class CentrifugeCylinderRecipe {

    private final ResourceLocation id;
    /** Входная жидкость (тип + расход за одну операцию). */
    private final FluidStack inputFluid;
    /** Выходные жидкости (до 4). */
    private final List<FluidStack> fluidOutputs;
    /** Выходные предметы (до 4). */
    private final List<ItemStack> itemOutputs;
    /** Время производства в тиках. */
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

    /** Проверка совпадения: в буфере достаточно требуемой жидкости нужного типа. */
    public boolean matches(FluidStack tankFluid) {
        if (tankFluid.isEmpty()) return false;
        if (tankFluid.getFluid() != inputFluid.getFluid()) return false;
        return tankFluid.getAmount() >= inputFluid.getAmount();
    }
}
