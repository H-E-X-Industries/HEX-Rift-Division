package com.trd.multiblock.industrial.centrifuge.cylinder;

import com.trd.block.entity.ModBlockEntities;
import com.trd.capability.ModCapabilities;
import com.trd.item.industrial.energy.EnergyCellItem;
import com.trd.item.industrial.energy.ModBatteryItem;
import com.trd.item.industrial.fluids.FluidIdentifierItem;
import com.trd.menu.industrial.CentrifugeCylinderMenu;
import com.trd.multiblock.system.IFluidTankProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
            return false;
        }
    };

    private final FluidTank[] tanks = new FluidTank[TOTAL_TANKS];

    private long energyStored = 0L;
    private int progress = 0;
    private int maxProgress = 0;
    private double jeCarry = 0.0;
    private CentrifugeCylinderRecipe currentRecipe = null;
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
                    if (index != INPUT_TANK) return true;
                    return isValidTargetFluid(stack.getFluid());
                }
            };
        }
    }

    private boolean updateStoredFluid() {
        ItemStack idStack = inventory.getStackInSlot(IDENTIFIER_SLOT);
        if (idStack.isEmpty() || !(idStack.getItem() instanceof FluidIdentifierItem)) return false;
        String selected = FluidIdentifierItem.getSelectedFluid(idStack);
        if (selected.isEmpty() || selected.equals("none")) return false;
        if (selected.equals(storedFluidId)) return false;
        try {
            ResourceLocation rl = ResourceLocation.parse(selected);
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (fluid == null || fluid == Fluids.EMPTY || !CentrifugeCylinderRecipes.isFluidUsed(fluid)) return false;
            storedFluidId = selected;
            setChanged();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Fluid getTargetFluid() {
        if (storedFluidId.isEmpty()) return Fluids.EMPTY;
        try {
            ResourceLocation rl = ResourceLocation.parse(storedFluidId);
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            return fluid != null ? fluid : Fluids.EMPTY;
        } catch (Exception e) {
            return Fluids.EMPTY;
        }
    }

    private boolean isValidTargetFluid(Fluid fluid) {
        return fluid != null && fluid != Fluids.EMPTY && fluid == getTargetFluid();
    }

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
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null
                || stack.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM) != null
                || stack.getItem() instanceof ModBatteryItem
                || stack.getItem() instanceof EnergyCellItem;
    }

    // ===================== CAPABILITIES =====================

    private final IFluidHandler portFluidHandler = new IFluidHandler() {
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
    };

    @Override
    public IFluidHandler getFluidHandlerCapability() {
        return portFluidHandler;
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
            level.sendBlockUpdated(pos, state, state, 2);
        }
    }

    private void finishProcessing(CentrifugeCylinderRecipe recipe) {
        tanks[INPUT_TANK].drain(recipe.getInputFluid().getAmount(), IFluidHandler.FluidAction.EXECUTE);

        List<FluidStack> fluidOutputs = recipe.getFluidOutputs();
        for (int i = 0; i < fluidOutputs.size() && i < OUTPUT_TANKS; i++) {
            FluidStack out = fluidOutputs.get(i).copy();
            FluidTank tank = tanks[FIRST_OUTPUT_TANK + i];
            FluidStack cur = tank.getFluid();
            if (cur.isEmpty() || cur.getFluid() == out.getFluid()) {
                tank.fill(out, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        List<ItemStack> itemOutputs = recipe.getItemOutputs();
        for (int i = 0; i < itemOutputs.size() && i < OUTPUT_SLOTS; i++) {
            ItemStack result = itemOutputs.get(i).copy();
            int slotIndex = FIRST_OUTPUT_SLOT + i;
            ItemStack cur = inventory.getStackInSlot(slotIndex);
            if (cur.isEmpty()) {
                inventory.setStackInSlot(slotIndex, result);
            } else if (ItemStack.isSameItemSameComponents(cur, result)) {
                int space = cur.getMaxStackSize() - cur.getCount();
                int put = Math.min(space, result.getCount());
                cur.grow(put);
            }
        }
    }

    private boolean canFitOutputs(CentrifugeCylinderRecipe recipe) {
        List<FluidStack> fluidOutputs = recipe.getFluidOutputs();
        if (fluidOutputs.size() > OUTPUT_TANKS) return false;
        for (int i = 0; i < fluidOutputs.size(); i++) {
            FluidStack out = fluidOutputs.get(i);
            FluidStack cur = tanks[FIRST_OUTPUT_TANK + i].getFluid();
            if (cur.isEmpty()) {
                if (out.getAmount() > TANK_CAPACITY) return false;
            } else if (cur.getFluid() == out.getFluid()) {
                if (cur.getAmount() + out.getAmount() > TANK_CAPACITY) return false;
            } else {
                return false;
            }
        }

        List<ItemStack> itemOutputs = recipe.getItemOutputs();
        if (itemOutputs.size() > OUTPUT_SLOTS) return false;
        for (int i = 0; i < itemOutputs.size(); i++) {
            ItemStack result = itemOutputs.get(i);
            ItemStack cur = inventory.getStackInSlot(FIRST_OUTPUT_SLOT + i);
            if (cur.isEmpty()) {
                if (result.getCount() > result.getMaxStackSize()) return false;
            } else if (ItemStack.isSameItemSameComponents(cur, result)) {
                if (cur.getCount() + result.getCount() > cur.getMaxStackSize()) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean chargeFromBattery() {
        ItemStack battery = inventory.getStackInSlot(BATTERY_SLOT);
        if (battery.isEmpty() || energyStored >= MAX_ENERGY) return false;

        boolean changed = false;

        var storage = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage != null && storage.canExtract()) {
            int max = (int) Math.min(MAX_ENERGY - energyStored, RECEIVE_SPEED);
            int extracted = storage.extractEnergy(max, false);
            if (extracted > 0) {
                energyStored += extracted;
                changed = true;
            }
        }

        if (!changed && energyStored < MAX_ENERGY) {
            var provider = battery.getCapability(ModCapabilities.ENERGY_PROVIDER_ITEM);
            if (provider != null && provider.canExtract()) {
                long max = Math.min(MAX_ENERGY - energyStored, RECEIVE_SPEED);
                long extracted = provider.extractEnergy(max, false);
                if (extracted > 0) {
                    energyStored += extracted;
                    changed = true;
                }
            }
        }

        return changed;
    }

    // ===================== NBT / SYNC =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        CompoundTag tanksTag = new CompoundTag();
        for (int i = 0; i < TOTAL_TANKS; i++) {
            tanksTag.put("Tank" + i, tanks[i].writeToNBT(registries, new CompoundTag()));
        }
        tag.put("Tanks", tanksTag);
        tag.putLong("Energy", energyStored);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putString("StoredFluid", storedFluidId);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        if (tag.contains("Tanks")) {
            CompoundTag tanksTag = tag.getCompound("Tanks");
            for (int i = 0; i < TOTAL_TANKS; i++) {
                if (tanksTag.contains("Tank" + i)) {
                    tanks[i].readFromNBT(registries, tanksTag.getCompound("Tank" + i));
                }
            }
        }
        energyStored = tag.getLong("Energy");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        storedFluidId = tag.getString("StoredFluid");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
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
