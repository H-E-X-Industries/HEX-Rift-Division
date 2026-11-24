package com.trd.climate.block.entity;

import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.calculation.TemperatureCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity стационарного ртутного термометра.
 * <p>
 * Раз в секунду сервер измеряет температуру в точке установки блока —
 * {@link TemperatureCalculator#calculatePointTemp}: внутри герметичного помещения
 * это комнатная температура (с учётом сквозняка при утечке и локального лучистого
 * жара у источников), снаружи — «уличная» с эффектом погоды, укрытия и ближайших
 * источников тепла. Значение сникается клиенту через BlockEntity update-tag
 * только при фактическом изменении (больше 0.05°C), чтобы не спамить пакетами.
 */
public class MercuryThermometerBlockEntity extends BlockEntity {

    /** Начальное показание до первого измерения — «комнатно-комфортные» +20°C. */
    public static final float DEFAULT_TEMP = 20.0f;

    /** Минимальная разница с прошлым показанием, ради которой стоит шлать update-tag. */
    private static final float SYNC_EPSILON = 0.05f;

    private float displayedTemp = DEFAULT_TEMP;
    private int tickCounter = 0;

    public MercuryThermometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMOMETER_MERC_BE.get(), pos, state);
    }

    public float getDisplayedTemp() {
        return this.displayedTemp;
    }

    // ═══════════════════════════════════════════════════════
    // СЕРВЕРНЫЙ ТИК (1 раз в секунду)
    // ═══════════════════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state, MercuryThermometerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.tickCounter++;
        if (be.tickCounter % 20 != 0) {
            return;
        }

        float measured = TemperatureCalculator.calculatePointTemp(serverLevel, pos);
        if (Math.abs(measured - be.displayedTemp) > SYNC_EPSILON) {
            be.displayedTemp = measured;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        } else {
            // Держим кеш свежим без отправки пакета
            be.displayedTemp = measured;
        }
    }

    // ═══════════════════════════════════════════════════════
    // СИНХРОНИЗАЦИЯ С КЛИЕНТОМ
    // ═══════════════════════════════════════════════════════

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // ═══════════════════════════════════════════════════════
    // NBT
    // ═══════════════════════════════════════════════════════

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("displayedTemp", this.displayedTemp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("displayedTemp")) {
            this.displayedTemp = tag.getFloat("displayedTemp");
        }
    }
}
