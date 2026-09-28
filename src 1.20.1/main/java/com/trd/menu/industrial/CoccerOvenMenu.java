package com.trd.menu.industrial;

import com.trd.multiblock.industrial.coccer.CoccerOvenBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class CoccerOvenMenu extends AbstractContainerMenu {
    private final CoccerOvenBlockEntity blockEntity;
    private final ContainerData data;

    public CoccerOvenMenu(int id, Inventory inv, CoccerOvenBlockEntity be, ContainerData data) {
        super(com.trd.menu.ModMenuTypes.COCCER_OVEN_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;

        // Вход
        this.addSlot(new SlotItemHandler(be.getInventory(), 0, 80, 8));
        // Выход
        this.addSlot(new SlotItemHandler(be.getInventory(), 1, 80, 45) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });

        // Инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
            }
        }
        // Хотбар
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 144));
        }

        this.addDataSlots(data);
    }

    public CoccerOvenBlockEntity getBlockEntity() { return blockEntity; }
    public int getTemperature() { return data.get(0); }
    public int getProgress() { return data.get(1); }
    public int getMaxProgress() { return data.get(2); }
    public int getRequiredTemp() { return data.get(3); }
    public boolean isProcessing() { return data.get(4) == 1; }
    public int getFluidAmount() { return data.get(5); }
    public int getFluidCapacity() { return data.get(6); }
    public boolean hasRecipe() { return data.get(7) == 1; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index < 2) {
                if (!this.moveItemStackTo(slotStack, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(slotStack, 0, 1, false)) return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.inventory.ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                .evaluate((lvl, pos) -> lvl.getBlockEntity(pos) == blockEntity, true);
    }
}