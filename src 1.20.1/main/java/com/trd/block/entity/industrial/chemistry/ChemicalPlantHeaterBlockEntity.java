package com.trd.block.entity.industrial.chemistry;

import com.trd.block.basic.industrial.chemistry.ChemicalPlantHeaterBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.api.energy.EnergyNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.capability.ModCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChemicalPlantHeaterBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    public static final long MAX_ENERGY = 1000L;
    public static final long RECEIVE_SPEED = 1000L;
    
    // Modes: 0 = Off, 1 = 50C (50 FE/s), 2 = 100C (100 FE/s)
    private int mode = 0;
    private int activeTemperature = 0;
    private int tickCounter = 0;
    private long energyStored = 0L;

    private final LazyOptional<IEnergyReceiver> receiverCap = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyConnector> connectorCap = LazyOptional.of(() -> this);

    public ChemicalPlantHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT_HEATER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(worldPosition);
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        connectorCap.invalidate();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChemicalPlantHeaterBlockEntity be) {
        if (level.isClientSide) return;

        boolean changed = false;
        be.tickCounter++;

        if (be.mode > 0) {
            int consumption = be.mode == 1 ? 50 : 100;
            int providedTemp = be.mode == 1 ? 50 : 100;
            
            // Consume energy every 20 ticks (1 second)
            if (be.tickCounter >= 20) {
                be.tickCounter = 0;
                if (be.energyStored >= consumption) {
                    be.energyStored -= consumption;
                    if (be.activeTemperature != providedTemp) {
                        be.activeTemperature = providedTemp;
                        changed = true;
                    }
                } else {
                    if (be.activeTemperature != 0) {
                        be.activeTemperature = 0; // Not enough energy
                        changed = true;
                    }
                }
            }
        } else {
            if (be.activeTemperature != 0) {
                be.activeTemperature = 0;
                changed = true;
            }
        }

        // Sync every tick for smooth HUD, just like MotorElectro
        level.sendBlockUpdated(pos, state, state, 3);
        if (changed) be.setChanged();
    }

    public int getMode() { return mode; }
    
    public void setMode(int mode) {
        this.mode = mode;
        this.tickCounter = 20; // force update on next tick
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getActiveTemperature() {
        return activeTemperature;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
        tag.putLong("Energy", energyStored);
        tag.putInt("ActiveTemperature", activeTemperature);
        tag.putInt("TickCounter", tickCounter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = tag.getInt("Mode");
        energyStored = tag.getLong("Energy");
        activeTemperature = tag.getInt("ActiveTemperature");
        tickCounter = tag.getInt("TickCounter");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (side == null || side != facing) {
            if (cap == ModCapabilities.ENERGY_RECEIVER) return receiverCap.cast();
            if (cap == ModCapabilities.ENERGY_CONNECTOR) return connectorCap.cast();
        }
        return super.getCapability(cap, side);
    }

    // ===================== IEnergyConnector =====================
    @Override
    public boolean canConnectEnergy(Direction side) {
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return side == null || side != facing;
    }

    // ===================== IEnergyReceiver =====================
    @Override
    public long getEnergyStored() { return energyStored; }

    @Override
    public long getMaxEnergyStored() { return MAX_ENERGY; }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY));
    }

    @Override
    public long getReceiveSpeed() { return RECEIVE_SPEED; }

    @Override
    public Priority getPriority() { return Priority.NORMAL; }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        long canReceive = Math.min(MAX_ENERGY - energyStored, Math.min(maxReceive, RECEIVE_SPEED));
        if (!simulate && canReceive > 0) {
            energyStored += canReceive;
            setChanged();
        }
        return canReceive;
    }

    @Override
    public boolean canReceive() { return energyStored < MAX_ENERGY; }

    public void nextMode() {
        int next = mode + 1;
        if (next > 2) next = 0;
        setMode(next);
    }
}
