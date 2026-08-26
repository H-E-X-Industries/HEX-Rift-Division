package com.trd.block.entity.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class RedstoneRadioMenu extends AbstractContainerMenu {
    public static final int SYNC_CHANNEL_ID = 0;
    public static final int SYNC_POWERED = 1;
    public static final int SYNC_SIGNAL_STRENGTH = 2;

    private final RedstoneRadioBlockEntity radioEntity;
    private final Level level;
    private final BlockPos pos;
    private final ContainerData data;
    private final IItemHandler playerInventory;

    public RedstoneRadioMenu(int windowId, Inventory playerInventory, RedstoneRadioBlockEntity radioEntity) {
        this(windowId, playerInventory, radioEntity, new SimpleContainerData(3), playerInventory.player.level(), radioEntity.getBlockPos());
    }

    public static RedstoneRadioMenu create(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = playerInventory.player.level().getBlockEntity(pos);
        if (entity instanceof RedstoneRadioBlockEntity radio) {
            return new RedstoneRadioMenu(windowId, playerInventory, radio);
        }
        return new RedstoneRadioMenu(windowId, playerInventory, pos);
    }

    private RedstoneRadioMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, buf.readBlockPos());
    }

    private RedstoneRadioMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        this(windowId, playerInventory, 
            (RedstoneRadioBlockEntity) playerInventory.player.level().getBlockEntity(pos),
            new SimpleContainerData(3),
            playerInventory.player.level(),
            pos);
    }

    private RedstoneRadioMenu(int windowId, Inventory playerInventory, RedstoneRadioBlockEntity radioEntity,
                              ContainerData data, Level level, BlockPos pos) {
        super(com.trd.menu.ModMenuTypes.REDSTONE_RADIO_MENU.get(), windowId);
        this.radioEntity = radioEntity;
        this.level = level;
        this.pos = pos;
        this.data = data;
        this.playerInventory = new ItemStackHandler(0);

        addDataSlots(data);
    }

    public RedstoneRadioBlockEntity getRadioEntity() {
        return radioEntity;
    }

    public String getChannelId() {
        return radioEntity.getChannelId();
    }

    public void setChannelId(String channelId) {
        radioEntity.setChannelId(channelId);
        data.set(SYNC_CHANNEL_ID, channelId.hashCode());
    }

    public boolean isPowered() {
        return radioEntity.isPowered();
    }

    public int getSignalStrength() {
        return radioEntity.getLastSignalStrength();
    }

    @Override
    public boolean stillValid(Player player) {
        if (radioEntity == null || radioEntity.isRemoved()) return false;
        return radioEntity.getLevel() == player.level() && 
               player.distanceToSqr(radioEntity.getBlockPos().getX() + 0.5, 
                                   radioEntity.getBlockPos().getY() + 0.5, 
                                   radioEntity.getBlockPos().getZ() + 0.5) < 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}