package com.trd.block.entity.industrial.chemistry;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.ChemicalPlantReactionChamberMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChemicalPlantReactionChamberBlockEntity extends BlockEntity implements MenuProvider {

    public static final int TANK_COUNT = 3;
    public static final int TANK_CAPACITY = 16000;
    public static final int INPUT_SLOTS = 3;
    public static final int OUTPUT_SLOTS = 3;

    private final FluidTank[] tanks = new FluidTank[TANK_COUNT];
    private final ItemStackHandler itemHandler = new ItemStackHandler(INPUT_SLOTS + OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < INPUT_SLOTS;
        }
    };

    private String currentRecipeId = "";
    private int progress = 0;
    private int maxProgress = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
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
        public int getCount() {
            return 2;
        }
    };

    private LazyOptional<IFluidHandler> fluidHandler = LazyOptional.empty();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();

    public ChemicalPlantReactionChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT_REACTION_CHAMBER_BE.get(), pos, state);
        for (int i = 0; i < TANK_COUNT; i++) {
            tanks[i] = new FluidTank(TANK_CAPACITY) {
                @Override
                protected void onContentsChanged() {
                    ChemicalPlantReactionChamberBlockEntity.this.setChanged();
                }
            };
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        fluidHandler = LazyOptional.of(() -> new IFluidHandler() {
            @Override
            public int getTanks() { return TANK_COUNT; }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                return tanks[tank].getFluid();
            }

            @Override
            public int getTankCapacity(int tank) {
                return tanks[tank].getCapacity();
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                if (currentRecipeId.isEmpty()) return false;
                ChemicalPlantRecipe recipe = ChemicalPlantRecipeRegistry.getById(new ResourceLocation(currentRecipeId));
                if (recipe == null) return false;
                for (FluidStack input : recipe.getFluidInputs()) {
                    if (input.getFluid() == stack.getFluid()) return true;
                }
                for (FluidStack output : recipe.getFluidOutputs()) {
                    if (output.getFluid() == stack.getFluid()) return true;
                }
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return 0;
                int totalFilled = 0;
                FluidStack remaining = resource.copy();
                for (int i = 0; i < TANK_COUNT && remaining.getAmount() > 0; i++) {
                    if (isFluidValid(i, remaining)) {
                        int filled = tanks[i].fill(remaining, action);
                        totalFilled += filled;
                        remaining.shrink(filled);
                    }
                }
                return totalFilled;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return FluidStack.EMPTY;
                int totalDrained = 0;
                FluidStack result = FluidStack.EMPTY;
                for (int i = 0; i < TANK_COUNT && totalDrained < resource.getAmount(); i++) {
                    FluidStack toDrain = resource.copy();
                    toDrain.setAmount(resource.getAmount() - totalDrained);
                    FluidStack drained = tanks[i].drain(toDrain, action);
                    if (!drained.isEmpty()) {
                        if (result.isEmpty()) result = drained;
                        else result.grow(drained.getAmount());
                        totalDrained += drained.getAmount();
                    }
                }
                return result;
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                for (int i = 0; i < TANK_COUNT; i++) {
                    if (!tanks[i].isEmpty()) {
                        return tanks[i].drain(maxDrain, action);
                    }
                }
                return FluidStack.EMPTY;
            }
        });
        itemCapability = LazyOptional.of(() -> new IItemHandler() {
            @Override
            public int getSlots() { return itemHandler.getSlots(); }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return itemHandler.getStackInSlot(slot);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (slot >= INPUT_SLOTS) return stack;
                return itemHandler.insertItem(slot, stack, simulate);
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot < INPUT_SLOTS) return ItemStack.EMPTY;
                return itemHandler.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) { return itemHandler.getSlotLimit(slot); }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return itemHandler.isItemValid(slot, stack);
            }
        });
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandler.invalidate();
        itemCapability.invalidate();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChemicalPlantReactionChamberBlockEntity be) {
        if (level.isClientSide) return;

        if (be.currentRecipeId.isEmpty()) {
            be.progress = 0;
            be.maxProgress = 0;
            return;
        }

        ChemicalPlantRecipe recipe = ChemicalPlantRecipeRegistry.getById(new ResourceLocation(be.currentRecipeId));
        if (recipe == null) {
            be.currentRecipeId = "";
            be.progress = 0;
            be.setChanged();
            return;
        }

        be.maxProgress = recipe.getProcessTime();

        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < TANK_COUNT; i++) fluids.add(be.tanks[i].getFluid());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) items.add(be.itemHandler.getStackInSlot(i));

        if (recipe.matches(fluids, items)) {
            List<FluidStack> outFluids = new ArrayList<>();
            for (int i = 0; i < TANK_COUNT; i++) outFluids.add(be.tanks[i].getFluid());
            List<ItemStack> outItems = new ArrayList<>();
            for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) outItems.add(be.itemHandler.getStackInSlot(i));

            if (!recipe.canFitOutputs(outFluids, outItems, TANK_COUNT, OUTPUT_SLOTS)) {
                return;
            }

            be.progress++;
            if (be.progress >= be.maxProgress) {
                be.consumeInputs(recipe);
                be.produceOutputs(recipe);
                be.progress = 0;
            }
            be.setChanged();
        } else {
            if (be.progress > 0) {
                be.progress = 0;
                be.setChanged();
            }
        }
    }

    private void consumeInputs(ChemicalPlantRecipe recipe) {
        for (FluidStack required : recipe.getFluidInputs()) {
            int remaining = required.getAmount();
            for (int i = 0; i < TANK_COUNT && remaining > 0; i++) {
                FluidStack inTank = tanks[i].getFluid();
                if (inTank.getFluid() == required.getFluid()) {
                    int toDrain = Math.min(remaining, inTank.getAmount());
                    tanks[i].drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    remaining -= toDrain;
                }
            }
        }
        for (ItemStack required : recipe.getItemInputs()) {
            int remaining = required.getCount();
            for (int i = 0; i < INPUT_SLOTS && remaining > 0; i++) {
                ItemStack slot = itemHandler.getStackInSlot(i);
                if (ItemStack.isSameItemSameTags(slot, required)) {
                    int toRemove = Math.min(remaining, slot.getCount());
                    slot.shrink(toRemove);
                    remaining -= toRemove;
                }
            }
        }
    }

    private void produceOutputs(ChemicalPlantRecipe recipe) {
        for (FluidStack output : recipe.getFluidOutputs()) {
            int remaining = output.getAmount();
            for (int i = 0; i < TANK_COUNT && remaining > 0; i++) {
                FluidStack inTank = tanks[i].getFluid();
                if (!inTank.isEmpty() && inTank.getFluid() == output.getFluid()) {
                    int filled = tanks[i].fill(new FluidStack(output.getFluid(), remaining), IFluidHandler.FluidAction.EXECUTE);
                    remaining -= filled;
                }
            }
            for (int i = 0; i < TANK_COUNT && remaining > 0; i++) {
                if (tanks[i].isEmpty()) {
                    int filled = tanks[i].fill(new FluidStack(output.getFluid(), remaining), IFluidHandler.FluidAction.EXECUTE);
                    remaining -= filled;
                }
            }
        }
        for (ItemStack output : recipe.getItemOutputs()) {
            int remaining = output.getCount();
            for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS && remaining > 0; i++) {
                ItemStack slot = itemHandler.getStackInSlot(i);
                if (slot.isEmpty()) {
                    itemHandler.setStackInSlot(i, output.copy());
                    remaining = 0;
                } else if (ItemStack.isSameItemSameTags(slot, output) && slot.getCount() + remaining <= slot.getMaxStackSize()) {
                    slot.grow(remaining);
                    remaining = 0;
                }
            }
        }
    }

    public void setRecipe(@Nullable ResourceLocation recipeId) {
        this.currentRecipeId = recipeId != null ? recipeId.toString() : "";
        this.progress = 0;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    public ResourceLocation getCurrentRecipeId() {
        return currentRecipeId.isEmpty() ? null : new ResourceLocation(currentRecipeId);
    }

    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }
    public FluidTank[] getTanks() { return tanks; }
    public ItemStackHandler getItemHandler() { return itemHandler; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int i = 0; i < TANK_COUNT; i++) {
            CompoundTag tankTag = new CompoundTag();
            tanks[i].writeToNBT(tankTag);
            tag.put("Tank" + i, tankTag);
        }
        tag.put("Items", itemHandler.serializeNBT());
        tag.putString("Recipe", currentRecipeId);
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < TANK_COUNT; i++) {
            if (tag.contains("Tank" + i)) {
                tanks[i].readFromNBT(tag.getCompound("Tank" + i));
            }
        }
        if (tag.contains("Items")) {
            itemHandler.deserializeNBT(tag.getCompound("Items"));
        }
        currentRecipeId = tag.getString("Recipe");
        progress = tag.getInt("Progress");
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

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidHandler.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.chemical_plant_reaction_chamber");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChemicalPlantReactionChamberMenu(id, inv, this, data);
    }
}