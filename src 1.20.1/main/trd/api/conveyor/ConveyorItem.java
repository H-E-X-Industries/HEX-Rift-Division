package com.trd.api.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class ConveyorItem {
    private ItemStack stack;
    private double progress;

    // Серверный иммунитет от повторного захвата сортировщиком сразу после выдачи
    // (транзиентно: не сериализуется в NBT и не синкается клиентам)
    private int sorterCooldown;

    /**
     * Позиция предыдущего блока, из которого предмет пришёл при вставке из другой сети
     * (например, перпендикулярный конвейер на Т-образном перекрёстке).
     * Не сохраняется в NBT, но синхронизируется в пакете.
     * Используется рендерером для правильного расчёта дуги Безье.
     */
    @Nullable
    private BlockPos prevOverridePos;

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

    public int getSorterCooldown() {
        return sorterCooldown;
    }

    public void setSorterCooldown(int ticks) {
        this.sorterCooldown = ticks;
    }

    @Nullable
    public BlockPos getPrevOverridePos() {
        return prevOverridePos;
    }

    public void setPrevOverridePos(@Nullable BlockPos pos) {
        this.prevOverridePos = pos;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", stack.save(new CompoundTag()));
        tag.putDouble("Progress", progress);
        return tag;
    }
}
