package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.util.StringRepresentable;

/**
 * Единое состояние конечного автомата нефтевышки.
 */
public enum RigAssemblyState implements StringRepresentable {
    IDLE("idle"),
    DRILLING("drilling"),
    FEEDING_PIPE("feeding_pipe"),
    EXTRACTING("extracting"),
    PAUSED_NO_PIPES("paused_no_pipes"),
    PAUSED_NO_POWER("paused_no_power");

    private final String serializedName;

    RigAssemblyState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
