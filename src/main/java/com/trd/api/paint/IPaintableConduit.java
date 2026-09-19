package com.trd.api.paint;

import net.minecraft.world.level.block.state.BlockState;

public interface IPaintableConduit {
    BlockState getMimicState();

    default net.minecraft.resources.ResourceLocation getCoreTexture() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "block/conduit_core");
    }
}
