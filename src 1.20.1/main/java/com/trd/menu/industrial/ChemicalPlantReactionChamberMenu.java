package com.trd.menu.industrial;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import com.trd.menu.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ChemicalPlantReactionChamberMenu extends AbstractContainerMenu {
    public final ChemicalPlantReactionChamberBlockEntity blockEntity;
    private final ContainerData data;

    public ChemicalPlantReactionChamberMenu(int id, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.CHEMICAL_PLANT_REACTION_CHAMBER_MENU.get(), id);
        this.blockEntity = (ChemicalPlantReactionChamberBlockEntity) entity;
        this.data = data;
        addDataSlots(data);
    }

    public ChemicalPlantReactionChamberMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(3));
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public int getCurrentTemperature() {
        return data.get(2);
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity.getLevel() == null) return false;
        return player.distanceToSqr(blockEntity.getBlockPos().getCenter()) < 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return ItemStack.EMPTY;
    }
}