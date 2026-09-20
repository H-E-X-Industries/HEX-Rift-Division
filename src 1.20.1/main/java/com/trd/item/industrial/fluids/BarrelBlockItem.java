package com.trd.item.industrial.fluids;

import com.trd.api.fluids.system.BarrelTier;
import com.trd.api.fluids.system.FluidInfoHelper;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Блочная бочка как предмет. Даёт:
 *  - динамичный буфер жидкости (IFluidHandlerItem): машина может залить/выкачать жидкость
 *    в/из бочки вплоть до полной ёмкости тира, данные хранятся в BlockEntityTag бочки
 *    (ключи FluidName/Amount/Tag — совместимо с FluidBarrelBlockEntity);
 *  - полоску «прочность» под иконкой: заполненность объёма, окрашенную в цвет залитой жидкости.
 */
public class BarrelBlockItem extends BlockItem {

    private final BarrelTier tier;

    public BarrelBlockItem(Block block, BarrelTier tier, Properties properties) {
        super(block, properties);
        this.tier = tier;
    }

    public BarrelTier getTier() { return tier; }

    // ═══════════════ РАБОТА С ЖИДКОСТЬЮ (NBT в BlockEntityTag) ═══════════════

    private CompoundTag getBETag(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return stack.getTag().getCompound("BlockEntityTag");
    }

    /** Жидкость бочки (может быть частично заполнена). */
    public FluidStack getFluid(ItemStack stack) {
        CompoundTag beTag = getBETag(stack);
        String fluidName = beTag.getString("FluidName");
        int amount = beTag.getInt("Amount");
        if (fluidName.isEmpty() || fluidName.equals("minecraft:empty") || amount <= 0) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(fluidName));
        if (fluid == null || fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        FluidStack fs = new FluidStack(fluid, amount);
        if (beTag.contains("Tag", Tag.TAG_COMPOUND)) fs.setTag(beTag.getCompound("Tag").copy());
        return fs;
    }

    public int getFluidAmount(ItemStack stack) {
        return getFluid(stack).getAmount();
    }

    private void writeFluid(ItemStack stack, FluidStack fluid) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag beTag = getBETag(stack);
        if (fluid.isEmpty()) {
            beTag.putString("FluidName", "minecraft:empty");
            beTag.putInt("Amount", 0);
            beTag.remove("Tag");
        } else {
            beTag.putString("FluidName", ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString());
            beTag.putInt("Amount", fluid.getAmount());
            if (fluid.getTag() != null) {
                beTag.put("Tag", fluid.getTag().copy());
            } else {
                beTag.remove("Tag");
            }
        }
        root.put("BlockEntityTag", beTag);
    }

    // ═══════════════ ПОЛОСКА «ПРОЧНОСТЬ» ПОД ИКОНКОЙ ═══════════════
    // Заполненность объёма жидкости, окрашенная в цвет залитой жидкости.

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isBarVisible(ItemStack stack) {
        return getFluidAmount(stack) > 0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getBarWidth(ItemStack stack) {
        int amount = getFluidAmount(stack);
        int cap = tier.getCapacity();
        return Math.round(13.0F * amount / cap);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getBarColor(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        return fluid.isEmpty() ? 0xFFFFFF : FluidInfoHelper.getRgbColor(fluid);
    }

    // ═══════════════ CAPABILITY: FLUID_HANDLER_ITEM (динамичный буфер) ═══════════════

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandler(stack);
    }

    private class FluidHandler implements IFluidHandlerItem, ICapabilityProvider {
        private final ItemStack stack;
        private final LazyOptional<IFluidHandlerItem> holder = LazyOptional.of(() -> this);

        FluidHandler(ItemStack stack) {
            this.stack = stack;
        }

        @Override public ItemStack getContainer() { return stack; }
        @Override public int getTanks() { return 1; }

        @Override public @NotNull FluidStack getFluidInTank(int tank) {
            return getFluid(stack);
        }

        @Override public int getTankCapacity(int tank) {
            return tier.getCapacity();
        }

        @Override public boolean isFluidValid(int tank, @NotNull FluidStack resource) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            FluidStack current = getFluid(stack);
            if (!current.isEmpty() && !current.isFluidEqual(resource)) return 0;

            int space = tier.getCapacity() - current.getAmount();
            int toFill = Math.min(resource.getAmount(), space);
            if (toFill <= 0) return 0;

            if (action.execute()) {
                if (current.isEmpty()) current = resource.copy();
                current.setAmount(current.getAmount() + toFill);
                writeFluid(stack, current);
            }
            return toFill;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            FluidStack current = getFluid(stack);
            if (current.isEmpty() || !current.isFluidEqual(resource)) return FluidStack.EMPTY;

            int toDrain = Math.min(resource.getAmount(), current.getAmount());
            if (toDrain <= 0) return FluidStack.EMPTY;

            FluidStack drained = current.copy();
            drained.setAmount(toDrain);

            if (action.execute()) {
                FluidStack remaining = current.copy();
                remaining.setAmount(current.getAmount() - toDrain);
                writeFluid(stack, remaining.getAmount() > 0 ? remaining : FluidStack.EMPTY);
            }
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            FluidStack current = getFluid(stack);
            if (current.isEmpty()) return FluidStack.EMPTY;

            int toDrain = Math.min(maxDrain, current.getAmount());
            if (toDrain <= 0) return FluidStack.EMPTY;

            FluidStack drained = current.copy();
            drained.setAmount(toDrain);

            if (action.execute()) {
                FluidStack remaining = current.copy();
                remaining.setAmount(current.getAmount() - toDrain);
                writeFluid(stack, remaining.getAmount() > 0 ? remaining : FluidStack.EMPTY);
            }
            return drained;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) return holder.cast();
            return LazyOptional.empty();
        }
    }
}
