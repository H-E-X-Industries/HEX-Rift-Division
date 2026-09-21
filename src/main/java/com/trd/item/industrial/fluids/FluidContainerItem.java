package com.trd.item.industrial.fluids;

import com.trd.api.fluids.system.FluidInfoHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Optional;

public class FluidContainerItem extends Item {

    public static final String TAG_FLUID = "Fluid";
    public static final String TAG_DISSOLVE = "trd:dissolve";

    private final int capacity;
    private final int maxCorrosion;
    private final int maxTemperature;

    public FluidContainerItem(Properties properties, int capacity, int maxCorrosion, int maxTemperature) {
        super(properties);
        this.capacity = capacity;
        this.maxCorrosion = maxCorrosion;
        this.maxTemperature = maxTemperature;
    }

    public int getCapacity() { return capacity; }
    public int getMaxCorrosion() { return maxCorrosion; }
    public int getMaxTemperature() { return maxTemperature; }

    public static FluidStack getFluid(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_FLUID)) return FluidStack.EMPTY;
        
        CompoundTag fluidTag = tag.getCompound(TAG_FLUID);
        String fluidName = fluidTag.getString("FluidName");
        int amount = fluidTag.getInt("Amount");
        
        ResourceLocation id = ResourceLocation.tryParse(fluidName);
        if (id == null) return FluidStack.EMPTY;
        
        Optional<Fluid> opt = BuiltInRegistries.FLUID.getOptional(id);
        if (opt.isEmpty() || opt.get() == Fluids.EMPTY) return FluidStack.EMPTY;
        
        return new FluidStack(opt.get(), amount);
    }

    public void setFluid(ItemStack stack, FluidStack fluid) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (fluid.isEmpty()) {
            tag.remove(TAG_FLUID);
        } else {
            CompoundTag fluidTag = new CompoundTag();
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            fluidTag.putString("FluidName", id != null ? id.toString() : "minecraft:empty");
            fluidTag.putInt("Amount", capacity);
            tag.put(TAG_FLUID, fluidTag);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack createFilled(Item item, Fluid fluid) {
        if (!(item instanceof FluidContainerItem container) || fluid == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(container);
        FluidStack fill = new FluidStack(fluid, container.capacity);
        if (!container.canWithstand(fill)) return ItemStack.EMPTY;
        container.setFluid(stack, fill);
        return stack;
    }

    public static boolean isFilled(ItemStack stack) {
        return !getFluid(stack).isEmpty();
    }

    public boolean isDissolving(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(TAG_DISSOLVE);
    }

    private boolean canWithstand(FluidStack fluid) {
        return FluidInfoHelper.getCorrosivity(fluid) <= maxCorrosion
                && FluidInfoHelper.getTemperature(fluid) <= maxTemperature;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            int color = FluidInfoHelper.getRgbColor(fluid);
            tooltip.add(Component.literal("  ")
                    .append(FluidInfoHelper.getDisplayName(fluid))
                    .append(Component.literal(": " + fluid.getAmount() + " / " + capacity + " mB"))
                    .withStyle(style -> style.withColor(color)));
            tooltip.addAll(getMaxRatingTooltip());
        } else {
            tooltip.add(Component.translatable("tooltip.trd.fluid_barrel.empty").withStyle(ChatFormatting.GRAY));
            tooltip.addAll(getMaxRatingTooltip());
        }
    }

    public java.util.List<Component> getMaxRatingTooltip() {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(Component.translatable("tooltip.trd.fluid_pipe.max_temp").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(maxTemperature + " °C").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.translatable("tooltip.trd.fluid_pipe.max_corrosion").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(maxCorrosion)).withStyle(ChatFormatting.YELLOW)));
        return tooltip;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!isDissolving(stack)) return;
        removeAndPlaySound(stack, entity, level);
    }

    private void dissolveNow(ItemStack stack) {
        stack.shrink(stack.getCount());
    }

    public void removeAndPlaySound(ItemStack stack, net.minecraft.world.entity.Entity entity, Level level) {
        removeAndPlaySound(stack, entity.blockPosition(), level);
    }

    public static void removeAndPlaySound(ItemStack stack, net.minecraft.core.BlockPos pos, Level level) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.2F);
        stack.shrink(stack.getCount());
    }

    public static class FluidHandler implements IFluidHandlerItem {
        private final ItemStack stack;
        private final FluidStack[] internal;
        private boolean updateNeeded = false;

        public FluidHandler(ItemStack stack, FluidContainerItem item) {
            this.stack = stack;
            this.internal = new FluidStack[]{FluidStack.EMPTY};
            this.internal[0] = item.getFluid(stack);
            if (!internal[0].isEmpty()) {
                internal[0].setAmount(item.getCapacity());
            }
        }

        @Override public ItemStack getContainer() { return stack; }

        @Override public int getTanks() { return 1; }

        @Override public @NotNull FluidStack getFluidInTank(int tank) {
            return updateNeeded ? FluidStack.EMPTY : internal[0];
        }

        @Override public int getTankCapacity(int tank) { 
            if (stack.getItem() instanceof FluidContainerItem item) return item.getCapacity();
            return 0;
        }

        @Override public boolean isFluidValid(int tank, @NotNull FluidStack resource) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !(stack.getItem() instanceof FluidContainerItem item)) return 0;
            if (!internal[0].isEmpty()) return 0;
            if (resource.getAmount() < item.getCapacity()) return 0;

            FluidStack toFill = resource.copy();
            toFill.setAmount(item.getCapacity());

            if (action.execute()) {
                internal[0] = toFill;
                updateNeeded = true;
                item.setFluid(stack, toFill);
                if (!item.canWithstand(toFill)) {
                    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    tag.putBoolean(TAG_DISSOLVE, true);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    item.dissolveNow(stack);
                }
            }
            return item.getCapacity();
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            if (internal[0].isEmpty() || !internal[0].is(resource.getFluid())) return FluidStack.EMPTY;
            if (internal[0].getAmount() < resource.getAmount()) return FluidStack.EMPTY;

            FluidStack drained = internal[0].copy();
            if (action.execute()) {
                internal[0] = FluidStack.EMPTY;
                updateNeeded = true;
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.remove(TAG_FLUID);
                tag.remove(TAG_DISSOLVE);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (internal[0].isEmpty()) return FluidStack.EMPTY;
            if (maxDrain < internal[0].getAmount()) return FluidStack.EMPTY;

            FluidStack drained = internal[0].copy();
            if (action.execute()) {
                internal[0] = FluidStack.EMPTY;
                updateNeeded = true;
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.remove(TAG_FLUID);
                tag.remove(TAG_DISSOLVE);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return drained;
        }
    }
}
