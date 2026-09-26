package com.trd.multiblock.industrial.ccmachine;

import com.trd.api.fluids.ModFluids;
import com.trd.api.metallurgy.system.IMetalReceiver;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.api.metallurgy.system.recipe.MoldRecipe;
import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.block.entity.ModBlockEntities;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.menu.industrial.CCMachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CCMachineBlockEntity extends BlockEntity implements MenuProvider, IMetalReceiver {

    public static final int METAL_CAPACITY = MetalUnits2.UNITS_PER_BLOCK; // 81
    public static final int TANK_CAPACITY = 64000;
    public static final int WATER_PER_9_UNITS = 1000;
    public static final int CAST_TIME_INSTANT = 1;
    public static final int CAST_TIME_PARTIAL = 100;

    public static final int SLOT_MOLD = 0;
    public static final int SLOT_OUTPUT_START = 1;
    public static final int SLOT_OUTPUT_COUNT = 6;
    public static final int INVENTORY_SIZE = 1 + SLOT_OUTPUT_COUNT; // 7

    private Metal currentMetal = null;
    private int storedUnits = 0;
    private int castProgress = 0;
    private int castTargetTime = 0;

    private final FluidTank waterTank = new FluidTank(TANK_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid().is(FluidTags.WATER);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final FluidTank steamTank = new FluidTank(TANK_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == ModFluids.LOW_PRESSURE_STEAM_SOURCE.get();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_MOLD) {
                return MoldRecipeRegistry.hasRecipe(stack.getItem());
            }
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final ContainerData data = new SimpleContainerData(9) {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> storedUnits;
                case 1 -> METAL_CAPACITY;
                case 2 -> waterTank.getFluidAmount();
                case 3 -> TANK_CAPACITY;
                case 4 -> steamTank.getFluidAmount();
                case 5 -> TANK_CAPACITY;
                case 6 -> castProgress;
                case 7 -> castTargetTime > 0 ? castTargetTime
                        : (storedUnits >= METAL_CAPACITY ? CAST_TIME_INSTANT : CAST_TIME_PARTIAL);
                case 8 -> currentMetal != null ? currentMetal.getColor() : -1;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }
    };

    public CCMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CC_MACHINE_BE.get(), pos, state);
    }

    public ContainerData getData() { return data; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CCMachineBlockEntity be) {
        be.tickCasting();
    }

    private void tickCasting() {
        if (level == null || level.isClientSide) return;

        ItemStack mold = inventory.getStackInSlot(SLOT_MOLD);
        MoldRecipe recipe = mold.isEmpty() ? null : MoldRecipeRegistry.getRecipe(mold.getItem());

        if (this.currentMetal == null || this.storedUnits <= 0 || recipe == null) {
            resetCast();
            return;
        }

        int required = recipe.getRequiredUnits();
        int items = this.storedUnits / required;
        if (items <= 0) {
            resetCast();
            return;
        }
        int batchUnits = items * required;

        ItemStack result = recipe.createOutput(this.currentMetal);
        if (result.isEmpty() || !canOutput(result, items)) {
            resetCast();
            return;
        }

        int waterNeeded = Math.max(1, (batchUnits * WATER_PER_9_UNITS) / MetalUnits2.UNITS_PER_INGOT);

        if (waterTank.getFluidAmount() < waterNeeded
                || (steamTank.getCapacity() - steamTank.getFluidAmount()) < waterNeeded) {
            resetCast();
            return;
        }

        this.castTargetTime = (this.storedUnits >= METAL_CAPACITY) ? CAST_TIME_INSTANT : CAST_TIME_PARTIAL;

        this.castProgress++;
        if (this.castProgress >= this.castTargetTime) {
            finishCast(result, batchUnits, waterNeeded, items);
        } else if (this.castProgress % 40 == 0) {
            syncChanged();
        }
    }

    private void resetCast() {
        if (this.castProgress != 0 || this.castTargetTime != 0) {
            this.castProgress = 0;
            this.castTargetTime = 0;
            syncChanged();
        }
    }

    private void finishCast(ItemStack result, int batchUnits, int waterNeeded, int items) {
        this.storedUnits -= batchUnits;
        if (this.storedUnits <= 0) {
            this.storedUnits = 0;
            this.currentMetal = null;
        }
        waterTank.drain(waterNeeded, IFluidHandler.FluidAction.EXECUTE);
        steamTank.fill(new FluidStack(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), waterNeeded), IFluidHandler.FluidAction.EXECUTE);
        addToOutput(result, items);

        this.castProgress = 0;
        this.castTargetTime = 0;

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    8, 0.25, 0.1, 0.25, 0.03);
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
        }
        syncChanged();
    }

    private boolean canOutput(ItemStack result, int count) {
        int space = 0;
        for (int i = SLOT_OUTPUT_START; i < INVENTORY_SIZE; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) {
                space += result.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, result)) {
                space += slot.getMaxStackSize() - slot.getCount();
            }
            if (space >= count) return true;
        }
        return false;
    }

    private void addToOutput(ItemStack result, int count) {
        int remaining = count;
        for (int i = SLOT_OUTPUT_START; i < INVENTORY_SIZE && remaining > 0; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) {
                ItemStack toAdd = result.copy();
                int add = Math.min(remaining, result.getMaxStackSize());
                toAdd.setCount(add);
                inventory.setStackInSlot(i, toAdd);
                remaining -= add;
            } else if (ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() < slot.getMaxStackSize()) {
                int add = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                slot.grow(add);
                remaining -= add;
            }
        }
    }

    // ============ IMetalReceiver ============

    @Override
    public boolean canAcceptMetal(Metal metal) {
        if (storedUnits > 0 && currentMetal != null && !currentMetal.equals(metal)) return false;
        return storedUnits < METAL_CAPACITY;
    }

    @Override
    public int addMetal(Metal metal, int amount) {
        if (storedUnits == 0) {
            this.currentMetal = metal;
        } else if (!this.currentMetal.equals(metal)) {
            return 0;
        }
        int toAdd = Math.min(amount, METAL_CAPACITY - storedUnits);
        if (toAdd > 0) {
            this.storedUnits += toAdd;
            syncChanged();
        }
        return toAdd;
    }

    @Override
    public int getRemainingCapacity() {
        return METAL_CAPACITY - storedUnits;
    }

    @Override
    public Metal getCurrentMetal() {
        return currentMetal;
    }

    @Override
    public float getFillLevel() {
        return (float) storedUnits / METAL_CAPACITY;
    }

    public List<ItemStack> dumpMetalAsSlag() {
        List<ItemStack> slagItems = new ArrayList<>();
        if (currentMetal != null && storedUnits > 0) {
            ItemStack slag = SlagItem.createSlag(currentMetal, storedUnits);
            if (!HotItemHandler.isHot(slag)) {
                CustomData.update(DataComponents.CUSTOM_DATA, slag, tag -> {
                    tag.putFloat("HotTime", SlagItem.BASE_COOLING_TIME);
                    tag.putInt("HotTimeMax", SlagItem.BASE_COOLING_TIME);
                });
            }
            slagItems.add(slag);
            this.currentMetal = null;
            this.storedUnits = 0;
            syncChanged();
        }
        return slagItems;
    }

    public int getStoredUnits() { return storedUnits; }
    public int getMetalCapacity() { return METAL_CAPACITY; }
    public Metal getStoredMetal() { return currentMetal; }
    public FluidTank getWaterTank() { return waterTank; }
    public FluidTank getSteamTank() { return steamTank; }
    public ItemStackHandler getInventory() { return inventory; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.trd.cc_machine.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CCMachineMenu(id, inv, this, data);
    }

    private void syncChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Комбинированный флюид-хендлер: fill = только вода, drain = только пар н.д.
    private final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() { return 2; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? waterTank.getFluid() : steamTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? waterTank.getCapacity() : steamTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 ? waterTank.isFluidValid(stack) : steamTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return waterTank.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return steamTank.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            return steamTank.drain(maxDrain, action);
        }
    };

    private final IItemHandler automationHandler = new IItemHandler() {
        @Override
        public int getSlots() { return inventory.getSlots(); }

        @Override
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_MOLD) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < SLOT_OUTPUT_START || slot >= INVENTORY_SIZE) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
    };

    public IItemHandler getAutomationItemHandler() {
        return automationHandler;
    }

    public IFluidHandler getFluidHandler() {
        return combinedFluidHandler;
    }

    public IFluidHandler getPortFluidHandler(BlockPos portPos, @Nullable Direction side) {
        if (side != null) {
            Direction allowed = getAllowedLateralSide(portPos);
            if (side != allowed) {
                return null;
            }
        }
        return combinedFluidHandler;
    }

    private Direction getAllowedLateralSide(BlockPos portPos) {
        Direction facing = Direction.NORTH;
        if (getBlockState().hasProperty(CCMachineBlock.FACING)) {
            facing = getBlockState().getValue(CCMachineBlock.FACING);
        }
        int dx = portPos.getX() - worldPosition.getX();
        int dz = portPos.getZ() - worldPosition.getZ();
        Direction cw = facing.getClockWise();
        int dot = dx * cw.getStepX() + dz * cw.getStepZ();
        return dot > 0 ? cw : facing.getCounterClockWise();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("StoredUnits", storedUnits);
        tag.putInt("CastProgress", castProgress);
        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        if (tag.contains("WaterTank")) {
            waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        }
        if (tag.contains("SteamTank")) {
            steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        }
        storedUnits = tag.getInt("StoredUnits");
        castProgress = tag.getInt("CastProgress");
        if (tag.contains("MetalId")) {
            ResourceLocation id = ResourceLocation.parse(tag.getString("MetalId"));
            this.currentMetal = MetallurgyRegistry.get(id).orElse(null);
        } else {
            this.currentMetal = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries);
    }
}
