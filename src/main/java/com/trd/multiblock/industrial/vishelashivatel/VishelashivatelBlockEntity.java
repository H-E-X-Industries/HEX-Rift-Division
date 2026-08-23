package com.trd.multiblock.industrial.vishelashivatel;

import com.trd.api.rotation.KineticNetwork;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.tools.FluidIdentifierItem;
import com.trd.menu.industrial.VishelashivatelMenu;
import com.trd.multiblock.system.IFluidTankProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Контроллер выщелащивателя — мультиблок 3x3x1 (#$# / $@$ / #$#).
 * Крутящий момент подаётся непрерывно через ВЕРХНЮЮ грань контроллера.
 * Жидкость заливается только через жидкостные порты ($), напрямую во
 * внутренний буфер залить нельзя. Тип жидкости копируется с жидкостного
 * идентификатора в специальном слоте.
 */
public class VishelashivatelBlockEntity extends com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity
        implements MenuProvider, IFluidTankProvider {

    public static final int TANK_CAPACITY = 48000; // mB

    public static final int INPUT_SLOT = 0;        // входной слот
    public static final int OUTPUT_SLOTS = 3;      // выходные слоты 1..3
    public static final int FIRST_OUTPUT_SLOT = 1;
    public static final int IDENTIFIER_SLOT = 4;   // слот жидкостного идентификатора
    public static final int TOTAL_SLOTS = 5;

    /** Входной инвентарь: 0 — вход, 1..3 — выходы, 4 — идентификатор. */
    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                requestKineticRecalculation();
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == IDENTIFIER_SLOT) {
                return stack.getItem() instanceof FluidIdentifierItem;
            }
            if (slot >= FIRST_OUTPUT_SLOT && slot < FIRST_OUTPUT_SLOT + OUTPUT_SLOTS) {
                return false; // в выходные слоты вставлять нельзя
            }
            return true;
        }
    };

    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            // Принимаем только тип жидкости с идентификатора, и только если
            // она используется хотя бы в одном рецепте.
            return isValidTargetFluid(stack.getFluid());
        }
    };

    // ===================== СОСТОЯНИЕ =====================

    private int progress = 0;
    private int maxProgress = 0;
    /** Текущий рабочий рецепт (пересчитывается каждый тик). */
    private VishelashivatelRecipe currentRecipe = null;
    /** Последнее отданное сети значение потребления (для перерасчёта сети). */
    private long lastConsumedTorque = 0;

    // ===================== КИНЕТИКА (потребление через верх) =====================

    @Override
    public long getMaxTorqueTolerance() { return Long.MAX_VALUE; }

    @Override
    public long getMaxTorque() { return Long.MAX_VALUE; }

    @Override
    public double getInertiaContribution() { return 20.0; }

    @Override
    public long getMaxSpeed() { return 1500L; }

    @Override
    public long getTorque() { return 0L; }

    @Override
    public boolean isSource() { return false; }

    @Override
    public long getConsumedTorque() {
        return currentRecipe != null && isWorking() ? currentRecipe.getConsumedTorque() : 0L;
    }

    /** Крутящий момент принимаем только через верхнюю грань. */
    @Override
    public Direction[] getPropagationDirections() {
        return new Direction[]{Direction.UP};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level lvl, BlockPos myPos) {
        return List.of(myPos.above());
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, com.trd.api.rotation.Rotational neighbor) {
        return neighbor instanceof com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
    }

    @Override
    public long getVisualSpeed() {
        // Конвенция знака для UP-распространения (как у дробителя)
        return -this.speed;
    }

    // ===================== ЖИДКОСТНЫЙ ИДЕНТИФИКАТОР =====================

    /** Запомненный тип жидкости (сохраняется в NBT, копируется с идентификатора). */
    private String storedFluidId = "";

    /**
     * Копирует тип жидкости с идентификатора в слоте во внутреннюю память.
     * Буфер запоминает тип: после извлечения идентификатора машина продолжает
     * принимать запомненную жидкость. Принимаются только жидкости,
     * используемые хотя бы в одном рецепте.
     *
     * @return true если тип изменился (нужна синхронизация клиенту)
     */
    private boolean updateStoredFluid() {
        ItemStack idStack = inventory.getStackInSlot(IDENTIFIER_SLOT);
        if (idStack.isEmpty() || !(idStack.getItem() instanceof FluidIdentifierItem)) return false;
        String selected = FluidIdentifierItem.getSelectedFluid(idStack);
        if (selected.isEmpty() || selected.equals("none")) return false;
        if (selected.equals(storedFluidId)) return false;
        try {
            ResourceLocation rl = new ResourceLocation(selected);
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid == null || !VishelashivatelRecipes.isFluidUsed(fluid)) return false;
            storedFluidId = selected;
            setChanged();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Тип жидкости, который буфер принимает сейчас. */
    public Fluid getTargetFluid() {
        if (storedFluidId.isEmpty()) return Fluids.EMPTY;
        try {
            ResourceLocation rl = new ResourceLocation(storedFluidId);
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            return fluid != null ? fluid : Fluids.EMPTY;
        } catch (Exception e) {
            return Fluids.EMPTY;
        }
    }

    private boolean isValidTargetFluid(Fluid fluid) {
        return fluid != null && fluid != Fluids.EMPTY && fluid == getTargetFluid();
    }

    // ===================== ЁМКОСТИ И CAPABILITIES =====================

    /** Хендлер для GUI (полный доступ). */
    private final LazyOptional<IItemHandler> selfHandler = LazyOptional.of(() -> inventory);

    /**
     * Хендлер для автоматизации (воронки/трубы):
     * вставлять можно только во входной слот, высасывать можно только из
     * выходных. Из входного слота и слота идентификатора воронки не могут
     * высосать предметы.
     */
    private final LazyOptional<IItemHandler> automationHandler = LazyOptional.of(() -> new IItemHandler() {
        @Override
        public int getSlots() { return inventory.getSlots(); }

        @Override
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != INPUT_SLOT) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < FIRST_OUTPUT_SLOT || slot >= FIRST_OUTPUT_SLOT + OUTPUT_SLOTS) {
                return ItemStack.EMPTY; // из входа и слота идентификатора нельзя высасывать
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
    });

    /**
     * Жидкостный хендлер, который доступен ТОЛЬКО жидкостным портам
     * (через IFluidTankProvider). Напрямую в контроллер жидкость залить нельзя.
     * Заливать можно только тип жидкости с идентификатора; сливать — можно.
     */
    private final LazyOptional<IFluidHandler> portFluidHandler = LazyOptional.of(() -> new IFluidHandler() {
        @Override
        public int getTanks() { return 1; }

        @Override
        public FluidStack getFluidInTank(int tank) { return fluidTank.getFluid(); }

        @Override
        public int getTankCapacity(int tank) { return fluidTank.getCapacity(); }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluidTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isValidTargetFluid(resource.getFluid())) return 0;
            return fluidTank.fill(resource, action);
        }

        // Сливать жидкость из буфера нельзя — только вливать
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    });

    @Override
    public LazyOptional<IFluidHandler> getFluidHandlerCapability() {
        return portFluidHandler;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        selfHandler.invalidate();
        automationHandler.invalidate();
        portFluidHandler.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // side == null — это доступ из GUI меню, отдаём полный хендлер
            return (side == null ? selfHandler : automationHandler).cast();
        }
        // FLUID_HANDLER наружу НЕ выдаём: жидкость идёт только через порты
        return super.getCapability(cap, side);
    }

    // ===================== ДАННЫЕ ДЛЯ МЕНЮ/HUD =====================

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> fluidTank.getFluidAmount();
                case 3 -> fluidTank.getCapacity();
                case 4 -> (int) Math.abs(getSpeed());
                case 5 -> currentRecipe != null ? (int) currentRecipe.getMinRpm() : 0;
                case 6 -> currentRecipe != null ? 1 : 0;
                case 7 -> getTargetFluid() != Fluids.EMPTY ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
            }
        }

        @Override
        public int getCount() { return 8; }
    };

    public ContainerData getData() { return data; }
    public ItemStackHandler getInventory() { return inventory; }
    public FluidTank getFluidTank() { return fluidTank; }
    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }
    public VishelashivatelRecipe getCurrentRecipe() { return currentRecipe; }

    public boolean isWorking() {
        if (currentRecipe == null) return false;
        long absSpeed = Math.abs(getSpeed());
        if (absSpeed < currentRecipe.getMinRpm()) return false;
        FluidStack tankFluid = fluidTank.getFluid();
        if (tankFluid.isEmpty()) return false;
        if (tankFluid.getAmount() < currentRecipe.getRequiredFluid().getAmount()) return false;
        return canFitOutputs();
    }

    private boolean canFitOutputs() {
        for (ItemStack result : currentRecipe.getItemOutputs()) {
            if (!canInsert(result)) return false;
        }
        return true;
    }

    private boolean canInsert(ItemStack result) {
        for (int i = FIRST_OUTPUT_SLOT; i < FIRST_OUTPUT_SLOT + OUTPUT_SLOTS; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(slot, result)
                    && slot.getCount() + result.getCount() <= slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // ===================== ТИК =====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, VishelashivatelBlockEntity be) {
        boolean changed = false;

        // Копируем тип жидкости с идентификатора (сразу при вставке в слот)
        if (be.updateStoredFluid()) changed = true;

        ItemStack input = be.inventory.getStackInSlot(INPUT_SLOT);
        FluidStack tankFluid = be.fluidTank.getFluid();

        VishelashivatelRecipe recipe = VishelashivatelRecipes.findMatching(input, tankFluid);
        if (recipe != be.currentRecipe) {
            be.currentRecipe = recipe;
            be.progress = 0;
            be.maxProgress = recipe != null ? recipe.getProcessTime() : 0;
            changed = true;
        }

        if (be.currentRecipe != null && be.isWorking()) {
            be.progress++;
            changed = true;

            if (be.progress >= be.maxProgress) {
                be.finishProcessing();
                be.progress = 0;
            }
        } else {
            // Непрерывность: пропало вращение / жидкость / предметы — прогресс сбрасывается
            if (be.progress > 0) {
                be.progress = 0;
                changed = true;
            }
        }

        // Пересчёт сети при изменении потребляемого момента
        long consumed = be.getConsumedTorque();
        if (consumed != be.lastConsumedTorque) {
            be.lastConsumedTorque = consumed;
            be.requestKineticRecalculation();
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void finishProcessing() {
        VishelashivatelRecipe recipe = this.currentRecipe;
        if (recipe == null) return;

        // Расход жидкости
        fluidTank.drain(recipe.getRequiredFluid().getAmount(), IFluidHandler.FluidAction.EXECUTE);

        // Расход предметов
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (!input.isEmpty()) input.shrink(recipe.getItemInput().getCount());

        // Выход
        for (ItemStack result : recipe.getItemOutputs()) {
            insertResult(result.copy());
        }
    }

    private void insertResult(ItemStack result) {
        for (int i = FIRST_OUTPUT_SLOT; i < FIRST_OUTPUT_SLOT + OUTPUT_SLOTS; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, result)) {
                int canAdd = Math.min(result.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (canAdd > 0) {
                    slot.grow(canAdd);
                    result.shrink(canAdd);
                    if (result.isEmpty()) return;
                }
            }
        }
        for (int i = FIRST_OUTPUT_SLOT; i < FIRST_OUTPUT_SLOT + OUTPUT_SLOTS; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                inventory.setStackInSlot(i, result);
                return;
            }
        }
        if (level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(),
                    worldPosition.getY(), worldPosition.getZ(), result);
        }
    }

    private void requestKineticRecalculation() {
        if (level instanceof ServerLevel serverLevel) {
            KineticNetwork net = KineticNetworkManager.get(serverLevel).getNetworkFor(worldPosition);
            if (net != null) net.requestRecalculation();
        }
    }

    /** Выброс содержимого при ломании блока. */
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(),
                        worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    // ===================== BE БАЗА =====================

    public VishelashivatelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VISHELASHIVATEL_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("FluidTank", fluidTank.writeToNBT(new CompoundTag()));
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putString("StoredFluid", storedFluidId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        fluidTank.readFromNBT(tag.getCompound("FluidTank"));
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        storedFluidId = tag.getString("StoredFluid");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.vishelashivatel");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new VishelashivatelMenu(id, inv, this, data);
    }
}
