package com.trd.menu.industrial;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantPortBlockEntity;
import com.trd.menu.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ChemicalPlantPortMenu extends AbstractContainerMenu {
    public final ChemicalPlantPortBlockEntity blockEntity;

    public ChemicalPlantPortMenu(int id, Inventory inv, BlockEntity entity) {
        super(ModMenuTypes.CHEMICAL_PLANT_PORT_MENU.get(), id);
        this.blockEntity = entity instanceof ChemicalPlantPortBlockEntity port ? port : null;

        var handler = this.blockEntity != null ? this.blockEntity.getItemHandler() : new ItemStackHandler(9);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(handler, col + row * 3, 113 + col * 18, 29 + row * 18));
            }
        }

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
    }

    public ChemicalPlantPortMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public FluidStack getFluidA() {
        return blockEntity != null ? blockEntity.getTankA().getFluid() : FluidStack.EMPTY;
    }

    public FluidStack getFluidB() {
        return blockEntity != null ? blockEntity.getTankB().getFluid() : FluidStack.EMPTY;
    }

    public int getMode() {
        return blockEntity != null ? blockEntity.getMode() : 0;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.getLevel() == null) return false;
        return player.distanceToSqr(blockEntity.getBlockPos().getCenter()) < 64.0;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 91 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 91 + 3 * 18 + 4));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index < 9) {
                if (!this.moveItemStackTo(stack, 9, 45, true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }
}
