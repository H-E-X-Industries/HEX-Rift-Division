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
import net.minecraftforge.network.PacketDistributor;

public class RedstoneRadioReceiverBlockEntity extends RedstoneRadioBlockEntity {
    private int outputSignal = 0;

    public RedstoneRadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_RADIO_RECEIVER_BE.get(), pos, state);
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory playerInventory) {
        return new RedstoneRadioMenu(windowId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioReceiverBlockEntity be) {
        if (level.isClientSide) return;
    }

    public void receiveSignal(int signalStrength) {
        if (this.outputSignal != signalStrength) {
            this.outputSignal = signalStrength;
            this.setPowered(signalStrength > 0);
            this.setLastSignalStrength(signalStrength);
            setChanged();
            syncToClient();

            Level level = getLevel();
            if (level != null && !level.isClientSide) {
                ModPacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                    new RedstoneRadioSyncPacket(worldPosition, channelId, powered, outputSignal));
            }
            
            updateNeighbors();
        }
    }

    private void updateNeighbors() {
        Level level = getLevel();
        if (level == null) return;
        
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            level.updateNeighborsAt(neighborPos, getBlockState().getBlock());
            level.neighborChanged(getBlockState(), neighborPos, getBlockState().getBlock(), worldPosition, false);
        }
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    public int getOutputSignal() {
        return outputSignal;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("OutputSignal", outputSignal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.outputSignal = tag.getInt("OutputSignal");
    }
}