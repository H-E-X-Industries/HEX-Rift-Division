package com.trd.multiblock.industrial.centrifuge;

import com.trd.block.entity.ModBlockEntities;
import com.trd.item.energy.EnergyCellItem;
import com.trd.item.energy.ModBatteryItem;
import com.trd.item.tools.FluidIdentifierItem;
import com.trd.menu.industrial.CentrifugeCylinderMenu;
import com.trd.multiblock.system.IFluidTankProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Жидкостная насадка центрифуги (centrifuge_cylinder) — контроллер
 * мультиблока 1x1x2, ставится на мотор. Перерабатывает жидкость из
 * входного буфера в другие жидкости и/или предметы.
 * <p>
 * Тип входной жидкости копируется с жидкостного идентификатора в
 * специальном слоте (логика выщелачивателя). Залить можно только
 * запомненный тип и только если он используется хоть в одном рецепте;
 * вылить из входного буфера нельзя. Выходные буферы только отдают.
 */
public class CentrifugeCylinderBlockEntity extends BlockEntity implements MenuProvider, IFluidTankProvider {

    public static final int IDENTIFIER_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int FIRST_OUTPUT_SLOT = 2;
    public static final int OUTPUT_SLOTS = 4;
    public static final int TOTAL_SLOTS = 6;

    public static final int TANK_CAPACITY = 8000; // mB
    public static final int INPUT_TANK = 0;
    public static final int FIRST_OUTPUT_TANK = 1;
    public static final int OUTPUT_TANKS = 4;
    public static final int TOTAL_TANKS = 5;

    public static final long MAX_ENERGY = 50_000L;
    public static final long RECEIVE_SPEED = 1_000L;
    public static final double ENERGY_PER_TICK = 250.0 / 20.0; // 250 JE/сек

    /** Инвентарь: 0 — идентификатор, 1 — аккумулятор, 2..5 — выходы. */
    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == IDENTIFIER_SLOT) return stack.getItem() instanceof FluidIdentifierItem;
            if (slot == BATTERY_SLOT) return isBattery(stack);
            return false; // в выходные слоты вставлять нельзя
        }
    };

    /** Буферы: 0 — вход, 1..4 — выходы. */
    private final FluidTank[] tanks = new FluidTank[TOTAL_TANKS];

    // ===================== СОСТОЯНИЕ =====================

    private long energyStored = 0L;
    private int progress = 0;
    private int maxProgress = 0;
    private double jeCarry = 0.0;
    private CentrifugeCylinderRecipe currentRecipe = null;
    /** Запомненный тип жидкости (копируется с идентификатора). */
    private String storedFluidId = "";

    public CentrifugeCylinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(), pos, state);
        for (int i = 0; i < TOTAL_TANKS; i++) {
            final int index = i;
            tanks[i] = new FluidTank(TANK_CAPACITY) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                    if (level != null && !level.isClientSide) {
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    }
                }

                @Override
                public boolean isFluidValid(FluidStack stack) {
                    // Входной буфер принимает только тип с идентификатора.
                    // Выходные — разрешающие: запрет заливки извне реализован
                    // в portFluidHandler (единственной точке внешнего доступа).
                    if (index != INPUT_TANK) return true;
                    return isValidTargetFluid(stack.getFluid());
                }
            };
        }
    }

    // ===================== ЖИДКОСТНЫЙ ИДЕНТИФИКАТОР =====================

    /**
     * Копирует тип жидкости с идентификатора в слоте во внутреннюю память.
     * Принимаются только жидкости, используемые хотя бы в одном рецепте.
     *
     * @return true если тип изменился
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
            if (fluid == null || !CentrifugeCylinderRecipes.isFluidUsed(fluid)) return false;
            storedFluidId = selected;
            setChanged();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Тип жидкости, который входной буфер принимает сейчас. */
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

    // ===================== ДОСТУП ДЛЯ GUI/MOTOR =====================

    public ItemStackHandler getInventory() { return inventory; }

    public FluidTank getInputTank() { return tanks[INPUT_TANK]; }

    public FluidTank getOutputTank(int index) { return tanks[FIRST_OUTPUT_TANK + index]; }

    public FluidTank getTank(int index) { return tanks[index]; }

    public long getEnergyStored() { return energyStored; }

    public long getMaxEnergy() { return MAX_ENERGY; }

    public void addEnergy(long amount) {
        energyStored = Math.max(0, Math.min(MAX_ENERGY, energyStored + amount));
        setChanged();
    }

    public int getProgress() { return progress; }

    public int getMaxProgress() { return maxProgress; }

    @Nullable
    public CentrifugeCylinderRecipe getCurrentRecipe() { return currentRecipe; }

    private static boolean isBattery(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).isPresent()
                || stack.getItem() instanceof ModBatteryItem
                || stack.getItem() instanceof EnergyCellItem;
    }

    // ===================== CAPABILITIES =====================

    /** Хендлер для GUI (полный доступ). */
    private final LazyOptional<IItemHandler> selfHandler = LazyOptional.of(() -> inventory);

    /**
     * Жидкостный хендлер для портов мультиблока: заливка — только во входной
     * буфер (и только разрешённый тип), слив — только из выходных.
     */
    private final LazyOptional<IFluidHandler> portFluidHandler = LazyOptional.of(() -> new IFluidHandler() {
        @Override
        public int getTanks() { return TOTAL_TANKS; }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0 || tank >= TOTAL_TANKS) return FluidStack.EMPTY;
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < 0 || tank >= TOTAL_TANKS) return 0;
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == INPUT_TANK && tanks[INPUT_TANK].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isValidTargetFluid(resource.getFluid())) return 0;
            return tanks[INPUT_TANK].fill(resource, action);
        }

        // Из входного буфера выливать нельзя
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            for (int i = FIRST_OUTPUT_TANK; i < TOTAL_TANKS; i++) {
                FluidStack cur = tanks[i].getFluid();
                if (!cur.isEmpty() && cur.getFluid() == resource.getFluid()) {
                    return tanks[i].drain(resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = FIRST_OUTPUT_TANK; i < TOTAL_TANKS; i++) {
                if (!tanks[i].getFluid().isEmpty()) {
                    return tanks[i].drain(maxDrain, action);
                }
            }
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
        portFluidHandler.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // side == null — доступ из GUI меню, отдаём полный хендлер
            return (side == null ? selfHandler : LazyOptional.empty()).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // Заливка — только во входной буфер, слив — только из выходных
            // (правила внутри portFluidHandler)
            return portFluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    // ===================== ДАННЫЕ ДЛЯ МЕНЮ =====================

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> (int) energyStored;
                case 3 -> (int) MAX_ENERGY;
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
        public int getCount() { return 4; }
    };

    public ContainerData getData() { return data; }

    // ===================== ТИК =====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeCylinderBlockEntity be) {
        boolean changed = be.chargeFromBattery();

        if (be.updateStoredFluid()) changed = true;

        CentrifugeCylinderRecipe recipe = CentrifugeCylinderRecipes.findMatching(be.tanks[INPUT_TANK].getFluid());
        if (recipe != be.currentRecipe) {
            be.currentRecipe = recipe;
            be.maxProgress = recipe != null ? recipe.getProcessTime() : 0;
            be.progress = 0;
            changed = true;
        }

        if (be.currentRecipe == null) {
            if (be.progress > 0) {
                be.progress = 0;
                changed = true;
            }
        } else if (be.canFitOutputs(be.currentRecipe)) {
            double projected = be.jeCarry + ENERGY_PER_TICK;
            long whole = (long) Math.floor(projected);
            if (be.energyStored >= whole) {
                be.energyStored -= whole;
                be.jeCarry = projected - Math.floor(projected);
                be.progress++;
                if (be.progress >= be.maxProgress) {
                    be.finishProcessing(be.currentRecipe);
                    be.progress = 0;
                }
                changed = true;
            }
        } else {
            if (be.progress > 0) {
                be.progress = 0;
                changed = true;
            }
        }

        if (changed || be.progress > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void finishProcessing(CentrifugeCylinderRecipe recipe) {
        // Расход входной жидкости
        tanks[INPUT_TANK].drain(recipe.getInputFluid().getAmount(), IFluidHandler.FluidAction.EXECUTE);

        // Выходные жидкости: сначала доливаем в частично заполненные, потом в пустые
        for (FluidStack out : recipe.getFluidOutputs()) {
            fillOutput(out.copy());
        }

        // Выходные предметы
        for (List<ItemStack> group : groupOutputs(recipe.getItemOutputs())) {
            ItemStack representative = group.get(0);
            int remaining = 0;
            for (ItemStack stack : group) remaining += stack.getCount();

            List<Integer> candidates = new ArrayList<>();
            for (int i = FIRST_OUTPUT_SLOT; i < FIRST_OUTPUT_SLOT + OUTPUT_SLOTS; i++) candidates.add(i);
            shuffle(candidates);

            for (int slot : candidates) {
                if (remaining <= 0) break;
                ItemStack cur = inventory.getStackInSlot(slot);
                if (cur.isEmpty()) {
                    int put = Math.min(representative.getMaxStackSize(), remaining);
                    ItemStack placed = representative.copy();
                    placed.setCount(put);
                    inventory.setStackInSlot(slot, placed);
                    remaining -= put;
                } else if (ItemStack.isSameItemSameTags(cur, representative)) {
                    int space = cur.getMaxStackSize() - cur.getCount();
                    int put = Math.min(space, remaining);
                    if (put > 0) {
                        cur.grow(put);
                        remaining -= put;
                    }
                }
            }

            if (remaining > 0 && level != null) {
                ItemStack leftover = representative.copy();
                leftover.setCount(remaining);
                Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, leftover);
            }
        }
    }

    private void fillOutput(FluidStack out) {
        int remaining = out.getAmount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = FIRST_OUTPUT_TANK; i < TOTAL_TANKS && remaining > 0; i++) {
                FluidStack cur = tanks[i].getFluid();
                if (pass == 0) {
                    if (!cur.isEmpty() && cur.getFluid() == out.getFluid()) {
                        remaining -= tanks[i].fill(new FluidStack(out.getFluid(), remaining),
                                IFluidHandler.FluidAction.EXECUTE);
                    }
                } else if (cur.isEmpty()) {
                    remaining -= tanks[i].fill(new FluidStack(out.getFluid(), remaining),
                            IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private static List<List<ItemStack>> groupOutputs(List<ItemStack> outputs) {
        List<List<ItemStack>> groups = new ArrayList<>();
        for (ItemStack out : outputs) {
            if (out.isEmpty()) continue;
            boolean merged = false;
            for (List<ItemStack> group : groups) {
                if (ItemStack.isSameItemSameTags(group.get(0), out)) {
                    group.add(out);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                List<ItemStack> group = new ArrayList<>();
                group.add(out);
                groups.add(group);
            }
        }
        return groups;
    }

    private void shuffle(List<Integer> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = level.random.nextInt(i + 1);
            int tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private boolean canFitOutputs(CentrifugeCylinderRecipe recipe) {
        for (FluidStack out : recipe.getFluidOutputs()) {
            if (!canFitFluid(out)) return false;
        }

        ItemStack[] sim = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            sim[i] = inventory.getStackInSlot(FIRST_OUTPUT_SLOT + i).copy();
        }
        for (List<ItemStack> group : groupOutputs(recipe.getItemOutputs())) {
            ItemStack rep = group.get(0);
            int total = 0;
            for (ItemStack stack : group) total += stack.getCount();

            for (int pass = 0; pass < 2 && total > 0; pass++) {
                for (int i = 0; i < OUTPUT_SLOTS && total > 0; i++) {
                    ItemStack cur = sim[i];
                    if (pass == 0) {
                        if (!cur.isEmpty() && ItemStack.isSameItemSameTags(cur, rep)) {
                            int space = cur.getMaxStackSize() - cur.getCount();
                            int put = Math.min(space, total);
                            cur.grow(put);
                            total -= put;
                        }
                    } else if (cur.isEmpty()) {
                        int put = Math.min(rep.getMaxStackSize(), total);
                        ItemStack placed = rep.copy();
                        placed.setCount(put);
                        sim[i] = placed;
                        total -= put;
                    }
                }
            }
            if (total > 0) return false;
        }
        return true;
    }

    private boolean canFitFluid(FluidStack out) {
        int remaining = out.getAmount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = FIRST_OUTPUT_TANK; i < TOTAL_TANKS && remaining > 0; i++) {
                FluidStack cur = tanks[i].getFluid();
                if (pass == 0) {
                    if (!cur.isEmpty() && cur.getFluid() == out.getFluid()) {
                        remaining -= Math.min(remaining, tanks[i].getCapacity() - cur.getAmount());
                    }
                } else if (cur.isEmpty()) {
                    remaining -= Math.min(remaining, tanks[i].getCapacity());
                }
            }
        }
        return remaining <= 0;
    }

    private boolean chargeFromBattery() {
        ItemStack battery = inventory.getStackInSlot(BATTERY_SLOT);
        if (battery.isEmpty() || energyStored >= MAX_ENERGY) return false;

        boolean[] changed = {false};

        battery.getCapability(ForgeCapabilities.ENERGY).ifPresent(storage -> {
            if (storage.canExtract()) {
                int max = (int) Math.min(MAX_ENERGY - energyStored, RECEIVE_SPEED);
                int extracted = storage.extractEnergy(max, false);
                if (extracted > 0) {
                    energyStored += extracted;
                    changed[0] = true;
                }
            }
        });

        if (!changed[0] && energyStored < MAX_ENERGY) {
            battery.getCapability(com.trd.capability.ModCapabilities.ENERGY_PROVIDER).ifPresent(provider -> {
                if (provider.canExtract()) {
                    long max = Math.min(MAX_ENERGY - energyStored, RECEIVE_SPEED);
                    long extracted = provider.extractEnergy(max, false);
                    if (extracted > 0) {
                        energyStored += extracted;
                        changed[0] = true;
                    }
                }
            });
        }

        return changed[0];
    }

    // ===================== NBT / SYNC =====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        CompoundTag tanksTag = new CompoundTag();
        for (int i = 0; i < TOTAL_TANKS; i++) {
            tanksTag.put("Tank" + i, tanks[i].writeToNBT(new CompoundTag()));
        }
        tag.put("Tanks", tanksTag);
        tag.putLong("Energy", energyStored);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putString("StoredFluid", storedFluidId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        CompoundTag tanksTag = tag.getCompound("Tanks");
        for (int i = 0; i < TOTAL_TANKS; i++) {
            tanks[i].readFromNBT(tanksTag.getCompound("Tank" + i));
        }
        energyStored = tag.getLong("Energy");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        storedFluidId = tag.getString("StoredFluid");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ===================== MENU =====================

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.centrifuge_cylinder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CentrifugeCylinderMenu(id, inv, this, data);
    }
}
