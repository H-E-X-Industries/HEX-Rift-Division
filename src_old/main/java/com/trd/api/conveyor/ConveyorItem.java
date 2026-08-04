package com.trd.api.conveyor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class ConveyorItem {
    private ItemStack stack;
    private double progress;

    public ConveyorItem(ItemStack stack, double progress) {
        this.stack = stack.copy();
        this.progress = progress;
    }

    public ConveyorItem(CompoundTag tag) {
        this.stack = ItemStack.of(tag.getCompound("Stack"));
        this.progress = tag.getDouble("Progress");
    }

    public ItemStack getStack() {
        return stack;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", stack.save(new CompoundTag()));
        tag.putDouble("Progress", progress);
        return tag;
    }
}
