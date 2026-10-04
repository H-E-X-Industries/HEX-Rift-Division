package com.trd.menu.industrial;

import com.trd.menu.ModMenuTypes;
import com.trd.multiblock.industrial.steel_storage.SteelStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SteelStorageMenu extends AbstractContainerMenu {

    public static final int SLOTS = SteelStorageBlockEntity.SLOTS;
    private final SteelStorageBlockEntity blockEntity;
    /** Сохраняет содержимое обратно в предмет — используется версией хранилища-предмета. */
    @Nullable
    private final Runnable saveAction;

    // Серверный конструктор (хранилище стоит как блок)
    public SteelStorageMenu(int id, Inventory playerInv, SteelStorageBlockEntity be) {
        super(ModMenuTypes.STEEL_STORAGE_MENU.get(), id);
        this.blockEntity = be;
        this.saveAction = null;
        init(playerInv, be != null ? be.getInventory() : new ItemStackHandler(SLOTS));
    }

    /**
     * Серверный конструктор для хранилища-предмета: инвентарь лежит в NBT предмета,
     * а {@code saveAction} записывает его обратно при любом изменении.
     */
    public SteelStorageMenu(int id, Inventory playerInv, ItemStackHandler inv, @Nullable Runnable saveAction) {
        super(ModMenuTypes.STEEL_STORAGE_MENU.get(), id);
        this.blockEntity = null;
        this.saveAction = saveAction;
        init(playerInv, inv);
    }

    private void init(Inventory playerInv, IItemHandler inv) {
        // Сетка 13×7, начало (8, 15)
        for (int row = 0; row < SteelStorageBlockEntity.ROWS; row++) {
            for (int col = 0; col < SteelStorageBlockEntity.COLS; col++) {
                int index = row * SteelStorageBlockEntity.COLS + col;
                int x = 8 + col * 18;
                int y = 15 + row * 18;
                this.addSlot(new SlotItemHandler(inv, index, x, y));
            }
        }

        // Инвентарь игрока (44, 145)
        int invX = 44;
        int invY = 145;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }

        // Хотбар (44, 203) — 145 + 58
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, invX + col * 18, invY + 58));
        }
    }

    // Клиентский конструктор
    public SteelStorageMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, getBlockEntity(playerInv, buf));
    }

    private static SteelStorageBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        // У хранилища-предмета сервер не пишет позицию, поэтому буфер пустой
        if (buf == null || buf.readableBytes() < Long.BYTES) return null;
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level().getBlockEntity(pos) instanceof SteelStorageBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (saveAction != null) {
            saveAction.run();
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < SLOTS) {
                if (!this.moveItemStackTo(itemstack1, SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return blockEntity == null || !blockEntity.isRemoved();
    }
}
