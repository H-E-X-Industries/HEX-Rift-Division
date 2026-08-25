package com.trd.menu.industrial;

import com.trd.menu.ModMenuTypes;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class CentrifugeCylinderMenu extends AbstractContainerMenu {

    public static final int IDENTIFIER_INDEX = 0;   // (28, 80)
    public static final int FIRST_OUTPUT_INDEX = 1; // (58/78/98/118, 80)
    public static final int OUTPUT_COUNT = 4;
    public static final int BATTERY_INDEX = 5;      // (146, 82)
    public static final int MACHINE_SLOTS = 6;

    public final CentrifugeCylinderBlockEntity blockEntity;
    private final ContainerData data;

    public CentrifugeCylinderMenu(int id, Inventory inv, CentrifugeCylinderBlockEntity be, ContainerData data) {
        super(ModMenuTypes.CENTRIFUGE_CYLINDER_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;

        this.addSlot(new SlotItemHandler(be.getInventory(), CentrifugeCylinderBlockEntity.IDENTIFIER_SLOT, 28, 80));

        for (int i = 0; i < OUTPUT_COUNT; i++) {
            this.addSlot(new SlotItemHandler(be.getInventory(),
                    CentrifugeCylinderBlockEntity.FIRST_OUTPUT_SLOT + i, 58 + i * 20, 80) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        this.addSlot(new SlotItemHandler(be.getInventory(), CentrifugeCylinderBlockEntity.BATTERY_SLOT, 146, 82));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 174));
        }

        this.addDataSlots(data);
    }

    public CentrifugeCylinderMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()) instanceof CentrifugeCylinderBlockEntity be
                        ? be : null,
                new net.minecraft.world.inventory.SimpleContainerData(4));
    }

    public CentrifugeCylinderBlockEntity getBlockEntity() { return blockEntity; }

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
                if (!this.moveItemStackTo(slotStack, IDENTIFIER_INDEX, IDENTIFIER_INDEX + 1, false)
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
