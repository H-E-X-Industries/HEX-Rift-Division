package com.trd.block.entity.industrial.energy;

import com.trd.api.energy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Абстрактный базовый класс для всех энергетических BlockEntity.
 * 
 * Содержит общую логику:
 * - Хранение энергии (energy, capacity)
 * - Режимы работы (mode) и приоритеты (priority)
 * - Регистрация в EnergyNetworkManager
 * - Совместимость с Forge Energy через PackedEnergyCapabilityProvider
 * - Синхронизация данных с клиентом
 */
public abstract class EnergyNodeBlockEntity extends BlockEntity implements IEnergyProvider, IEnergyReceiver {

    protected long energy = 0;
    protected long capacity = 0;
    protected int mode = 0; // 0=BOTH, 1=INPUT, 2=OUTPUT, 3=DISABLED
    protected Priority priority = Priority.NORMAL;

    protected final PackedEnergyCapabilityProvider feCapabilityProvider;

    protected EnergyNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.feCapabilityProvider = new PackedEnergyCapabilityProvider(this);
    }

    // ===================== IEnergyProvider & IEnergyReceiver =====================

    @Override
    public long getEnergyStored() {
        return this.energy;
    }

    @Override
    public long getMaxEnergyStored() {
        return this.capacity;
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energy = Math.max(0, Math.min(this.capacity, energy));
        setChanged();
    }

    @Override
    public Priority getPriority() {
        return this.priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
        setChanged();
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return true; // По умолчанию соединяется со всех сторон, можно переопределить
    }

    @Override
    public abstract long getProvideSpeed();

    @Override
    public abstract long getReceiveSpeed();

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        if (!canExtract()) return 0;
        long energyExtracted = Math.min(this.energy, Math.min(getProvideSpeed(), maxExtract));
        if (!simulate && energyExtracted > 0) {
            setEnergyStored(this.energy - energyExtracted);
        }
        return energyExtracted;
    }

    @Override
    public boolean canExtract() {
        return (mode == 0 || mode == 2) && this.energy > 0 && getProvideSpeed() > 0;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) return 0;
        long energyReceived = Math.min(this.capacity - this.energy, Math.min(getReceiveSpeed(), maxReceive));
        if (!simulate && energyReceived > 0) {
            setEnergyStored(this.energy + energyReceived);
        }
        return energyReceived;
    }

    @Override
    public boolean canReceive() {
        return (mode == 0 || mode == 1) && this.energy < this.capacity && getReceiveSpeed() > 0;
    }

    // ===================== ЖИЗНЕННЫЙ ЦИКЛ =====================

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

    // ===================== CAPABILITIES =====================

    @Nullable
    public IEnergyProvider getEnergyProvider(@Nullable Direction side) {
        if ((mode == 0 || mode == 2) && (side == null || canConnectEnergy(side))) {
            return this;
        }
        return null;
    }

    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        if ((mode == 0 || mode == 1) && (side == null || canConnectEnergy(side))) {
            return this;
        }
        return null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        if (side == null || canConnectEnergy(side)) {
            return this;
        }
        return null;
    }

    public PackedEnergyCapabilityProvider getFeCapabilityProvider() {
        return feCapabilityProvider;
    }

    // ===================== NBT & SYNC =====================

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Energy", this.energy);
        tag.putLong("Capacity", this.capacity);
        tag.putInt("EnergyMode", this.mode);
        tag.putInt("Priority", this.priority.ordinal());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.energy = tag.getLong("Energy");
        this.capacity = tag.getLong("Capacity");
        this.mode = tag.getInt("EnergyMode");
        if (tag.contains("Priority")) {
            this.priority = Priority.values()[Math.min(tag.getInt("Priority"), Priority.values().length - 1)];
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, this.level == null ? null : this.level.registryAccess());
        }
    }

    protected void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
