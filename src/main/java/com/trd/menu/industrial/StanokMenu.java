package com.trd.menu.industrial;

import com.trd.menu.ModMenuTypes;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Container menu для станка.
 *
 * Раскладка слотов (GUI-координаты из ТЗ):
 *   0–5   входные  — 2 строки × 3 столбца, старт x8,y21, шаг 20px (18px + 2px отступ)
 *   6–11  выходные — 2 строки × 3 столбца, старт x116,y21, шаг 20px
 *   12    насадка  — x80,y8
 *   13–39 инвентарь игрока (3×9) x8,y65
 *   40–48 хотбар x8,y123
 */
public class StanokMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Серверный конструктор */
    public StanokMenu(int id, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.STANOK_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;

        if (entity instanceof StanokBlockEntity sbe) {
            net.minecraftforge.items.IItemHandler handler = sbe.getInventory();
            // Входные слоты: 2 строки × 3 столбца, шаг 18px (стандарт)
            for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 3; col++) {
                        int slotIdx = row * 3 + col;
                        addSlot(new SlotItemHandler(handler, slotIdx,
                                8 + col * 18, 21 + row * 18));
                    }
                }
                // Выходные слоты: 2 строки × 3 столбца, шаг 18px (только extract)
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 3; col++) {
                        int slotIdx = StanokBlockEntity.INPUT_SLOTS + row * 3 + col;
                        addSlot(new SlotItemHandler(handler, slotIdx,
                                116 + col * 18, 21 + row * 18) {
                            @Override
                            public boolean mayPlace(ItemStack stack) { return false; }
                        });
                    }
                }
                // Слот насадки
                addSlot(new SlotItemHandler(handler, StanokBlockEntity.CARRIAGE_SLOT, 80, 8));
            }

        // Инвентарь игрока: x8, y65
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 65 + row * 18));
            }
        }
        // Хотбар: x8, y123
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 123));
        }

        addDataSlots(data);
    }

    /** Клиентский конструктор */
    public StanokMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, getBlockEntity(inv, buf), new SimpleContainerData(5));
    }

    private static BlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        if (buf == null) return null;
        BlockPos pos = buf.readBlockPos();
        return inv.player.level().getBlockEntity(pos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            // Машинные слоты: 0–12 (6 вход + 6 выход + 1 насадка)
            if (index <= 12) {
                if (!this.moveItemStackTo(stack, 13, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря — пробуем слот насадки, потом входные
                if (!this.moveItemStackTo(stack, 12, 13, false)) {
                    if (!this.moveItemStackTo(stack, 0, 6, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    // ─── Data accessors для GUI ───
    public int getProgress()      { return data.get(0); }
    public int getMaxProgress()   { return data.get(1); }
    public int getCarriageType()  { return data.get(2); } // ordinal или -1
    public int getSpeedStatus()   { return data.get(3); } // 0=OK,1=slow,2=fast
    public boolean hasInputs()    { return data.get(5) == 1; } // есть ли нужный материал

    public BlockEntity getBlockEntity() { return blockEntity; }
}
