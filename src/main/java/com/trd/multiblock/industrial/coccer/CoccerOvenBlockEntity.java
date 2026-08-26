package com.trd.multiblock.industrial.coccer;

import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.CoccerOvenMenu;
import com.trd.multiblock.industrial.heaters.HeaterBlockEntity;
import com.trd.multiblock.system.IFluidTankProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CoccerOvenBlockEntity extends BlockEntity implements MenuProvider, IFluidTankProvider {

    public static final int MAX_TEMP = 2700;
    public static final int TANK_CAPACITY = 32000;
    private static final float BURN_MIN_TEMP = 300.0F;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == 0;
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
    };

    private float temperature = 0;
    private int progress = 0;
    private int maxProgress = 0;
    private int requiredTemp = 0;
    private boolean isProcessing = false;
    private CoccerOvenRecipe currentRecipe = null;
    private ItemStack inputSnapshot = ItemStack.EMPTY;
    /** Сколько тиков дым идёт после окончания рецепта (~3 секунды) */
    public static final int SMOKE_TAIL_TICKS = 60;
    private int smokeTicks = 0; // Хвост дыма (синхронизируется)

    private final LazyOptional<IItemHandler> fullHandler = LazyOptional.of(() -> inventory);
    private final LazyOptional<IItemHandler> automationHandler = LazyOptional.of(() -> new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0) return stack;
            return inventory.insertItem(0, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1) return ItemStack.EMPTY;
            return inventory.extractItem(1, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
    });
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> new IFluidHandler() {
        @Override public int getTanks() { return fluidTank.getTanks(); }
        @Override public FluidStack getFluidInTank(int tank) { return fluidTank.getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return fluidTank.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return fluidTank.isFluidValid(tank, stack); }

        // Запрещаем влив извне — бак только для выхода продукта рецепта
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return fluidTank.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return fluidTank.drain(maxDrain, action);
        }
    });

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) temperature;
                case 1 -> progress;
                case 2 -> maxProgress;
                case 3 -> requiredTemp;
                case 4 -> isProcessing ? 1 : 0;
                case 5 -> fluidTank.getFluidAmount();
                case 6 -> fluidTank.getCapacity();
                case 7 -> currentRecipe != null ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> temperature = value;
                case 1 -> progress = value;
                case 2 -> maxProgress = value;
                case 3 -> requiredTemp = value;
                case 4 -> isProcessing = value == 1;
            }
        }
        @Override public int getCount() { return 8; }
    };

    public CoccerOvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COCCER_OVEN_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }
    public FluidTank getFluidTank() { return fluidTank; }
    public ContainerData getContainerData() { return data; }
    public float getTemperature() { return temperature; }
    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }
    public int getRequiredTemp() { return requiredTemp; }
    public boolean isProcessing() { return isProcessing; }

    /** Остаток хвоста дыма после окончания рецепта (тики) */
    public int getSmokeTicks() { return smokeTicks; }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fullHandler.invalidate();
        automationHandler.invalidate();
        fluidHandler.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return side == null ? fullHandler.cast() : automationHandler.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public LazyOptional<IFluidHandler> getFluidHandlerCapability() {
        return fluidHandler;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoccerOvenBlockEntity be) {
        be.pickupThrownItems(level, pos);
        be.burnEntitiesInRecess(level, pos);

        // === ДЫМ ===
        // Спавнится клиентски в CoccerOvenBlock.animateTick как у ванильного костра
        // (сигнальный дым — высокий столб из центра над крышей мультиблока).
        // Пока идёт рецепт — таймер обновляется, после его завершения дым
        // продолжает идти ещё SMOKE_TAIL_TICKS тиков
        if (be.currentRecipe != null) {
            be.smokeTicks = SMOKE_TAIL_TICKS;
        } else if (be.smokeTicks > 0) {
            be.smokeTicks--;
        }

        // === ТЕПЛООБМЕН ===
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            float transfer = (heater.getTemperature() - be.temperature) / 10f + 0.5f;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            float baseCooling = (be.temperature * be.temperature) / 512000f;
            if (baseCooling < 0.1f && be.temperature > 0) baseCooling = 0.1f;
            int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
            float cooling = Math.max(0.1f, baseCooling + thermalNoise);
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // === ЛОГИКА РЕЦЕПТА ===
        ItemStack input = be.inventory.getStackInSlot(0);

        if (input.isEmpty()) {
            be.resetRecipe();
        } else {
            if (be.currentRecipe == null || !ItemStack.isSameItemSameTags(be.inputSnapshot, input)) {
                be.resetRecipe();
                be.currentRecipe = CoccerOvenRecipeRegistry.findRecipe(input.getItem());
                if (be.currentRecipe != null) {
                    be.inputSnapshot = input.copy();
                    be.requiredTemp = be.currentRecipe.getRequiredTemp();
                    be.maxProgress = be.currentRecipe.getBaseTicks();
                }
            }

            if (be.currentRecipe != null) {
                ItemStack output = be.inventory.getStackInSlot(1);
                CoccerOvenRecipe recipe = be.currentRecipe;

                boolean canOutputItem = !recipe.hasItemOutput() ||
                        (output.isEmpty() || (ItemStack.isSameItemSameTags(output, recipe.getOutputItem())
                                && output.getCount() + recipe.getOutputItem().getCount() <= output.getMaxStackSize()));

                boolean canOutputFluid = !recipe.hasFluidOutput() ||
                        be.fluidTank.fill(recipe.getOutputFluid(), IFluidHandler.FluidAction.SIMULATE) == recipe.getOutputFluid().getAmount();

                if (canOutputItem && canOutputFluid) {
                    if (be.temperature >= be.requiredTemp) {
                        be.isProcessing = true;
                        float multiplier = Math.min(2.0f, be.temperature / (float) be.requiredTemp);
                        be.progress += multiplier;

                        if (be.progress >= be.maxProgress) {
                            input.shrink(1);
                            if (recipe.hasItemOutput()) {
                                if (output.isEmpty()) {
                                    be.inventory.setStackInSlot(1, recipe.getOutputItem().copy());
                                } else {
                                    output.grow(recipe.getOutputItem().getCount());
                                }
                            }
                            if (recipe.hasFluidOutput()) {
                                be.fluidTank.fill(recipe.getOutputFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                            }
                            be.resetRecipe();
                        }
                    } else {
                        be.isProcessing = false;
                    }
                } else {
                    be.isProcessing = false;
                }
            }
        }

        if (be.isProcessing || be.temperature > 0 || be.progress > 0 || be.smokeTicks > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void resetRecipe() {
        this.currentRecipe = null;
        this.progress = 0;
        this.maxProgress = 0;
        this.requiredTemp = 0;
        this.isProcessing = false;
        this.inputSnapshot = ItemStack.EMPTY;
    }

    private void pickupThrownItems(Level level, BlockPos pos) {
        IItemHandler handler = automationHandler.orElse(null);
        if (handler == null) return;

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos.above()));
        for (ItemEntity itemEntity : items) {
            if (itemEntity.getY() > pos.getY() + 1.5D) continue;
            if (Math.abs(itemEntity.getX() - (pos.getX() + 0.5D)) >= 0.5D
                    || Math.abs(itemEntity.getZ() - (pos.getZ() + 0.5D)) >= 0.5D) continue;

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, itemEntity.getItem(), false);
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
    }

    private void burnEntitiesInRecess(Level level, BlockPos pos) {
        if (temperature < BURN_MIN_TEMP || level.getGameTime() % 20L != 0L) return;

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()))) {
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            living.setSecondsOnFire(2);
            living.hurt(level.damageSources().inFire(), 2.0F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("FluidTank", fluidTank.writeToNBT(new CompoundTag()));
        tag.putFloat("Temperature", temperature);
        tag.putInt("SmokeTicks", smokeTicks);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putInt("RequiredTemp", requiredTemp);
        tag.putBoolean("IsProcessing", isProcessing);
        if (currentRecipe != null) {
            tag.putString("RecipeInput", ForgeRegistries.ITEMS.getKey(currentRecipe.getInput()).toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        fluidTank.readFromNBT(tag.getCompound("FluidTank"));
        temperature = tag.getFloat("Temperature");
        smokeTicks = tag.getInt("SmokeTicks");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        requiredTemp = tag.getInt("RequiredTemp");
        isProcessing = tag.getBoolean("IsProcessing");
        if (tag.contains("RecipeInput")) {
            ResourceLocation id = new ResourceLocation(tag.getString("RecipeInput"));
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                currentRecipe = CoccerOvenRecipeRegistry.findRecipe(item);
            }
        }
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.coccer_oven");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CoccerOvenMenu(id, inv, this, data);
    }
}