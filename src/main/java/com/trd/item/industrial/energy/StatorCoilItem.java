package com.trd.item.industrial.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class StatorCoilItem extends Item {
    private final String materialName;
    private final long energyConversionRate;
    private final long energyBuffer;
    private final long baseTorqueLoad;
    private final float asymmetryMultiplier;

    public StatorCoilItem(Properties properties, String materialName, long energyConversionRate, long energyBuffer, long baseTorqueLoad, float asymmetryMultiplier) {
        super(properties);
        this.materialName = materialName;
        this.energyConversionRate = energyConversionRate;
        this.energyBuffer = energyBuffer;
        this.baseTorqueLoad = baseTorqueLoad;
        this.asymmetryMultiplier = asymmetryMultiplier;
    }

    public String getMaterialName() {
        return materialName;
    }

    public long getEnergyConversionRate() {
        return energyConversionRate;
    }

    public long getEnergyBuffer() {
        return energyBuffer;
    }

    public long getBaseTorqueLoad() {
        return baseTorqueLoad;
    }

    public float getAsymmetryMultiplier() {
        return asymmetryMultiplier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.trd.machine.stator_coil.desc").withStyle(ChatFormatting.GRAY));
    }
}
