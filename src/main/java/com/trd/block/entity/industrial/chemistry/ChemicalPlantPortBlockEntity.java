package com.trd.block.entity.industrial.chemistry;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.ChemicalPlantPortMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChemicalPlantPortBlockEntity extends BlockEntity implements MenuProvider {

    public static final int TANK_CAPACITY = 8000;
    public static final int ITEM_SLOTS = 9;

    private final FluidTank tankA = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    private final FluidTank tankB = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    private final ItemStackHandler itemHandler = new ItemStackHandler(ITEM_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int mode = 0; // 0 = input (вставщик), 1 = output (извлекатель)
    private int lastDrainedTank = 0;

    private final IFluidHandler portFluidHandler;

    public ChemicalPlantPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT_PORT_BE.get(), pos, state);
        this.portFluidHandler = createFluidHandler();
    }

    // ===================== РЕЦЕПТ ПОДКЛЮЧЁННОЙ КАМЕРЫ =====================

    private ChemicalPlantRecipe cachedRecipe;
    private long recipeCacheTime = Long.MIN_VALUE;

    @Nullable
    public ChemicalPlantRecipe getConnectedRecipe() {
        if (level == null) return null;
        long now = level.getGameTime();
        if (recipeCacheTime == now) return cachedRecipe;
        recipeCacheTime = now;

        cachedRecipe = computeConnectedRecipe();
        return cachedRecipe;
    }

    @Nullable
    private ChemicalPlantRecipe computeConnectedRecipe() {
        BlockState state = getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return null;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (level.getBlockEntity(worldPosition.relative(facing)) instanceof ChemicalPlantReactionChamberBlockEntity chamber) {
            ResourceLocation id = chamber.getCurrentRecipeId();
            return id == null ? null : ChemicalPlantRecipeRegistry.getById(id);
        }
        return null;
    }

    public boolean acceptsFluidForRecipe(FluidStack stack) {
        if (stack.isEmpty()) return false;
        ChemicalPlantRecipe recipe = getConnectedRecipe();
        if (recipe == null) return false;
        for (FluidStack input : recipe.getFluidInputs()) {
            if (input.getFluid() == stack.getFluid()) return true;
        }
        return false;
    }

    public boolean acceptsItemForRecipe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ChemicalPlantRecipe recipe = getConnectedRecipe();
        if (recipe == null) return false;
        for (ItemStack input : recipe.getItemInputs()) {
            if (ItemStack.isSameItemSameComponents(input, stack)) return true;
        }
        return false;
    }

    // ===================== CAPABILITIES =====================

    private IFluidHandler createFluidHandler() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 2; }
            @Override public @NotNull FluidStack getFluidInTank(int tank) {
                return tank == 0 ? tankA.getFluid() : tankB.getFluid();
            }
            @Override public int getTankCapacity(int tank) {
                return tank == 0 ? tankA.getCapacity() : tankB.getCapacity();
            }
            @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                if (mode != 0 || !acceptsFluidForRecipe(stack)) return false;
                FluidTank target = tank == 0 ? tankA : tankB;
                return target.isEmpty() || target.getFluid().getFluid() == stack.getFluid();
            }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (mode != 0 || resource.isEmpty()) return 0;
                if (!acceptsFluidForRecipe(resource)) return 0;
                return internalFill(resource, action);
            }
            @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (mode != 1) return FluidStack.EMPTY;
                FluidStack drainedA = tankA.drain(resource, action);
                if (!drainedA.isEmpty()) return drainedA;
                return tankB.drain(resource, action);
            }
            @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                if (mode != 1 || maxDrain <= 0) return FluidStack.EMPTY;
                for (int k = 0; k < 2; k++) {
                    int index = (lastDrainedTank + k) % 2;
                    FluidTank tank = index == 0 ? tankA : tankB;
                    if (!tank.isEmpty()) {
                        lastDrainedTank = (index + 1) % 2;
                        return tank.drain(maxDrain, action);
                    }
                }
                return FluidStack.EMPTY;
            }
        };
    }

    public IFluidHandler getFluidHandler() {
        return portFluidHandler;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        if (side == facing) return null; // Front face touches chamber
        return portFluidHandler;
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        if (side == facing) return null;
        return itemHandler;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChemicalPlantPortBlockEntity be) {
        if (level.isClientSide) return;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos chamberPos = pos.relative(facing);
        BlockEntity beTarget = level.getBlockEntity(chamberPos);
        if (!(beTarget instanceof ChemicalPlantReactionChamberBlockEntity chamber)) return;

        IFluidHandler chamberFluid = chamber.getInternalFluidHandler();
        IItemHandler chamberItem = chamber.getExposedItemHandler();

        boolean changed = false;

        if (be.mode == 0) { // INPUT
            ChemicalPlantRecipe recipe = be.getConnectedRecipe();

            if (recipe != null && chamberFluid != null) {
                changed |= transferFluid(be.tankA, chamberFluid, 200);
                changed |= transferFluid(be.tankB, chamberFluid, 200);
            }
            if (chamberItem != null) {
                for (int i = 0; i < ITEM_SLOTS; i++) {
                    ItemStack stack = be.itemHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;

                    int targetSlot = chamber.getSlotForItem(stack);
                    if (targetSlot < 0 || targetSlot >= ChemicalPlantReactionChamberBlockEntity.INPUT_SLOTS) continue;

                    ItemStack toInsert = stack.copyWithCount(1);
                    ItemStack remainder = chamberItem.insertItem(targetSlot, toInsert, false);
                    if (remainder.isEmpty()) {
                        be.itemHandler.extractItem(i, 1, false);
                        changed = true;
                    }
                }
            }
        } else { // OUTPUT
            if (chamberFluid != null) {
                ChemicalPlantRecipe recipe = be.getConnectedRecipe();

                if (recipe != null) {
                    for (int i = 0; i < ChemicalPlantReactionChamberBlockEntity.TANK_COUNT; i++) {
                        FluidStack available = chamberFluid.getFluidInTank(i);
                        if (available.isEmpty()) continue;

                        boolean isInput = false;
                        for (FluidStack input : recipe.getFluidInputs()) {
                            if (input.getFluid() == available.getFluid()) {
                                isInput = true;
                                break;
                            }
                        }
                        if (isInput) continue;

                        FluidStack toDrain = available.copyWithAmount(Math.min(available.getAmount(), 200));
                        FluidStack drained = chamberFluid.drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty()) {
                            int filled = be.internalFill(drained, IFluidHandler.FluidAction.SIMULATE);
                            if (filled > 0) {
                                FluidStack realDrain = drained.copyWithAmount(filled);
                                FluidStack real = chamberFluid.drain(realDrain, IFluidHandler.FluidAction.EXECUTE);
                                be.internalFill(real, IFluidHandler.FluidAction.EXECUTE);
                                changed = true;
                            }
                        }
                    }
                }
            }

            if (chamberItem != null) {
                for (int j = ChemicalPlantReactionChamberBlockEntity.INPUT_SLOTS;
                     j < ChemicalPlantReactionChamberBlockEntity.INPUT_SLOTS + ChemicalPlantReactionChamberBlockEntity.OUTPUT_SLOTS; j++) {

                    ItemStack stack = chamberItem.getStackInSlot(j);
                    if (stack.isEmpty()) continue;

                    ItemStack singleItem = stack.copyWithCount(1);
                    boolean canInsert = false;
                    for (int i = 0; i < ITEM_SLOTS; i++) {
                        if (be.itemHandler.insertItem(i, singleItem, true).isEmpty()) {
                            canInsert = true;
                            break;
                        }
                    }
                    if (!canInsert) continue;

                    ItemStack extracted = chamberItem.extractItem(j, 1, false);
                    if (extracted.isEmpty()) continue;

                    for (int i = 0; i < ITEM_SLOTS; i++) {
                        extracted = be.itemHandler.insertItem(i, extracted, false);
                        if (extracted.isEmpty()) break;
                    }

                    if (!extracted.isEmpty()) {
                        chamber.getRawItemHandler().insertItem(j, extracted, false);
                    } else {
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            be.setChanged();
            if (level != null) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private static boolean transferFluid(FluidTank from, IFluidHandler to, int maxAmount) {
        if (from.isEmpty()) return false;
        FluidStack toTransfer = from.getFluid().copyWithAmount(Math.min(from.getFluidAmount(), maxAmount));
        int filled = to.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (filled > 0) {
            FluidStack drained = from.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return true;
            }
        }
        return false;
    }

    private int internalFill(FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty()) return 0;
        if (!tankA.isEmpty() && tankA.getFluid().getFluid() == resource.getFluid()) {
            return tankA.fill(resource, action);
        }
        if (!tankB.isEmpty() && tankB.getFluid().getFluid() == resource.getFluid()) {
            return tankB.fill(resource, action);
        }
        if (tankA.isEmpty()) return tankA.fill(resource, action);
        if (tankB.isEmpty()) return tankB.fill(resource, action);
        return 0;
    }

    public void setMode(int mode) {
        this.mode = mode;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getMode() { return mode; }
    public FluidTank getTankA() { return tankA; }
    public FluidTank getTankB() { return tankB; }
    public ItemStackHandler getItemHandler() { return itemHandler; }

    public void clearBuffers() {
        tankA.setFluid(FluidStack.EMPTY);
        tankB.setFluid(FluidStack.EMPTY);
        for (int i = 0; i < ITEM_SLOTS; i++) {
            itemHandler.setStackInSlot(i, ItemStack.EMPTY);
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean canAcceptFluid(FluidStack stack) {
        if (stack.isEmpty()) return true;
        if (!tankA.isEmpty() && tankA.getFluid().getFluid() == stack.getFluid()
                && tankA.getFluidAmount() < tankA.getCapacity()) return true;
        if (!tankB.isEmpty() && tankB.getFluid().getFluid() == stack.getFluid()
                && tankB.getFluidAmount() < tankB.getCapacity()) return true;
        if (tankA.isEmpty()) return true;
        if (tankB.isEmpty()) return true;
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        CompoundTag tankATag = new CompoundTag();
        tankA.writeToNBT(provider, tankATag);
        tag.put("TankA", tankATag);
        CompoundTag tankBTag = new CompoundTag();
        tankB.writeToNBT(provider, tankBTag);
        tag.put("TankB", tankBTag);
        tag.put("Items", itemHandler.serializeNBT(provider));
        tag.putInt("Mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("TankA")) tankA.readFromNBT(provider, tag.getCompound("TankA"));
        if (tag.contains("TankB")) tankB.readFromNBT(provider, tag.getCompound("TankB"));
        if (tag.contains("Items")) itemHandler.deserializeNBT(provider, tag.getCompound("Items"));
        mode = tag.getInt("Mode");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.chemical_plant_port");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChemicalPlantPortMenu(id, inv, this);
    }
}
