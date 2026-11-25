package com.trd.api.nature.climate.system.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Клиентская «тень» герметичной комнаты.
 * <p>
 * Серверные комнаты — это блок-сущности контроллеров, живущие только на сервере.
 * Чтобы клиентский расчёт температуры (термометры, дебаг-оверлей) внутри помещения
 * показывал те же числа, что и сервер, сервер раз в секунду присылает слепок
 * комнаты этим POJO: границы, объём, температура и флаг герметичности.
 */
public final class SyncedRoom implements SealedRoom {

    private final BlockPos controllerPos;
    private final AABB interiorBox;
    private final int volume;
    private final float roomTemperature;
    private final boolean sealed;

    public SyncedRoom(BlockPos controllerPos, AABB interiorBox, int volume,
                      float roomTemperature, boolean sealed) {
        this.controllerPos = controllerPos;
        this.interiorBox = interiorBox;
        this.volume = volume;
        this.roomTemperature = roomTemperature;
        this.sealed = sealed;
    }

    @Override
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Override
    public AABB getInteriorBox() {
        return interiorBox;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public float getRoomTemperature() {
        return roomTemperature;
    }

    @Override
    public boolean isSealed() {
        return sealed;
    }

    @Override
    public boolean isStructureValid() {
        return interiorBox != null && volume > 0;
    }

    @Override
    public boolean hasLeak() {
        return !sealed;
    }
}
