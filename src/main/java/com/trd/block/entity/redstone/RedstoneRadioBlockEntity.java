package com.trd.block.entity.redstone;

import com.trd.network.packet.redstone.RedstoneRadioSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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

    // 1.21.1: TRACKING_CHUNK-дистрибьютор Forge заменён на прямой вызов по чанку
    public void sendSyncPacket() {
        if (level instanceof ServerLevel serverLevel) {
            RedstoneRadioSyncPacket.sendToTracking(serverLevel, worldPosition, channelId, powered, lastSignalStrength);
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

    // 1.21.1: MenuProvider -> MenuConstructor, createMenu принимает (id, инвентарь, игрок).
    // Игрок нужен только интерфейсу, поэтому наши наследники его не получают.
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

    // 1.21.1: сохранение/загрузка идут через HolderLookup.Provider, а собственные
    // getUpdateTag/handleUpdateTag/getUpdatePacket/onDataPacket больше не нужны —
    // базовый BlockEntity сам пишет их в пакет синхронизации BE.
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ChannelId", channelId);
        tag.putBoolean("Powered", powered);
        tag.putInt("LastSignalStrength", lastSignalStrength);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.channelId = tag.getString("ChannelId");
        this.powered = tag.getBoolean("Powered");
        this.lastSignalStrength = tag.getInt("LastSignalStrength");
    }

    /**
     * 1.21.1: критично для клиента.
     * <p>
     * {@code ClientboundLevelChunkPacketData#create} собирает NBT блока в чанк-пакете
     * именно через {@code getUpdateTag}, и если тот пустой — не отправляет NBT вообще
     * ({@code compoundtag.isEmpty() ? null : compoundtag}), а тип блока приходит отдельным
     * полем. Базовая реализация {@code BlockEntity#getUpdateTag} в 1.21.1 возвращает
     * пустой {@link CompoundTag}, так что клиент после загрузки чанка поднимает BE с
     * нулевым каналом и powered=false — отсюда пропадающие айди и ВКЛ/ВЫКЛ.
     * Полный NBT пишется на диск и в пакет изменения BE, но не в чанк-пакет.
     * Поэтому здесь пишем своё состояние через {@code saveAdditional} вручную.
     * <p>
     * В 1.20.1 базовый {@code getUpdateTag} возвращал полный NBT, поэтому такой
     * переопределяющий метод там был по сути лишним — но и не мешал.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioBlockEntity be) {
        // переопределяется в наследниках
    }
}
