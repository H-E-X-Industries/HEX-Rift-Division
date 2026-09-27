package com.trd.multiblock.industrial.stanok;

import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.item.ModItems;
import com.trd.menu.industrial.StanokMenu;
import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BlockEntity для кинетического станка (stanok).
 * Наследует KineticNodeBlockEntity, реализует MenuProvider.
 *
 * Слоты инвентаря:
 *  0–5   — входные (6 слотов, сетка 3×2)
 *  6–11  — выходные (6 слотов, сетка 3×2)
 *  12    — слот насадки (CarriageItem)
 *
 * ContainerData (indices):
 *  0 — progress
 *  1 — maxProgress
 *  2 — carriageType (ordinal, -1 = нет)
 *  3 — speedStatus (0=OK, 1=too slow, 2=too fast, 3=no mats/no space)
 *  4 — recipeIdHash (для синхронизации на клиент)
 *  5 — hasInputs (1 если есть нужный материал, 0 если нет)
 */
public class StanokBlockEntity extends KineticNodeBlockEntity implements MenuProvider {

    // ────── Константы слотов ──────
    public static final int INPUT_SLOTS   = 6;
    public static final int OUTPUT_SLOTS  = 6;
    public static final int CARRIAGE_SLOT = 12;
    public static final int TOTAL_SLOTS   = 13;

    // ────── Кинетика ──────
    @Override public long getMaxTorqueTolerance() { return Long.MAX_VALUE; }
    @Override public long getMaxTorque()           { return Long.MAX_VALUE; }
    @Override public double getInertiaContribution(){ return 25.0; }
    @Override public long getMaxSpeed()             { return 4000L; }
    @Override public long getTorque()               { return 0L;    }
    @Override public boolean isSource()             { return false; }

    // ────── Данные кинетической сети для HUD (синхронизируются на клиент) ──────
    private long networkTorque = 0;
    private long networkConsumedTorque = 0;

    public long getNetworkTorque() { return networkTorque; }
    public long getNetworkConsumedTorque() { return networkConsumedTorque; }

    @Override
    public long getConsumedTorque() {
        StanokRecipe recipe = getCurrentRecipe();
        if (recipe == null || !hasCarriage() || !isCrafting) return 0L;
        return recipe.getConsumedTorque();
    }

    /**
     * Станок принимает кинетику с запада и востока (ось X).
     * Контроллер смотрит на север, порты % — слева (WEST) и справа (EAST).
     */
    private Direction getFacing() {
        if (getBlockState().hasProperty(StanokBlock.FACING)) {
            return getBlockState().getValue(StanokBlock.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    public Direction[] getPropagationDirections() {
        Direction facing = getFacing();
        return new Direction[]{facing.getCounterClockWise(), facing.getClockWise()};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        return List.of(
                getWestPortPos(),
                getEastPortPos()
        );
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos,
                                          com.trd.api.rotation.Rotational neighbor) {
        if (neighbor instanceof com.trd.multiblock.system.MultiblockPartEntity part) {
            return part.isKineticPort()
                    && part.getControllerPos() != null
                    && part.getControllerPos().equals(myPos);
        }
        return false;
    }

    @Override
    public long getVisualSpeed() {
        return this.speed;
    }

    // ────── Инвентарь ──────
    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                if (slot == CARRIAGE_SLOT) {
                    com.trd.api.rotation.KineticNetwork net =
                            KineticNetworkManager.get((ServerLevel) level)
                                    .getNetworkFor(worldPosition);
                    if (net != null) net.requestRecalculation();
                }
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < INPUT_SLOTS)  return !isCarriageItem(stack);
            if (slot < INPUT_SLOTS + OUTPUT_SLOTS) return false;
            return isCarriageItem(stack);
        }
    };

    private static boolean isCarriageItem(ItemStack stack) {
        return stack.is(ModItems.PRESS_CARRIAGE.get())
                || stack.is(ModItems.WIRE_CARRIAGE.get())
                || stack.is(ModItems.FREZA_CARRIAGE.get());
    }

    // ────── Состояние логики ──────
    private int progress    = 0;
    private int maxProgress = 60;

    /** 0=OK, 1=too slow, 2=too fast, 3=no material/no space */
    public int speedStatus = 0;

    /** ID выбранного рецепта (null = не выбран) */
    private ResourceLocation currentRecipeId = null;

    /**
     * Счётчик операций пресса для логики разгона.
     * 0 = первая операция (60 тиков), 4+ = 20 тиков.
     */
    private int pressOperationCount = 0;

    /** Флаг активности процесса крафта (для звуков и анимаций) */
    private boolean isCrafting = false;

    // ────── ContainerData ──────
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> getCarriageTypeOrdinal();
                case 3 -> speedStatus;
                case 4 -> currentRecipeId != null ? currentRecipeId.hashCode() : 0;
                case 5 -> {
                    StanokRecipe r = getCurrentRecipe();
                    yield (r != null && hasRequiredInputs(r)) ? 1 : 0;
                }
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress    = value;
                case 1 -> maxProgress = value;
                case 3 -> speedStatus = value;
            }
        }

        @Override
        public int getCount() { return 6; }
    };

    // ────── Cargo Port Handler ──────
    private final IItemHandler cargoPortHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Разрешаем извлекать ТОЛЬКО из выходных слотов
            if (slot >= INPUT_SLOTS && slot < INPUT_SLOTS + OUTPUT_SLOTS) {
                return inventory.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY; // Запрет извлечения из входных слотов и слота насадки
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };

    public IItemHandler getCargoPortCapability() {
        return cargoPortHandler;
    }

    public StanokBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STANOK_BE.get(), pos, state);
    }

    // ────── Геттеры ──────

    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData()         { return data; }
    public boolean isCrafting()            { return isCrafting; }
    public int getProgress()               { return progress; }
    public int getMaxProgress()            { return maxProgress; }

    @Nullable
    public ResourceLocation getCurrentRecipeId() { return currentRecipeId; }

    public void setCurrentRecipeId(@Nullable ResourceLocation id) {
        this.currentRecipeId = id;
        this.progress = 0;
        this.pressOperationCount = 0;
        StanokRecipe r = getCurrentRecipe();
        this.maxProgress = r != null ? r.getProcessTicks() : 60;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (level instanceof ServerLevel sl) {
                com.trd.api.rotation.KineticNetwork net =
                        KineticNetworkManager.get(sl).getNetworkFor(worldPosition);
                if (net != null) net.requestRecalculation();
            }
        }
    }

    @Nullable
    public StanokRecipe getCurrentRecipe() {
        if (currentRecipeId == null) return null;
        return StanokRecipeRegistry.getById(currentRecipeId);
    }

    public boolean hasCarriage() {
        return !inventory.getStackInSlot(CARRIAGE_SLOT).isEmpty();
    }

    @Nullable
    public CarriageType getCurrentCarriageType() {
        ItemStack stack = inventory.getStackInSlot(CARRIAGE_SLOT);
        if (stack.isEmpty()) return null;
        if (stack.is(ModItems.PRESS_CARRIAGE.get())) return CarriageType.PRESS;
        if (stack.is(ModItems.WIRE_CARRIAGE.get()))  return CarriageType.WIRE;
        if (stack.is(ModItems.FREZA_CARRIAGE.get())) return CarriageType.FREZA;
        return null;
    }

    private int getCarriageTypeOrdinal() {
        CarriageType t = getCurrentCarriageType();
        return t != null ? t.ordinal() : -1;
    }

    // ────── Клиентский тик (звуки) ──────

    public static void clientTick(Level level, BlockPos pos, BlockState state, StanokBlockEntity be) {
        if (level.isClientSide) {
            com.trd.client.sound.StanokSoundHandler.tick(be);
        }
    }

    // ────── Серверный тик ──────

    public static void serverTick(Level level, BlockPos pos, BlockState state, StanokBlockEntity be) {
        boolean changed = false;

        // Синхронизация момента кинетической сети на клиент для HUD
        com.trd.api.rotation.KineticNetwork net = KineticNetworkManager
                .get((ServerLevel) level)
                .getNetworkFor(be.worldPosition);
        if (net != null) {
            float absScale = Math.abs(be.networkScale);
            long newTorque = absScale > 0.001f ? (long) (net.getTotalTorque() / absScale) : net.getTotalTorque();
            long newConsumed = absScale > 0.001f ? (long) (net.getTotalConsumedTorque() / absScale) : net.getTotalConsumedTorque();
            if (newTorque != be.networkTorque || newConsumed != be.networkConsumedTorque) {
                be.networkTorque = newTorque;
                be.networkConsumedTorque = newConsumed;
                changed = true;
            }
        } else if (be.networkTorque != 0 || be.networkConsumedTorque != 0) {
            be.networkTorque = 0;
            be.networkConsumedTorque = 0;
            changed = true;
        }

        long absSpeed = Math.abs(be.getSpeed());

        // Обновить speedStatus: 0 = OK, 1 = слишком медленно/не готов, 2 = слишком быстро, 3 = нет материалов/места
        StanokRecipe recipe = be.getCurrentRecipe();
        CarriageType carriage = be.getCurrentCarriageType();
        int newStatus;
        if (recipe != null && carriage != null && recipe.getCarriageType() == carriage && absSpeed > 0) {
            long req = recipe.getRequiredRpm();
            long tolerance = (long)(req * 0.25);
            if (absSpeed < req - tolerance) {
                newStatus = 1; // слишком медленно
            } else if (absSpeed > req + tolerance) {
                newStatus = 2; // слишком быстро
            } else {
                newStatus = (be.hasRequiredInputs(recipe) && be.canFitOutputs(recipe)) ? 0 : 3;
            }
        } else if (absSpeed == 0 || recipe == null || carriage == null) {
            newStatus = 1; // не готов к крафту
        } else {
            newStatus = 1; // насадка не соответствует рецепту
        }

        if (newStatus != be.speedStatus) {
            be.speedStatus = newStatus;
            changed = true;
        }

        boolean crafting = be.canProcess();
        if (crafting != be.isCrafting) {
            be.isCrafting = crafting;
            changed = true;
            if (net != null) {
                net.requestRecalculation();
            }
        }

        // Обработка крафта
        if (be.canProcess()) {
            // Звук пресса: одиночный удар на половине такта операции (где слиток сменяется пластиной)
            if (carriage == CarriageType.PRESS && be.progress == be.maxProgress / 2) {
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        ModSounds.STANOK_PRESS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            // Звук фрезы: цикл в 4 секунды (80 тиков) во время процесса крафта
            if (carriage == CarriageType.FREZA && be.progress % 80 == 0) {
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        ModSounds.STANOK_FREZA.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }

            be.progress++;
            if (be.progress >= be.maxProgress) {
                be.finishProcessing();
                be.progress = 0;
                changed = true;
                // Разгон пресса
                if (carriage == CarriageType.PRESS) {
                    be.pressOperationCount++;
                    be.maxProgress = calcPressTime(be.pressOperationCount);
                }
            }
        } else {
            if (be.progress > 0) {
                be.progress = 0;
                changed = true;
            }
            // Сброс разгона пресса если слот пуст
            if (carriage == CarriageType.PRESS && !be.hasAnyInput()) {
                if (be.pressOperationCount != 0) {
                    be.pressOperationCount = 0;
                    be.maxProgress = 60;
                    changed = true;
                }
            }
        }

        if (changed || be.progress > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Вычислить время одной операции пресса (тиков) по счётчику завершённых операций.
     * Операция 0: 60 тиков (3 сек)
     * Операция 1: 50 тиков
     * Операция 2: 40 тиков
     * Операция 3: 30 тиков
     * Операция 4+: 20 тиков (1 сек)
     */
    private static int calcPressTime(int opCount) {
        return Math.max(20, 60 - opCount * 10);
    }

    private boolean canProcess() {
        if (!hasCarriage()) return false;
        StanokRecipe recipe = getCurrentRecipe();
        if (recipe == null) return false;
        CarriageType carriage = getCurrentCarriageType();
        if (carriage == null || recipe.getCarriageType() != carriage) return false;
        if (speedStatus != 0) return false;
        if (!hasRequiredInputs(recipe)) return false;
        return canFitOutputs(recipe);
    }

    private boolean hasAnyInput() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    public boolean hasRequiredInputsPublic(StanokRecipe recipe) {
        return hasRequiredInputs(recipe);
    }

    private boolean hasRequiredInputs(StanokRecipe recipe) {
        for (ItemStack required : recipe.getInputs()) {
            int needed = required.getCount();
            int found = 0;
            for (int i = 0; i < INPUT_SLOTS; i++) {
                ItemStack slot = inventory.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(slot, required)) {
                    found += slot.getCount();
                    if (found >= needed) break;
                }
            }
            if (found < needed) return false;
        }
        return true;
    }

    private boolean canFitOutputs(StanokRecipe recipe) {
        for (ItemStack out : recipe.getOutputs()) {
            if (!canInsertOutput(out)) return false;
        }
        return true;
    }

    private boolean canInsertOutput(ItemStack stack) {
        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void finishProcessing() {
        StanokRecipe recipe = getCurrentRecipe();
        if (recipe == null) return;

        // Снимаем входные предметы
        for (ItemStack required : recipe.getInputs()) {
            int toRemove = required.getCount();
            for (int i = 0; i < INPUT_SLOTS && toRemove > 0; i++) {
                ItemStack slot = inventory.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(slot, required)) {
                    int take = Math.min(toRemove, slot.getCount());
                    slot.shrink(take);
                    toRemove -= take;
                }
            }
        }

        // Добавляем выходные предметы
        for (ItemStack out : recipe.getOutputs()) {
            insertOutput(out.copy());
        }
    }

    private void insertOutput(ItemStack stack) {
        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int canAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (canAdd > 0) {
                    slot.grow(canAdd);
                    stack.shrink(canAdd);
                    if (stack.isEmpty()) return;
                }
            }
        }
        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                inventory.setStackInSlot(i, stack);
                return;
            }
        }
        if (!stack.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    // ────── NBT ──────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putInt("SpeedStatus", speedStatus);
        tag.putInt("PressOpCount", pressOperationCount);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putLong("NetTorque", this.networkTorque);
        tag.putLong("NetConsumedTorque", this.networkConsumedTorque);
        if (currentRecipeId != null) tag.putString("RecipeId", currentRecipeId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        progress            = tag.getInt("Progress");
        maxProgress         = tag.getInt("MaxProgress");
        speedStatus         = tag.getInt("SpeedStatus");
        pressOperationCount = tag.getInt("PressOpCount");
        isCrafting          = tag.getBoolean("IsCrafting");
        this.networkTorque  = tag.getLong("NetTorque");
        this.networkConsumedTorque = tag.getLong("NetConsumedTorque");
        if (tag.contains("RecipeId")) {
            currentRecipeId = ResourceLocation.parse(tag.getString("RecipeId"));
        } else {
            currentRecipeId = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) loadAdditional(tag, lookupProvider);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            com.trd.client.sound.StanokSoundHandler.stop(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            com.trd.client.sound.StanokSoundHandler.stop(worldPosition);
        }
    }

    // ────── MenuProvider ──────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.stanok");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StanokMenu(id, inv, this, data);
    }

    // ────── Кинетические методы для StanokBlock ──────

    public BlockPos getWestPortPos() {
        if (getBlockState().getBlock() instanceof StanokBlock sb) {
            return sb.getStructureHelper().getRotatedPos(worldPosition, new BlockPos(-1, 0, 0), getFacing());
        }
        return worldPosition.west();
    }

    public BlockPos getEastPortPos() {
        if (getBlockState().getBlock() instanceof StanokBlock sb) {
            return sb.getStructureHelper().getRotatedPos(worldPosition, new BlockPos(1, 0, 0), getFacing());
        }
        return worldPosition.east();
    }
}
