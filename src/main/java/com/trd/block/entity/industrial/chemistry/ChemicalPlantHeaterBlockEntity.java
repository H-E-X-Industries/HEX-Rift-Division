package com.trd.block.entity.industrial.chemistry;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChemicalPlantHeaterBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    public static final long MAX_ENERGY = 1000L;
    public static final long RECEIVE_SPEED = 1000L;

    // Modes: 0 = Off, 1 = 50C (50 FE/s), 2 = 100C (100 FE/s)
    private int mode = 0;
    private int activeTemperature = 0;
    private int tickCounter = 0;
    private long energyStored = 0L;

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

        // Sync on change or periodically for HUD
        if (changed || be.tickCounter == 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 2);
        }
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Mode", mode);
        tag.putLong("Energy", energyStored);
        tag.putInt("ActiveTemperature", activeTemperature);
        tag.putInt("TickCounter", tickCounter);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        mode = tag.getInt("Mode");
        energyStored = tag.getLong("Energy");
        activeTemperature = tag.getInt("ActiveTemperature");
        tickCounter = tag.getInt("TickCounter");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        return Direction.NORTH;
    }

    // ===================== Capability getters for ModCapabilities =====================
    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        if (side == null || side != getFacing()) {
            return this;
        }
        return null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        if (side == null || side != getFacing()) {
            return this;
        }
        return null;
    }

    // ===================== IEnergyConnector =====================
    @Override
    public boolean canConnectEnergy(Direction side) {
        return side == null || side != getFacing();
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
