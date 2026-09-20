package com.trd.multiblock.industrial.vishelashivatel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Рецепт выщелащивателя: жидкость взаимодействует с предметом во входном слоте.
 * Время производства, расход жидкости, минимальные обороты и потребляемый
 * крутящий момент задаются рецептом.
 */
public class VishelashivatelRecipe {

    private final ResourceLocation id;
    /** Требуемая жидкость (тип + расход за одну операцию). */
    private final FluidStack requiredFluid;
    /** Входной предмет (тип + количество за одну операцию). */
    private final ItemStack itemInput;
    /** Выходные предметы. */
    private final List<ItemStack> itemOutputs;
    /** Время производства в тиках. */
    private final int processTime;
    /** Минимальная скорость вращения (об/мин). */
    private final long minRpm;
    /** Потребляемый крутящий момент. */
    private final long consumedTorque;

    public VishelashivatelRecipe(ResourceLocation id, FluidStack requiredFluid, ItemStack itemInput,
                                 List<ItemStack> itemOutputs, int processTime, long minRpm, long consumedTorque) {
        this.id = id;
        this.requiredFluid = requiredFluid.copy();
        this.itemInput = itemInput.copy();
        this.itemOutputs = new ArrayList<>();
        for (ItemStack stack : itemOutputs) {
            if (!stack.isEmpty()) this.itemOutputs.add(stack.copy());
        }
        this.processTime = processTime;
        this.minRpm = minRpm;
        this.consumedTorque = consumedTorque;
    }

    public ResourceLocation getId() { return id; }
    public FluidStack getRequiredFluid() { return requiredFluid; }
    public ItemStack getItemInput() { return itemInput; }
    public List<ItemStack> getItemOutputs() { return itemOutputs; }
    public int getProcessTime() { return processTime; }
    public long getMinRpm() { return minRpm; }
    public long getConsumedTorque() { return consumedTorque; }

    /**
     * Проверка совпадения: входной предмет подходит по типу, а в баке
     * достаточно требуемой жидкости нужного типа.
     */
    public boolean matches(ItemStack input, FluidStack tankFluid) {
        if (input.isEmpty() || !ItemStack.isSameItemSameTags(input, itemInput)) return false;
        if (tankFluid.isEmpty()) return false;
        if (tankFluid.getFluid() != requiredFluid.getFluid()) return false;
        return tankFluid.getAmount() >= requiredFluid.getAmount();
    }
}
