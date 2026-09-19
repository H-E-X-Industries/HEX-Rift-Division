package com.trd.item.industrial.energy;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import com.trd.api.energy.IEnergyProvider;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.capability.ModCapabilities;
import com.trd.item.industrial.energy.EnergyCellItem;

public class ModBatteryItem extends Item {
    protected final int capacity;
    protected final int maxReceive;
    protected final int maxExtract;

    public ModBatteryItem(Properties properties, int capacity, int maxReceive, int maxExtract) {
        super(properties);
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    public static void addEnergyTooltip(List<Component> tooltip, long current, long max, ChatFormatting format) {
        tooltip.add(Component.translatable("tooltip.trd.energy", current, max).withStyle(format));
    }

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@Nonnull ItemStack stack) {
        IEnergyProvider provider = stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
        if (provider != null) {
            if (provider.getMaxEnergyStored() <= 0) return 0;
            return (int) Math.round(13.0 * provider.getEnergyStored() / (double) provider.getMaxEnergyStored());
        }
        IEnergyReceiver receiver = stack.getCapability(ModCapabilities.ENERGY_RECEIVER_ITEM);
        if (receiver != null) {
            if (receiver.getMaxEnergyStored() <= 0) return 0;
            return (int) Math.round(13.0 * receiver.getEnergyStored() / (double) receiver.getMaxEnergyStored());
        }
        return 0;
    }

    @Override
    public int getBarColor(@Nonnull ItemStack stack) {
        float ratio = 0.0f;
        IEnergyProvider provider = stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
        if (provider != null && provider.getMaxEnergyStored() > 0) {
            ratio = (float) provider.getEnergyStored() / provider.getMaxEnergyStored();
        } else {
            IEnergyReceiver receiver = stack.getCapability(ModCapabilities.ENERGY_RECEIVER_ITEM);
            if (receiver != null && receiver.getMaxEnergyStored() > 0) {
                ratio = (float) receiver.getEnergyStored() / receiver.getMaxEnergyStored();
            }
        }
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        IEnergyProvider provider = stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
        if (provider != null) {
            addEnergyTooltip(tooltip, provider.getEnergyStored(), provider.getMaxEnergyStored(), ChatFormatting.AQUA);
        } else {
            IEnergyReceiver receiver = stack.getCapability(ModCapabilities.ENERGY_RECEIVER_ITEM);
            if (receiver != null) {
                addEnergyTooltip(tooltip, receiver.getEnergyStored(), receiver.getMaxEnergyStored(), ChatFormatting.AQUA);
            }
        }

        if (maxReceive > 0) {
            tooltip.add(Component.translatable("tooltip.trd.energy_input", maxReceive).withStyle(ChatFormatting.GRAY));
        }
        if (maxExtract > 0) {
            tooltip.add(Component.translatable("tooltip.trd.energy_output", maxExtract).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
