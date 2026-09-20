package com.trd.block.entity.industrial.chemistry;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.ChemicalPlantPortMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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

    // Ротация стартового бака при drain(int): оба буфера доступны независимо друг от друга
    private int lastDrainedTank = 0;

    private LazyOptional<IFluidHandler> fluidHandler = LazyOptional.empty();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();

    public ChemicalPlantPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT_PORT_BE.get(), pos, state);
    }

    // ===================== РЕЦЕПТ ПОДКЛЮЧЁННОЙ КАМЕРЫ =====================

    private ChemicalPlantRecipe cachedRecipe;
    private long recipeCacheTime = Long.MIN_VALUE;

    /** Рецепт, выбранный у камеры, к которой подключён порт (null — рецепта нет или камера не подключена). Кэш на 1 тик. */
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

    /** Участвует ли жидкость во входах текущего рецепта камеры. */
    public boolean acceptsFluidForRecipe(FluidStack stack) {
        if (stack.isEmpty()) return false;
        ChemicalPlantRecipe recipe = getConnectedRecipe();
        if (recipe == null) return false;
        for (FluidStack input : recipe.getFluidInputs()) {
            if (input.getFluid() == stack.getFluid()) return true;
        }
        return false;
    }

    /** Совпадает ли предмет с входами текущего рецепта камеры. */
    public boolean acceptsItemForRecipe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ChemicalPlantRecipe recipe = getConnectedRecipe();
        if (recipe == null) return false;
        for (ItemStack input : recipe.getItemInputs()) {
            if (ItemStack.isSameItemSameTags(input, stack)) return true;
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
                // Заливать можно только в режиме вставщика и только жидкости из рецепта камеры
                if (mode != 0 || resource.isEmpty()) return 0;
                if (!acceptsFluidForRecipe(resource)) return 0;
                return internalFill(resource, action);
            }
            @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (mode != 1) return FluidStack.EMPTY;
                // Буферы независимы: опрашиваем оба, отдаём из того, где есть такая жидкость
                FluidStack drainedA = tankA.drain(resource, action);
                if (!drainedA.isEmpty()) return drainedA;
                return tankB.drain(resource, action);
            }
            @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                if (mode != 1 || maxDrain <= 0) return FluidStack.EMPTY;
                // Ротация стартового бака: труба видит содержимое обоих буферов,
                // второй не блокируется первым
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

    @Override
    public void onLoad() {
        super.onLoad();
        // Создаются ОДИН раз на жизнь BE и никогда не инвалидируются вручную.
        // Режим (mode) и рецепт камеры хендлер читает динамически при каждом вызове.
        fluidHandler = LazyOptional.of(this::createFluidHandler);
        itemCapability = LazyOptional.of(() -> itemHandler);
        // Принудительно уведомляем соседей о готовности capability после загрузки мира
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandler.invalidate();
        itemCapability.invalidate();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChemicalPlantPortBlockEntity be) {
        if (level.isClientSide) return;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos chamberPos = pos.relative(facing);
        BlockEntity beTarget = level.getBlockEntity(chamberPos);
        if (!(beTarget instanceof ChemicalPlantReactionChamberBlockEntity chamber)) return;

        Direction chamberSide = facing.getOpposite();
        IFluidHandler chamberFluid = chamber.getFluidHandler();
        IItemHandler chamberItem = chamber.getCapability(ForgeCapabilities.ITEM_HANDLER, chamberSide).orElse(null);

        boolean changed = false;

        if (be.mode == 0) { // INPUT
            ChemicalPlantRecipe recipe = be.getConnectedRecipe();

            // Жидкости гоним только если у камеры выбран рецепт (fill камеры отфильтрует лишнее).
            // Предметы передаём всегда — камера сама отклоняет чужие через isItemValid.
            if (recipe != null && chamberFluid != null) {
                changed |= transferFluid(be.tankA, chamberFluid, 200);
                changed |= transferFluid(be.tankB, chamberFluid, 200);
            }
            if (chamberItem != null) {
                for (int i = 0; i < ITEM_SLOTS; i++) {
                    ItemStack stack = be.itemHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;

                    // Камера определяет, в какой именно слот должен пойти этот предмет
                    int targetSlot = chamber.getSlotForItem(stack);
                    if (targetSlot < 0 || targetSlot >= ChemicalPlantReactionChamberBlockEntity.INPUT_SLOTS) continue;

                    ItemStack toInsert = stack.copy();
                    toInsert.setCount(1);
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

                // НЕ забираем жидкости если рецепта нет
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

                        FluidStack toDrain = available.copy();
                        toDrain.setAmount(Math.min(toDrain.getAmount(), 200));
                        FluidStack drained = chamberFluid.drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty()) {
                            int filled = be.internalFill(drained, IFluidHandler.FluidAction.SIMULATE);
                            if (filled > 0) {
                                FluidStack realDrain = drained.copy();
                                realDrain.setAmount(filled);
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

                    ItemStack extracted = chamberItem.extractItem(j, 1, false);
                    if (extracted.isEmpty()) continue;

                    ItemStack leftover = extracted;
                    for (int i = 0; i < ITEM_SLOTS; i++) {
                        leftover = be.itemHandler.insertItem(i, leftover, false);
                        if (leftover.isEmpty()) break;
                    }

                    if (!leftover.isEmpty()) {
                        chamberItem.insertItem(j, leftover, false); // вернуть если не влезло
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
        FluidStack toTransfer = from.getFluid().copy();
        toTransfer.setAmount(Math.min(toTransfer.getAmount(), maxAmount));
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
        // Capability НЕ инвалидируем: хендлер читает mode напрямую из поля BE,
        // а жидкостная сеть переопрашивает getCapability каждый тик.
        // Инвалидация лишь ломала кэшированные у соседей ссылки (порт "отваливался").
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getMode() { return mode; }
    public FluidTank getTankA() { return tankA; }
    public FluidTank getTankB() { return tankB; }
    public ItemStackHandler getItemHandler() { return itemHandler; }

    /** Очищает жидкостные баки и предметный инвентарь порта. Вызывается при смене рецепта. */
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

    /**
     * Проверяет, может ли порт принять жидкость данного типа.
     * Возвращает true если есть место в баке с этой жидкостью, или есть пустой бак.
     */
    public boolean canAcceptFluid(FluidStack stack) {
        if (stack.isEmpty()) return true;
        // Совпадающий бак с местом
        if (!tankA.isEmpty() && tankA.getFluid().getFluid() == stack.getFluid()
                && tankA.getFluidAmount() < tankA.getCapacity()) return true;
        if (!tankB.isEmpty() && tankB.getFluid().getFluid() == stack.getFluid()
                && tankB.getFluidAmount() < tankB.getCapacity()) return true;
        // Пустой бак
        if (tankA.isEmpty()) return true;
        if (tankB.isEmpty()) return true;
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag tankATag = new CompoundTag();
        tankA.writeToNBT(tankATag);
        tag.put("TankA", tankATag);
        CompoundTag tankBTag = new CompoundTag();
        tankB.writeToNBT(tankBTag);
        tag.put("TankB", tankBTag);
        tag.put("Items", itemHandler.serializeNBT());
        tag.putInt("Mode", mode);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TankA")) tankA.readFromNBT(tag.getCompound("TankA"));
        if (tag.contains("TankB")) tankB.readFromNBT(tag.getCompound("TankB"));
        if (tag.contains("Items")) itemHandler.deserializeNBT(tag.getCompound("Items"));
        mode = tag.getInt("Mode");
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
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (side == facing) return LazyOptional.empty();
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidHandler.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(cap, side);
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
