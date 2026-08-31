package com.trd.menu.industrial;

import com.trd.menu.ModMenuTypes;
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
import net.minecraftforge.items.SlotItemHandler;

public class DrobitelMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final BlockEntity blockEntity;
    public boolean isNetworkConnected() {
        return this.data.get(6) > 0;
    }
    // Серверный конструктор (вызывается из DrobitelBlockEntity.createMenu)
    public DrobitelMenu(int id, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.DROBITEL_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;

        if (entity instanceof com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity dbe) {
            net.minecraftforge.items.IItemHandler handler = dbe.getInventory();
            // Входные слоты 3x3: 8,29
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        addSlot(new SlotItemHandler(handler, col + row * 3, 8 + col * 18, 29 + row * 18));
                    }
                }
                // Выходные слоты 3x7: 116,29
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 7; col++) {
                        addSlot(new SlotItemHandler(handler, 9 + col + row * 7, 116 + col * 18, 29 + row * 18) {
                            @Override
                            public boolean mayPlace(ItemStack stack) {
                                return false;
                            }
                        });
                    }
                }
            }

        // Инвентарь игрока: 44,91
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 44 + col * 18, 91 + row * 18));
            }
        }
        // Хотбар
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 44 + col * 18, 149));
        }

        addDataSlots(data);
    }

    // Клиентский конструктор (вызывается Forge при открытии GUI)
    public DrobitelMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, getBlockEntity(inv, extraData), new SimpleContainerData(11));
    }

    private static BlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf data) {
        if (data == null) return null;
        BlockPos pos = data.readBlockPos();
        return inv.player.level().getBlockEntity(pos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();

            if (index < 30) { // вход + выход
                if (!this.moveItemStackTo(stack, 30, 66, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря игрока — только во входные слоты
                if (!this.moveItemStackTo(stack, 0, 9, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }
    public int getBlade1Durability() { return data.get(2); }
    public int getBlade2Durability() { return data.get(3); }
    public boolean hasBlade1() { return data.get(4) == 1; }
    public boolean hasBlade2() { return data.get(5) == 1; }
    public boolean isOverstressed() { return data.get(7) == 1; }
    public boolean isTooSlow() { return data.get(8) == 1; }
    public int getBlade1MaxDurability() { return data.get(9); }
    public int getBlade2MaxDurability() { return data.get(10); }
}