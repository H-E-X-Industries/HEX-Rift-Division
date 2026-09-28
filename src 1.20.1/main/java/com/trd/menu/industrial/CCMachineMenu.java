package com.trd.menu.industrial;

import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.block.basic.ModBlocks;
import com.trd.menu.ModMenuTypes;
import com.trd.multiblock.industrial.ccmachine.CCMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

public class CCMachineMenu extends AbstractContainerMenu {
    private final CCMachineBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess levelAccess;

    public CCMachineMenu(int id, Inventory inv, CCMachineBlockEntity entity, ContainerData data) {
        super(ModMenuTypes.CC_MACHINE_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;
        this.levelAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());

        // слот формы (0)
        this.addSlot(new SlotItemHandler(entity.getInventory(), CCMachineBlockEntity.SLOT_MOLD, 80, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MoldRecipeRegistry.hasRecipe(stack.getItem());
            }
        });

        // выходные слоты 1-6, сетка 3×2
        for (int i = 0; i < CCMachineBlockEntity.SLOT_OUTPUT_COUNT; i++) {
            int col = i % 3;
            int row = i / 3;
            this.addSlot(new SlotItemHandler(entity.getInventory(),
                    CCMachineBlockEntity.SLOT_OUTPUT_START + i, 62 + col * 18, 60 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // инвентарь игрока (7-42)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 118 + row * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 176));
        }

        this.addDataSlots(data);
    }

    public static CCMachineMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = inv.player.level().getBlockEntity(pos);
        SimpleContainerData data = new SimpleContainerData(9);
        return new CCMachineMenu(id, inv, (CCMachineBlockEntity) entity, data);
    }

    // === Getters для GUI (как в SmelterMenu) ===
    public int getMetalUnits()      { return data.get(0); }
    public int getMetalCapacity()   { return data.get(1); }
    public int getWaterAmount()     { return data.get(2); }
    public int getWaterCapacity()   { return data.get(3); }
    public int getSteamAmount()     { return data.get(4); }
    public int getSteamCapacity()   { return data.get(5); }
    public int getCastProgress()    { return data.get(6); }
    public int getCastRequired()    { return data.get(7); }
    public int getMetalColor()      { return data.get(8); }

    public CCMachineBlockEntity getBlockEntity() { return blockEntity; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.CC_MACHINE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            returnStack = stack.copy();

            if (index < 7) {
                // из машины в инвентарь игрока (7-42)
                if (!this.moveItemStackTo(stack, 7, 43, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // из инвентаря в машину — только форма в слот формы (0)
                if (MoldRecipeRegistry.hasRecipe(stack.getItem())) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return returnStack;
    }
}