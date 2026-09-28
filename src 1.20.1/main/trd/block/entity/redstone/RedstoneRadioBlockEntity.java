package com.trd.block.entity.redstone;

import com.trd.network.ModPacketHandler;
import com.trd.network.packet.redstone.RedstoneRadioSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

public abstract class RedstoneRadioBlockEntity extends BlockEntity implements MenuProvider {
    protected String channelId = "";
    protected boolean powered = false;
    protected int lastSignalStrength = 0;

    public RedstoneRadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // При загрузке на сервере отправляем синхронизацию всем клиентам
        if (level != null && !level.isClientSide) {
            sendSyncPacket();
        }
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId != null ? channelId : "";
        setChanged();
        if (level != null && !level.isClientSide) {
            sendSyncPacket();
        }
    }

    public void sendSyncPacket() {
        if (level != null && !level.isClientSide) {
            ModPacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                    new RedstoneRadioSyncPacket(worldPosition, channelId, powered, lastSignalStrength)
            );
        }
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
            if (level != null && !level.isClientSide) {
                syncToClient();
            }
        }
    }

    public int getLastSignalStrength() {
        return lastSignalStrength;
    }

    public void setLastSignalStrength(int strength) {
        if (this.lastSignalStrength != strength) {
            this.lastSignalStrength = strength;
            setChanged();
            if (level != null && !level.isClientSide) {
                syncToClient();
            }
        }
    }

    public void syncFromPacket(String channelId, boolean powered, int signalStrength) {
        if (channelId != null) {  // убрал !isEmpty(), иначе нельзя стереть канал
            this.channelId = channelId;
        }
        this.powered = powered;
        this.lastSignalStrength = signalStrength;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return createMenu(windowId, playerInventory);
    }

    protected abstract AbstractContainerMenu createMenu(int windowId, Inventory playerInventory);

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("ChannelId", channelId);
        tag.putBoolean("Powered", powered);
        tag.putInt("LastSignalStrength", lastSignalStrength);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.channelId = tag.getString("ChannelId");
        this.powered = tag.getBoolean("Powered");
        this.lastSignalStrength = tag.getInt("LastSignalStrength");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioBlockEntity be) {
        // переопределяется в наследниках
    }

    // ── Стандартная синхронизация с клиентом ──

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);  // пишем весь наш NBT в пакет загрузки чанка
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        load(tag);  // клиент читает NBT при получении чанка
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        if (pkt.getTag() != null) {
            load(pkt.getTag());  // клиент читает NBT при sendBlockUpdated
        }
    }
}