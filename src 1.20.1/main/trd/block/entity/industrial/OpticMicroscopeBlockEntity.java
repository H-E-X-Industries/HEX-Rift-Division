package com.trd.block.entity.industrial;

import com.trd.item.ModItems;
import com.trd.item.conglomerates.ConglomerateItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity оптического микроскопа.
 *
 * <p>Слоты:
 * {@code 0} — входной слот куска конгломерата;
 * {@code 1} — выходной слот проанализированного куска;
 * {@code 2} — входной слот пипетки (наполнение буфера);
 * {@code 3} — выходной слот опустошённой пипетки.
 *
 * <p>Жидкостный буфер вмещает 100 мб и пополняется ТОЛЬКО пипетками через слот {@code 2}.
 * Анализ не проанализированного куска конгломерата требует 50 мб серной кислоты
 * и занимает 3 секунды (60 тиков). Проанализированный кусок просто переносится на выход.
 */
public class OpticMicroscopeBlockEntity extends BlockEntity {

    public static final int TOTAL_SLOTS = 4;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int PIPETTE_IN_SLOT = 2;
    public static final int PIPETTE_OUT_SLOT = 3;

    public static final int TANK_CAPACITY = 100;
    /** Сколько кислоты уходит за один анализ. */
    public static final int ACID_PER_ANALYSIS = 50;
    /** Время анализа в тиках (30 секунд). */
    public static final int MAX_PROGRESS = 600;

    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == INPUT_SLOT) {
                progress = 0;
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case INPUT_SLOT -> stack.is(ModItems.CONGLOMERATE_CHUNK.get());
                case OUTPUT_SLOT -> false;
                case PIPETTE_IN_SLOT -> stack.is(ModItems.PIPETTE.get())
                        || stack.is(ModItems.PIPETTE_IDUSTRIAL.get());
                case PIPETTE_OUT_SLOT -> false;
                default -> false;
            };
        }
    };

    public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return !stack.isEmpty() && stack.getFluid() == com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get();
        }
    };

    private int progress = 0;
    private int maxProgress = 0;

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> fluidTank.getFluidAmount();
                case 3 -> TANK_CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) progress = value;
            else if (index == 1) maxProgress = value;
        }

        @Override
        public int getCount() { return 4; }
    };

    public OpticMicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(com.trd.block.entity.ModBlockEntities.OPTIC_MICROSCOPE_BE.get(), pos, state);
    }

    public ItemStackHandler getItemHandler() { return itemHandler; }
    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }

    // ═══════════════════ TICK ═══════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state, OpticMicroscopeBlockEntity be) {
        be.processPipette();
        be.processAnalysis();
    }

    /**
     * Переносит пипетку из входного слота ({@code 2}) в выходной ({@code 3}),
     * сливая её содержимое в жидкостный буфер. Работает через FluidUtil — стружает
     * максимум из терминала; опустошённая пипетка (all-or-nothing) попадает на выход
     * только если выходной слот способен её принять.
     */
    private void processPipette() {
        ItemStack pipetteIn = itemHandler.getStackInSlot(PIPETTE_IN_SLOT);
        if (pipetteIn.isEmpty()) return;

        var sim = FluidUtil.tryEmptyContainer(pipetteIn, fluidTank, fluidTank.getSpace(), null, false);
        if (!sim.isSuccess()) return;

        ItemStack drained = sim.getResult();
        if (!canInsert(PIPETTE_OUT_SLOT, drained)) return;

        FluidUtil.tryEmptyContainer(pipetteIn, fluidTank, fluidTank.getSpace(), null, true);
        insertOrMerge(PIPETTE_OUT_SLOT, drained);
        itemHandler.getStackInSlot(PIPETTE_IN_SLOT).shrink(1);
    }

    /**
     * Основная логика анализа. Обрабатывает строго ОДИН кусок за раз: анализ запускается
     * только если выход способен принять результат, а по завершении на выход переносится
     * ровно один предмет (вход уменьшается на один, остаток анализируется следующей итерацией).
     * Уже проанализированный кусок просто остаётся в слоте.
     */
    private void processAnalysis() {
        ItemStack input = itemHandler.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            resetProgress();
            return;
        }

        // Уже проанализированный — просто оставляем в слоте, ничего не тратим и не двигаем.
        if (ConglomerateItem.isAnalyzed(input)) {
            resetProgress();
            return;
        }

        // Нельзя начать анализ, пока выход не примет результат (перерабатываем по одному).
        if (maxProgress == 0) {
            if (getSulfuricAcidAmount() < ACID_PER_ANALYSIS || !canAcceptOutput()) {
                return; // ждём: не хватает кислоты или выход занят
            }
            maxProgress = MAX_PROGRESS;
            progress = 0;
        }

        progress++;
        setChanged();

        if (progress >= maxProgress) {
            if (placeResult(input)) {
                resetProgress();
            }
            // Если выход занят — ждём (progress остаётся на максимуме), кислота не списывается.
        }
    }

    /** Есть ли куда положить результат анализа. Куски конгломерата не стакаются, поэтому нужен свободный выход. */
    private boolean canAcceptOutput() {
        return itemHandler.getStackInSlot(OUTPUT_SLOT).isEmpty();
    }

    /**
     * Переносит ОДИН проанализированный кусок на выход, списывая кислоту.
     * Возвращает {@code true}, если перенос удался.
     */
    private boolean placeResult(ItemStack input) {
        if (!canAcceptOutput()) return false;

        // Списываем кислоту
        fluidTank.drain(new FluidStack(com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get(), ACID_PER_ANALYSIS),
                IFluidHandler.FluidAction.EXECUTE);

        ItemStack out = input.copy();
        out.setCount(1);
        ConglomerateItem.setAnalyzed(out);
        itemHandler.setStackInSlot(OUTPUT_SLOT, out);
        itemHandler.getStackInSlot(INPUT_SLOT).shrink(1);
        return true;
    }

    private void resetProgress() {
        if (progress != 0 || maxProgress != 0) {
            progress = 0;
            maxProgress = 0;
            setChanged();
        }
    }

    private int getSulfuricAcidAmount() {
        FluidStack fluid = fluidTank.getFluid();
        if (fluid.isEmpty()) return 0;
        if (fluid.getFluid() != com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get()) return 0;
        return fluid.getAmount();
    }

    // ═══════════════════ HELPERS ═══════════════════

    private boolean canInsert(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack existing = itemHandler.getStackInSlot(slot);
        if (existing.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize();
    }

    private void insertOrMerge(int slot, ItemStack stack) {
        ItemStack existing = itemHandler.getStackInSlot(slot);
        if (existing.isEmpty()) {
            itemHandler.setStackInSlot(slot, stack);
        } else {
            existing.grow(stack.getCount());
        }
    }

    // ═══════════════════ CAPABILITIES ═══════════════════

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == Direction.DOWN) {
                // Снизу — только извлечение из выходов (кусок и пустая пипетка)
                return LazyOptional.of(() -> new ExtractOnlyHandler(itemHandler)).cast();
            }
            // Со всех остальных сторон — умный доступ: вставка только во входные слоты,
            // извлечение только из выходных (воронка не может вставить в выход и вынуть из входа).
            return LazyOptional.of(() -> new SmartHandler(itemHandler)).cast();
        }
        // Жидкостная capability намеренно не выставляется: микроскоп не работает с трубами,
        // буфер пополняется исключительно пипетками через слот.
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateCaps();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    // ═══════════════════ NBT ═══════════════════

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        fluidTank.writeToNBT(tag);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        fluidTank.readFromNBT(tag);
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    /** Только извлечение из выходных слотов (1 и 3). */
    private static class ExtractOnlyHandler implements IItemHandler {
        private final ItemStackHandler wrapped;

        ExtractOnlyHandler(ItemStackHandler wrapped) { this.wrapped = wrapped; }

        @Override public int getSlots() { return 2; }

        @Override
        public ItemStack getStackInSlot(int index) {
            int real = (index == 0) ? OUTPUT_SLOT : PIPETTE_OUT_SLOT;
            return wrapped.getStackInSlot(real);
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            int real = (index == 0) ? OUTPUT_SLOT : PIPETTE_OUT_SLOT;
            return wrapped.extractItem(real, amount, simulate);
        }

        @Override public int getSlotLimit(int index) {
            int real = (index == 0) ? OUTPUT_SLOT : PIPETTE_OUT_SLOT;
            return wrapped.getSlotLimit(real);
        }

        @Override public boolean isItemValid(int index, ItemStack stack) { return false; }
    }

    /**
     * Умный доступ для боковых/верхних сторон: вставка разрешена только во входные слоты
     * (0 — кусок, 2 — пипетка), извлечение — только из выходных слотов (1, 3).
     * Это не позволяет воронкам вставлять предметы в выход и забирать из входа.
     */
    private static class SmartHandler implements IItemHandler {
        private final ItemStackHandler wrapped;

        SmartHandler(ItemStackHandler wrapped) { this.wrapped = wrapped; }

        @Override public int getSlots() { return 4; }

        @Override
        public ItemStack getStackInSlot(int index) {
            return wrapped.getStackInSlot(index);
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            if (index == OUTPUT_SLOT || index == PIPETTE_OUT_SLOT) return stack;
            return wrapped.insertItem(index, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            if (index == INPUT_SLOT || index == PIPETTE_IN_SLOT) return ItemStack.EMPTY;
            return wrapped.extractItem(index, amount, simulate);
        }

        @Override
        public int getSlotLimit(int index) {
            return wrapped.getSlotLimit(index);
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            if (index == OUTPUT_SLOT || index == PIPETTE_OUT_SLOT) return false;
            return wrapped.isItemValid(index, stack);
        }
    }
}
