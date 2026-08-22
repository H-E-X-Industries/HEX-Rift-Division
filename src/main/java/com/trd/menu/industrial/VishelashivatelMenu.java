package com.trd.menu.industrial;

import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class VishelashivatelMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT_INDEX = 0;      // вход (26,45)
    public static final int FIRST_OUTPUT_INDEX = 1;    // выходы (99/117/135,45)
    public static final int OUTPUT_COUNT = 3;
    public static final int IDENTIFIER_INDEX = 4;      // жидкостный идентификатор (26,21)
    public static final int MACHINE_SLOTS = 5;

    private final VishelashivatelBlockEntity blockEntity;
    private final ContainerData data;

    public VishelashivatelMenu(int id, Inventory inv, VishelashivatelBlockEntity be, ContainerData data) {
        super(com.trd.menu.ModMenuTypes.VISHELASHIVATEL_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;

        // Жидкостный идентификатор
        this.addSlot(new SlotItemHandler(be.getInventory(), IDENTIFIER_INDEX, 26, 21));
        // Входной слот
        this.addSlot(new SlotItemHandler(be.getInventory(), INPUT_SLOT_INDEX, 26, 45));
        // Выходные слоты — 3 в ряд
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            this.addSlot(new SlotItemHandler(be.getInventory(), FIRST_OUTPUT_INDEX + i, 98 + i * 18, 45) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        // Инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 71 + row * 18));
            }
        }
        // Хотбар
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 129));
        }

        this.addDataSlots(data);
    }

    /** Конструктор сетевой стороны. */
    public VishelashivatelMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf extraData) {
        this(id, inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()) instanceof VishelashivatelBlockEntity be
                        ? be : null,
                new net.minecraft.world.inventory.SimpleContainerData(8));
    }

    public VishelashivatelBlockEntity getBlockEntity() { return blockEntity; }

    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }
    public int getFluidAmount() { return data.get(2); }
    public int getFluidCapacity() { return data.get(3); }
    public int getSpeed() { return data.get(4); }
    public int getMinRpm() { return data.get(5); }
    public boolean hasRecipe() { return data.get(6) == 1; }
    public boolean hasTarget() { return data.get(7) == 1; }

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
                if (!this.moveItemStackTo(slotStack, 0, MACHINE_SLOTS, false)) {
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
