package com.trd.climate.block.entity;

import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.TemperatureGraph;
import com.trd.climate.calculation.TemperatureCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEntity стационарного электронного термометра.
 * <p>
 * Раз в секунду меряет температуру в точке установки ({@link TemperatureCalculator#calculatePointTemp})
 * и запоминает изменение температуры за секунду — из этих изменений строится график
 * на дисплее прибора (см. {@link TemperatureGraph}).
 * <p>
 * Значение и история изменений сникают на клиент через BlockEntity update-tag:
 * сервер шлёт обновление раз в секунду (история графика меняется каждый тик замера).
 */
public class ElectricThermometerBlockEntity extends BlockEntity {

    /** Показание по умолчанию, °C. */
    public static final float DEFAULT_TEMP = 20.0f;

    /** Порог, при котором показание считается изменившимся, °C. */
    private static final float SYNC_EPSILON = 0.05f;

    /** Текущее показание, °C. */
    private float displayedTemp = DEFAULT_TEMP;
    /** Температура на прошлом замере — от неё считается изменение, °C. */
    private float lastSampleTemp = DEFAULT_TEMP;
    /** Был ли уже хотя бы один замер (иначе первый же дал бы ложный скачок). */
    private boolean hasSample = false;
    /** История изменений температуры за секунду, °C/сек (от старого к новому). */
    private final List<Float> changes = new ArrayList<>();
    /** Счётчик тиков до следующего замера. */
    private int tickCounter = 0;

    public ElectricThermometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_THERMOMETER_BE.get(), pos, state);
    }

    /** Текущее показание прибора, °C. */
    public float getDisplayedTemp() {
        return displayedTemp;
    }

    /** Строки графика прибора (0 — верх, -1 — столбец без данных). */
    public int[] getGraphRows() {
        return TemperatureGraph.rowsFromChanges(changes);
    }

    // ═══════════════════════════════════════════════════════
    // СЕРВЕРНАЯ ЛОГИКА (замер раз в секунду)
    // ═══════════════════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricThermometerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.tickCounter++;
        if (be.tickCounter % 20 != 0) {
            return;
        }

        float measured = TemperatureCalculator.calculatePointTemp(serverLevel, pos);

        if (!be.hasSample) {
            // Первый замер: истории ещё нет, иначе получился бы ложный скачок.
            be.hasSample = true;
            be.lastSampleTemp = measured;
            be.displayedTemp = measured;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            return;
        }

        float change = measured - be.lastSampleTemp;
        be.lastSampleTemp = measured;

        if (Math.abs(measured - be.displayedTemp) > SYNC_EPSILON) {
            be.displayedTemp = measured;
        }

        // История изменений для графика (старые значения уходят влево)
        be.changes.add(change);
        while (be.changes.size() > TemperatureGraph.COLUMNS) {
            be.changes.remove(0);
        }

        be.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    // ═══════════════════════════════════════════════════════
    // СИНХРОНИЗАЦИЯ
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
        tag.putFloat("lastSampleTemp", this.lastSampleTemp);
        tag.putBoolean("hasSample", this.hasSample);
        ListTag list = new ListTag();
        for (float change : this.changes) {
            list.add(FloatTag.valueOf(change));
        }
        tag.put("changes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("displayedTemp")) {
            this.displayedTemp = tag.getFloat("displayedTemp");
        }
        if (tag.contains("lastSampleTemp")) {
            this.lastSampleTemp = tag.getFloat("lastSampleTemp");
        }
        this.hasSample = tag.getBoolean("hasSample");
        this.changes.clear();
        if (tag.contains("changes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("changes", Tag.TAG_FLOAT);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof FloatTag value) {
                    this.changes.add(value.getAsFloat());
                }
            }
        }
    }
}
