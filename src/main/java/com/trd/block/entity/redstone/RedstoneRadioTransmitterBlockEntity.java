package com.trd.block.entity.redstone;

import com.trd.block.basic.redstone.RedstoneRadioBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.RedstoneRadioMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class RedstoneRadioTransmitterBlockEntity extends RedstoneRadioBlockEntity {
    private static final int SCAN_INTERVAL = 20;
    private int scanCooldown = 0;
    private final Set<BlockPos> knownReceivers = new HashSet<>();
    private boolean initialized = false;

    public RedstoneRadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_RADIO_TRANSMITTER_BE.get(), pos, state);
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory playerInventory) {
        return new RedstoneRadioMenu(windowId, playerInventory, this);
    }

    // Отправка сигнала всем известным приёмникам
    private void sendSignalToReceivers(int signal) {
        if (level == null || level.isClientSide) return;
        for (BlockPos receiverPos : knownReceivers) {
            BlockEntity entity = level.getBlockEntity(receiverPos);
            if (entity instanceof RedstoneRadioReceiverBlockEntity receiver) {
                receiver.receiveSignal(signal, worldPosition);
            }
        }
    }

    public void notifyReceiversOfRemoval() {
        sendSignalToReceivers(0);
        knownReceivers.clear();
    }

    @Override
    public void setChannelId(String channelId) {
        if (!this.channelId.equals(channelId) && !this.channelId.isEmpty()) {
            sendSignalToReceivers(0);
            knownReceivers.clear();
        }
        super.setChannelId(channelId);
    }

    @Override
    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
            if (level != null && !level.isClientSide) {
                BlockState state = level.getBlockState(worldPosition);
                if (state.getValue(RedstoneRadioBlock.POWERED) != powered) {
                    level.setBlock(worldPosition, state.setValue(RedstoneRadioBlock.POWERED, powered), 3);
                }
            }
        }
    }

    @Override
    public void setLastSignalStrength(int strength) {
        if (this.lastSignalStrength != strength) {
            this.lastSignalStrength = strength;
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioTransmitterBlockEntity be) {
        if (level.isClientSide) return;

        if (!be.initialized) {
            be.initialized = true;
            be.discoverReceivers(level);
            be.sendSignalToReceivers(be.lastSignalStrength);
            return;
        }

        int inputSignal = level.getBestNeighborSignal(pos);
        boolean wasPowered = be.powered;
        boolean nowPowered = inputSignal > 0;
        int prevStrength = be.lastSignalStrength;
        boolean signalChanged = false;

        if (nowPowered != wasPowered) {
            be.setPowered(nowPowered);
            signalChanged = true;
        }
        if (inputSignal != prevStrength) {
            be.setLastSignalStrength(inputSignal);
            signalChanged = true;
        }

        be.scanCooldown--;
        if (be.scanCooldown <= 0) {
            be.scanCooldown = SCAN_INTERVAL;
            be.discoverReceivers(level);
            if (be.powered) {
                be.sendSignalToReceivers(be.lastSignalStrength);
            } else {
                be.sendSignalToReceivers(0);
            }
        }

        if (signalChanged) {
            be.sendSignalToReceivers(be.lastSignalStrength);
        }
    }

    private void discoverReceivers(Level level) {
        knownReceivers.clear();
        if (channelId.isEmpty()) return;

        int range = 64;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos checkPos = worldPosition.offset(dx, dy, dz);
                    if (checkPos.distSqr(worldPosition) > range * range) continue;
                    BlockEntity be = level.getBlockEntity(checkPos);
                    if (be instanceof RedstoneRadioReceiverBlockEntity receiver) {
                        if (channelId.equals(receiver.getChannelId())) {
                            knownReceivers.add(be.getBlockPos());
                        }
                    }
                }
            }
        }
    }

    public void forceRescan(Level level) {
        discoverReceivers(level);
        sendSignalToReceivers(lastSignalStrength);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Initialized", initialized);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.initialized = tag.getBoolean("Initialized");
    }
}