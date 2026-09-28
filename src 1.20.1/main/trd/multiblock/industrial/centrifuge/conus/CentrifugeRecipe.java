package com.trd.multiblock.industrial.centrifuge.conus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class CentrifugeRecipe {

    private final ResourceLocation id;
    private final ItemStack input;
    private final List<ItemStack> outputs;
    private final int processTime;

    public CentrifugeRecipe(ResourceLocation id, ItemStack input, List<ItemStack> outputs, int processTime) {
        this.id = id;
        this.input = input;
        this.outputs = List.copyOf(outputs);
        this.processTime = Math.max(1, processTime);
    }

    public ResourceLocation getId() { return id; }

    public ItemStack getInput() { return input; }

    public List<ItemStack> getOutputs() { return outputs; }

    public int getProcessTime() { return processTime; }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, input);
    }
}
