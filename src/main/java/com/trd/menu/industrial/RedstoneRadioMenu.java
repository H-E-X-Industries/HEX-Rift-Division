package com.trd.menu.industrial;

import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.menu.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class RedstoneRadioMenu extends AbstractContainerMenu {
    public static final int SYNC_CHANNEL_ID = 0;
    public static final int SYNC_POWERED = 1;
    public static final int SYNC_SIGNAL_STRENGTH = 2;

    private final RedstoneRadioBlockEntity radioEntity;
    private final BlockPos pos;
    private final ContainerData data;

    public RedstoneRadioMenu(int windowId, Inventory playerInventory, RedstoneRadioBlockEntity radioEntity) {
        this(windowId, playerInventory, radioEntity, new SimpleContainerData(3), radioEntity.getBlockPos());
    }

    // 1.21.1: в клиентский конструктор позиция приходит из буфера, который
    // IPlayerExtension#openMenu(MenuProvider, BlockPos) пишет при открытии меню
    public RedstoneRadioMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, buf.readBlockPos());
    }

    private RedstoneRadioMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        this(windowId, playerInventory,
                playerInventory.player.level().getBlockEntity(pos) instanceof RedstoneRadioBlockEntity radio ? radio : null,
                new SimpleContainerData(3),
                pos);
    }

    private RedstoneRadioMenu(int windowId, Inventory playerInventory, RedstoneRadioBlockEntity radioEntity,
                              ContainerData data, BlockPos pos) {
        super(ModMenuTypes.REDSTONE_RADIO_MENU.get(), windowId);
        this.radioEntity = radioEntity;
        this.pos = pos;
        this.data = data;

        addDataSlots(data);
    }

    public RedstoneRadioBlockEntity getRadioEntity() {
        return radioEntity;
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public String getChannelId() {
        return radioEntity != null ? radioEntity.getChannelId() : "";
    }

    public void setChannelId(String channelId) {
        if (radioEntity != null) {
            radioEntity.setChannelId(channelId);
        }
        data.set(SYNC_CHANNEL_ID, channelId.hashCode());
    }

    public boolean isPowered() {
        return radioEntity != null && radioEntity.isPowered();
    }

    public int getSignalStrength() {
        return radioEntity != null ? radioEntity.getLastSignalStrength() : 0;
    }

    @Override
    public boolean stillValid(Player player) {
        if (radioEntity == null || radioEntity.isRemoved()) return false;
        return radioEntity.getLevel() == player.level() &&
                player.distanceToSqr(pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5) < 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
