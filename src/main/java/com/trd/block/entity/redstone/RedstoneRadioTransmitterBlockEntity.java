package com.trd.block.entity.redstone;

import com.trd.block.entity.ModBlockEntities;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.redstone.RedstoneRadioSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public class RedstoneRadioTransmitterBlockEntity extends RedstoneRadioBlockEntity {
    private static final int SCAN_INTERVAL = 20;
    private int scanCooldown = 0;
    private final Set<BlockPos> knownReceivers = new HashSet<>();

    public RedstoneRadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_RADIO_TRANSMITTER_BE.get(), pos, state);
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory playerInventory) {
        return new RedstoneRadioMenu(windowId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioTransmitterBlockEntity be) {
        if (level.isClientSide) return;

        int inputSignal = level.getBestNeighborSignal(pos);
        boolean wasPowered = be.powered;
        boolean nowPowered = inputSignal > 0;
        int prevStrength = be.lastSignalStrength;

        if (nowPowered != wasPowered || inputSignal != prevStrength) {
            be.setPowered(nowPowered);
            be.setLastSignalStrength(inputSignal);
        }

        be.scanCooldown--;
        if (be.scanCooldown <= 0) {
            be.scanCooldown = SCAN_INTERVAL;
            be.discoverReceivers(level);
        }

        if (be.powered && !be.knownReceivers.isEmpty()) {
            be.transmitSignal(level);
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

    private void transmitSignal(Level level) {
        for (BlockPos receiverPos : knownReceivers) {
            BlockEntity entity = level.getBlockEntity(receiverPos);
            if (entity instanceof RedstoneRadioReceiverBlockEntity receiver) {
                receiver.receiveSignal(lastSignalStrength);
                ModPacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(receiverPos)),
                    new RedstoneRadioSyncPacket(receiverPos, receiver.getChannelId(), receiver.isPowered(), receiver.getLastSignalStrength()));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }
}