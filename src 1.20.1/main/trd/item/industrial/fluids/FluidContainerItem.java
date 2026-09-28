package com.trd.item.industrial.fluids;

import com.trd.api.fluids.system.FluidInfoHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Жидкостный контейнер-предмет (пипетка, промышленная пипетка, жидкостный контейнер).
 *
 * <p>Особенности:
 * <ul>
 *   <li>{@link #capacity} — фиксированный буфер. Контейнер бывает ТОЛЬКО полностью
 *       заполненным или полностью пустым (all-or-nothing).</li>
 *   <li>{@link #maxCorrosion} / {@link #maxTemperature} — устойчивость к коррозии и температуре.
 *       Если залить жидкость, выходящую за пределы устойчивости, контейнер наполняется ею и
 *       тут же растворяется (опустошается прямо в момент залива — разовая проверка в самом
 *       предмете, без сканирования мира). У помещённого в инвентарь растворяющегося стека
 *       воспроизводится звук протекающей бочки.</li>
 *   <li>Реализует {@link IFluidHandlerItem}, поэтому автоматически работает в жидкостных
 *       слотах цистерн/бочек (через {@code FluidUtil}).</li>
 * </ul>
 */
public class FluidContainerItem extends Item {

    public static final String TAG_FLUID = "Fluid";          // CompoundTag (FluidStack)
    public static final String TAG_DISSOLVE = "trd:dissolve"; // флаг растворения

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

    // ═══════════════════════════════════════════════════════════
    // РАБОТА С ЖИДКОСТЬЮ (NBT)
    // ═══════════════════════════════════════════════════════════

    /** Возвращает жидкость контейнера: либо полный буфер, либо пусто (защита от частичных). */
    public static FluidStack getFluid(ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getTag().contains(TAG_FLUID)) {
            return FluidStack.EMPTY;
        }
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(stack.getTag().getCompound(TAG_FLUID));
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid;
    }

    /** Полностью заполняет контейнер указанной жидкостью. */
    public void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            if (stack.hasTag()) stack.getTag().remove(TAG_FLUID);
            return;
        }
        FluidStack stored = fluid.copy();
        stored.setAmount(capacity);
        stack.getOrCreateTag().put(TAG_FLUID, stored.writeToNBT(new CompoundTag()));
    }

    /**
     * Создаёт предзаполненный контейнер заданной жидкостью (для креативной вкладки).
     * Возвращает пустой стек, если жидкость несовместима с контейнером.
     */
    public static ItemStack createFilled(Item item, net.minecraft.world.level.material.Fluid fluid) {
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
        return stack.hasTag() && stack.getTag().getBoolean(TAG_DISSOLVE);
    }

    /** Может ли контейнер выдержать данную жидкость. */
    private boolean canWithstand(FluidStack fluid) {
        return FluidInfoHelper.getCorrosivity(fluid) <= maxCorrosion
                && FluidInfoHelper.getTemperature(fluid) <= maxTemperature;
    }

    // ═══════════════════════════════════════════════════════════
    // СТАКИНГ
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    // Пипетки не стакаются вовсе (задаётся stacksTo(1) в конструкторе).
    // Жидкостный контейнер стакается по 64; жидкости внутри хранятся в NBT, поэтому
    // залитые разными жидкостями стеки не сольются (совпадающий NBT обязателен).

    // ═══════════════════════════════════════════════════════════
    // ТУЛТИП (как у бочки: количество + цвет залитой жидкости)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            int color = FluidInfoHelper.getRgbColor(fluid);
            tooltip.add(Component.literal("  ")
                    .append(FluidInfoHelper.getDisplayName(fluid))
                    .append(Component.literal(": " + fluid.getAmount() + " / " + capacity + " mB"))
                    .withStyle(style -> style.withColor(color)));
            // Демонстрация устойчивости САМИХ пипеток/контейнера (макс. коррозия и температура).
            tooltip.addAll(getMaxRatingTooltip());
        } else {
            tooltip.add(Component.translatable("tooltip.trd.fluid_barrel.empty").withStyle(ChatFormatting.GRAY));
            tooltip.addAll(getMaxRatingTooltip());
        }
    }

    /** Тултип-характеристики самого контейнера: макс. коррозионность и температура (как у труб). */
    public java.util.List<Component> getMaxRatingTooltip() {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(Component.translatable("tooltip.trd.fluid_pipe.max_temp").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(maxTemperature + " °C").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.translatable("tooltip.trd.fluid_pipe.max_corrosion").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(maxCorrosion)).withStyle(ChatFormatting.YELLOW)));
        return tooltip;
    }

    // ═══════════════════════════════════════════════════════════
    // РАСТВОРЕНИЕ
    // ═══════════════════════════════════════════════════════════

    /**
     * Происходит при тике предмета (в инвентаре игрока/в руке): если контейнер был
     * наполнен несовместимой жидкостью — он растворяется (исчезает) со звуком.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!isDissolving(stack)) return;
        removeAndPlaySound(stack, entity, level);
    }

    /**
     * Немедленное растворение при заливе несовместимой жидкости. Опустошает стек, поэтому
     * вызывающая машина ({@code FluidUtil} / {@code insertOrMerge}) не разместит результат
     * и вернёт пусто — контейнер исчезает в момент залива, без сканирования мира.
     */
    private void dissolveNow(ItemStack stack) {
        stack.shrink(stack.getCount());
    }

    /** Удаляет стек (исчезает) и проигрывает звук протекающей бочки рядом с сущностью. */
    public void removeAndPlaySound(ItemStack stack, net.minecraft.world.entity.Entity entity, Level level) {
        removeAndPlaySound(stack, entity.blockPosition(), level);
    }

    /** Удаляет стек (исчезает) и проигрывает звук протекающей бочки в позиции. */
    public static void removeAndPlaySound(ItemStack stack, net.minecraft.core.BlockPos pos, Level level) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.2F);
        stack.shrink(stack.getCount());
    }

    // ═══════════════════════════════════════════════════════════
    // CAPABILITY: FLUID_HANDLER_ITEM (all-or-nothing)
    // ═══════════════════════════════════════════════════════════

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandler(stack);
    }

    private class FluidHandler implements IFluidHandlerItem, ICapabilityProvider {
        private final ItemStack stack;
        private final FluidStack[] internal;
        private boolean updateNeeded = false;
        private final LazyOptional<IFluidHandlerItem> holder = LazyOptional.of(() -> this);

        FluidHandler(ItemStack stack) {
            this.stack = stack;
            this.internal = new FluidStack[]{FluidStack.EMPTY};
            this.internal[0] = getFluid(stack);
            if (!internal[0].isEmpty()) {
                internal[0].setAmount(capacity);
            }
        }

        @Override public ItemStack getContainer() { return stack; }

        @Override public int getTanks() { return 1; }

        @Override public @NotNull FluidStack getFluidInTank(int tank) {
            return updateNeeded ? FluidStack.EMPTY : internal[0];
        }

        @Override public int getTankCapacity(int tank) { return capacity; }

        @Override public boolean isFluidValid(int tank, @NotNull FluidStack resource) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (!internal[0].isEmpty()) return 0;              // уже заполнен
            if (resource.getAmount() < capacity) return 0;     // только полный буфер

            FluidStack toFill = resource.copy();
            toFill.setAmount(capacity);

            if (action.execute()) {
                internal[0] = toFill;
                updateNeeded = true;
                setFluid(stack, toFill);
                // Несовместимая жидкость → контейнер наполняется и тут же растворяется
                // (разовая проверка в момент залива, без сканирования мира).
                if (!canWithstand(toFill)) {
                    stack.getOrCreateTag().putBoolean(TAG_DISSOLVE, true);
                    dissolveNow(stack);
                }
            }
            return capacity;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            if (internal[0].isEmpty() || !internal[0].isFluidEqual(resource)) return FluidStack.EMPTY;
            if (internal[0].getAmount() < resource.getAmount()) return FluidStack.EMPTY; // all-or-nothing

            FluidStack drained = internal[0].copy();
            if (action.execute()) {
                internal[0] = FluidStack.EMPTY;
                updateNeeded = true;
                stack.getOrCreateTag().remove(TAG_FLUID);
                stack.getOrCreateTag().remove(TAG_DISSOLVE);
            }
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (internal[0].isEmpty()) return FluidStack.EMPTY;
            if (maxDrain < internal[0].getAmount()) return FluidStack.EMPTY; // all-or-nothing

            FluidStack drained = internal[0].copy();
            if (action.execute()) {
                internal[0] = FluidStack.EMPTY;
                updateNeeded = true;
                stack.getOrCreateTag().remove(TAG_FLUID);
                stack.getOrCreateTag().remove(TAG_DISSOLVE);
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
