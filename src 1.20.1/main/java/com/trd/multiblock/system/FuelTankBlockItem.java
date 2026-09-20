package com.trd.multiblock.system;

import com.trd.api.fluids.system.FluidInfoHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет цистерны. Добавляет полоску «прочность» под иконкой: заполненность объёма
 * жидкости, окрашенную в цвет залитой жидкости (как у бочек).
 */
public class FuelTankBlockItem extends MultiblockBlockItem {

    private final int capacity;

    public FuelTankBlockItem(Block block, int capacity, Properties properties) {
        super(block, properties);
        this.capacity = capacity;
    }

    /** Жидкость цистерны из BlockEntityTag (ключи FluidName/Amount/Tag — как у бочек). */
    private FluidStack readFluid(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return FluidStack.EMPTY;
        }
        CompoundTag be = stack.getTag().getCompound("BlockEntityTag");
        String name = be.getString("FluidName");
        int amount = be.getInt("Amount");
        if (name.isEmpty() || name.equals("minecraft:empty") || amount <= 0) return FluidStack.EMPTY;
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(name));
        if (fluid == null || fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        FluidStack fs = new FluidStack(fluid, amount);
        if (be.contains("Tag", Tag.TAG_COMPOUND)) fs.setTag(be.getCompound("Tag").copy());
        return fs;
    }

    // ═══════════════ ПОЛОСКА «ПРОЧНОСТЬ» ПОД ИКОНКОЙ ═══════════════

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isBarVisible(ItemStack stack) {
        return readFluid(stack).getAmount() > 0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * readFluid(stack).getAmount() / capacity);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getBarColor(ItemStack stack) {
        FluidStack fluid = readFluid(stack);
        return fluid.isEmpty() ? 0xFFFFFF : FluidInfoHelper.getRgbColor(fluid);
    }

    public int getCapacity() { return capacity; }
}
