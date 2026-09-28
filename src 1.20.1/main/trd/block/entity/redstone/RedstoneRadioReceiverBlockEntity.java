package com.trd.block.entity.redstone;

import com.trd.api.redstone.RadioNetworkManager;
import com.trd.block.basic.redstone.RedstoneRadioBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.RedstoneRadioMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class RedstoneRadioReceiverBlockEntity extends RedstoneRadioBlockEntity {
    // Карта сигналов от передатчиков (только на сервере)
    private final Map<BlockPos, Integer> transmitterSignals = new HashMap<>();
    private int outputSignal = 0;

    public RedstoneRadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_RADIO_RECEIVER_BE.get(), pos, state);
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, Inventory playerInventory) {
        return new RedstoneRadioMenu(windowId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneRadioReceiverBlockEntity be) {
        // ничего не делаем
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
                sendSyncPacket();
            }
        }
    }

    @Override
    public void setLastSignalStrength(int strength) {
        if (this.lastSignalStrength != strength) {
            this.lastSignalStrength = strength;
            setChanged();
            if (level != null && !level.isClientSide) {
                sendSyncPacket();
            }
        }
    }

    @Override
    public void syncFromPacket(String channelId, boolean powered, int signalStrength) {
        super.syncFromPacket(channelId, powered, signalStrength);
        this.outputSignal = signalStrength;
    }

    /**
     * Приём сигнала от передатчика (вызывается на сервере)
     * @param signalStrength сила сигнала
     * @param sourcePos позиция передатчика
     */
    public void receiveSignal(int signalStrength, BlockPos sourcePos) {
        if (level == null || level.isClientSide) return;

        // Обновляем запись для этого передатчика
        transmitterSignals.put(sourcePos, signalStrength);

        // Вычисляем максимум
        int maxSignal = 0;
        for (int s : transmitterSignals.values()) {
            if (s > maxSignal) maxSignal = s;
        }

        // Если максимум изменился, обновляем выход
        if (this.outputSignal != maxSignal) {
            this.outputSignal = maxSignal;
            this.setPowered(maxSignal > 0);
            this.setLastSignalStrength(maxSignal);
            setChanged();
            updateNeighbors();
        }
    }

    private void updateNeighbors() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state == null) return;
        // Стандартный способ уведомить соседей, что наш блок изменил выходной сигнал.
        // НЕ вызываем level.neighborChanged вручную — это ломало соседние блоки (поршни),
        // потому что передавалось наше BlockState в чужую позицию.
        level.updateNeighborsAt(worldPosition, state.getBlock());
    }

    public int getOutputSignal() {
        return outputSignal;
    }
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            RadioNetworkManager.get(level).registerReceiver(worldPosition, channelId);
            if (outputSignal > 0) {
                updateNeighbors();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            RadioNetworkManager.get(level).unregisterReceiver(worldPosition);
        }
    }

    @Override
    public void setChannelId(String channelId) {
        String old = this.channelId;
        this.channelId = channelId != null ? channelId : "";
        setChanged();
        if (level != null && !level.isClientSide) {
            RadioNetworkManager.get(level).registerReceiver(worldPosition, this.channelId);
            if (!old.equals(this.channelId)) {
                // старый канал почистится автоматически внутри registerReceiver
                // (перезапись channelByPos + удаление из старого Set)
            }
            sendSyncPacket();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("OutputSignal", outputSignal);
        // карту не сохраняем — она будет восстановлена при получении сигналов
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.outputSignal = tag.getInt("OutputSignal");
    }
}