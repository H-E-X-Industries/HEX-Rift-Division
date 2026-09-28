package com.trd.menu.industrial;

import com.trd.multiblock.industrial.centrifuge.conus.CentrifugeConusBlockEntity;
import com.trd.menu.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class CentrifugeMenu extends AbstractContainerMenu {

    public static final int INPUT_INDEX = 0;        // (80, 42)
    public static final int FIRST_OUTPUT_INDEX = 1; // колонки (44, 24) и (116, 24)
    public static final int OUTPUT_COUNT = 6;
    public static final int BATTERY_INDEX = 7;      // (154, 62)
    public static final int MACHINE_SLOTS = 8;

    public final CentrifugeConusBlockEntity blockEntity;
    private final ContainerData data;

    public CentrifugeMenu(int id, Inventory inv, CentrifugeConusBlockEntity be, ContainerData data) {
        super(ModMenuTypes.CENTRIFUGE_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;

        this.addSlot(new SlotItemHandler(be.getInventory(), CentrifugeConusBlockEntity.INPUT_SLOT, 80, 42));

        for (int row = 0; row < 3; row++) {
            this.addSlot(new SlotItemHandler(be.getInventory(), FIRST_OUTPUT_INDEX + row, 44, 24 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) {
            this.addSlot(new SlotItemHandler(be.getInventory(), FIRST_OUTPUT_INDEX + 3 + row, 116, 24 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        this.addSlot(new SlotItemHandler(be.getInventory(), CentrifugeConusBlockEntity.BATTERY_SLOT, 154, 62));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 100 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 158));
        }

        this.addDataSlots(data);
    }

    public CentrifugeMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()) instanceof CentrifugeConusBlockEntity be
                        ? be : null,
                new net.minecraft.world.inventory.SimpleContainerData(4));
    }

    public int getProgress() { return data.get(0); }

    public int getMaxProgress() { return data.get(1); }

    public int getEnergy() { return data.get(2); }

    public int getMaxEnergy() { return data.get(3); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index < MACHINE_SLOTS) {
                if (!this.moveItemStackTo(slotStack, MACHINE_SLOTS, MACHINE_SLOTS + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, INPUT_INDEX, INPUT_INDEX + 1, false)
                        && !this.moveItemStackTo(slotStack, BATTERY_INDEX, BATTERY_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.isRemoved()) return false;
        return player.distanceToSqr(blockEntity.getBlockPos().getCenter()) < 64.0;
    }
}
