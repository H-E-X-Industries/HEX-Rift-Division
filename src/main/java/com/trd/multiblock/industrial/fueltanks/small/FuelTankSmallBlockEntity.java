package com.trd.multiblock.industrial.fueltanks.small;

import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity;
import com.trd.menu.industrial.FuelTankMenu;
import com.trd.multiblock.system.IFluidTankProvider;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class FuelTankSmallBlockEntity extends FluidBarrelBlockEntity implements IFluidTankProvider {

    public static final int CAPACITY = 288_000;
    public static final int FUEL_TANK_MAX_TRANSFER_RATE = Integer.MAX_VALUE;

    public FuelTankSmallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_TANK_SMALL_BE.get(), pos, state);
    }

    @Override
    protected int getMaxTransferRate() {
        return FUEL_TANK_MAX_TRANSFER_RATE;
    }

    @Override
    protected int getTankCapacity() {
        return CAPACITY;
    }

    @Override
    public IFluidHandler getFluidHandlerCapability() {
        return this.networkFluidHandler;
    }

    public IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role) {
        if (side != null) {
            Direction facing = this.getBlockState().getValue(FuelTankSmallBlock.FACING);
            // Allow connection only to front (facing) and back (opposite)
            if (side == facing || side == facing.getOpposite()) {
                return this.networkFluidHandler;
            }
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.fuel_tank_small");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FuelTankMenu(id, inv, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FuelTankSmallBlockEntity be) {
        if (level.isClientSide) return;
        be.updateIdentifierFilter();
        be.processBuckets();
    }
}
