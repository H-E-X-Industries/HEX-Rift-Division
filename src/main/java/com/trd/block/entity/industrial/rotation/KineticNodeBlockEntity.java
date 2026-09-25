package com.trd.block.entity.industrial.rotation;

import com.trd.api.rotation.KineticNetwork;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.Rotational;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Абстрактный базовый класс для всех кинетических BlockEntity.
 *
 * Содержит общую логику:
 * - Хранение скорости (speed, lastSyncedSpeed, networkScale)
 * - Интеллектуальная синхронизация клиента (shouldSyncSpeed)
 * - Восстановление состояния сети при загрузке (onLoad)
 * - NBT-сериализация базовых кинетических полей
 * - Отправка обновления клиенту (getUpdateTag / getUpdatePacket / onDataPacket)
 *
 * Наследники: ShaftBlockEntity, BearingBlockEntity, HandCrankBlockEntity...
 */
public abstract class KineticNodeBlockEntity extends BlockEntity implements Rotational {

    // ===================== ОБЩИЕ КИНЕТИЧЕСКИЕ ПОЛЯ =====================

    /** Локальная скорость этого блока (с учётом networkScale) */
    protected long speed = 0;

    /** Последняя отправленная клиенту скорость — для дедупликации пакетов */
    protected long lastSyncedSpeed = 0;

    /**
     * Коэффициент передачи относительно первичного вала сети.
     * Знак определяет направление вращения (±).
     * Модуль — передаточное число (например 0.5 = замедление в 2 раза).
     */
    protected float networkScale = 1.0f;

    // ===================== КОНСТРУКТОР =====================

    protected KineticNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ===================== Rotational: СКОРОСТЬ =====================

    @Override
    public long getSpeed() {
        return speed;
    }

    /**
     * Вызывается сетью каждый тик.
     * Применяет networkScale и отправляет клиентский пакет только при
     * достаточном изменении скорости (shouldSyncSpeed), чтобы не спамить сеть.
     */
    @Override
    public void setSpeed(long networkSpeed) {
        long actualSpeed = (long) (networkSpeed * this.networkScale);
        if (this.speed != actualSpeed) {
            this.speed = actualSpeed;
            setChanged();
            if (shouldSyncSpeed()) {
                this.lastSyncedSpeed = this.speed;
                syncToClient();
            }
        }
    }

    /**
     * Проверяет, нужно ли отправить пакет клиенту.
     * Пакет отправляется:
     * - при переходе 0 → ненулевое или ненулевое → 0
     * - при изменении более чем на 5% текущей скорости (но минимум на 2 ед.)
     */
    protected boolean shouldSyncSpeed() {
        if (this.speed == 0 && this.lastSyncedSpeed != 0) return true;
        if (this.speed != 0 && this.lastSyncedSpeed == 0) return true;
        long diff = Math.abs(this.speed - this.lastSyncedSpeed);
        long threshold = Math.max(2, Math.abs(this.lastSyncedSpeed) / 20);
        return diff >= threshold;
    }

    /** Рассылает обновление блока клиентам чанка. */
    protected void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ===================== Rotational: МАСШТАБ СЕТИ =====================

    @Override
    public void setNetworkScale(float scale) {
        this.networkScale = scale;
    }

    @Override
    public float getNetworkScale() {
        return this.networkScale;
    }

    // ===================== ЖИЗНЕННЫЙ ЦИКЛ =====================

    /**
     * При загрузке чанка восстанавливаем скорость из текущего состояния сети.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
            KineticNetwork net = manager.getNetworkFor(worldPosition);
            if (net == null && manager.isReady()) {
                manager.updateNetworkAfterPlace(worldPosition);
                net = manager.getNetworkFor(worldPosition);
            }
            if (net != null) {
                this.speed = (long) (net.getSpeed() * this.networkScale);
                this.lastSyncedSpeed = this.speed;
            }
        }
    }

    // ===================== NBT =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Speed", this.speed);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
        tag.putFloat("NetworkScale", this.networkScale);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.speed = tag.getLong("Speed");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
        this.networkScale = tag.contains("NetworkScale") ? tag.getFloat("NetworkScale") : 1.0f;
    }

    // ===================== СИНХРОНИЗАЦИЯ КЛИЕНТ ↔ СЕРВЕР =====================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }
}
