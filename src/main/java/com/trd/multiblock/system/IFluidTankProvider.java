package com.trd.multiblock.system;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public interface IFluidTankProvider {
    IFluidHandler getFluidHandlerCapability();
}