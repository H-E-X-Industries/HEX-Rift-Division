package com.trd.block.basic.industrial.lighting;

import net.minecraft.util.StringRepresentable;

/**
 * Vertical pitch alignment modes for floodlight fixtures.
 */
public enum FloodlightPitch implements StringRepresentable {
    STRAIGHT("straight"),
    UP("up"),
    DOWN("down");

    private final String name;

    FloodlightPitch(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
