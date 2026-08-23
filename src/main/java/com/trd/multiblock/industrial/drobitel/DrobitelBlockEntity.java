package com.trd.multiblock.industrial.drobitel;

import com.trd.api.rotation.Rotational;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.MillstoneBlockEntity;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.item.ModItems;
import com.trd.menu.industrial.DrobitelMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

public class DrobitelBlockEntity extends KineticNodeBlockEntity implements MenuProvider {

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 21;
    public static final int BLADE_SLOTS = 2;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS + BLADE_SLOTS;
    public static final int MAX_PROGRESS = 60; // 3 секунды
    public static final ResourceKey<DamageType> CRUSHER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("trd", "crusher"));
    private int networkConnected = 0;
    public boolean isOverstressed = false;
    public boolean isTooSlow = false;
    private final ItemStackHandler inventory = new ItemStackHandler(INPUT_SLOTS + OUTPUT_SLOTS + 2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                updateBladeData();
                com.trd.api.rotation.KineticNetwork net = com.trd.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level).getNetworkFor(worldPosition);
                if (net != null) {
                    net.requestRecalculation();
                }
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < INPUT_SLOTS) {
                return !stack.is(ModItems.BLADE.get());
            }
            if (slot < INPUT_SLOTS + OUTPUT_SLOTS) return false;
            return stack.is(ModItems.BLADE.get());
        }
    };

    private final ContainerData data = new SimpleContainerData(9) {
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                case 2 -> blade1Durability = value;
                case 3 -> blade2Durability = value;
                case 4 -> hasBlade1 = value;
                case 5 -> hasBlade2 = value;
                case 6 -> networkConnected = value;
                case 7 -> isOverstressed = (value == 1);
                case 8 -> isTooSlow = (value == 1);
            }
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> blade1Durability;
                case 3 -> blade2Durability;
                case 4 -> hasBlade1;
                case 5 -> hasBlade2;
                case 6 -> networkConnected;
                case 7 -> isOverstressed ? 1 : 0;
                case 8 -> isTooSlow ? 1 : 0;
                default -> 0;
            };
        }
    };

    private int progress = 0;
    private int maxProgress = MAX_PROGRESS;
    private int blade1Durability = 0;
    private int blade2Durability = 0;
    private int hasBlade1 = 0;
    private int hasBlade2 = 0;
    
    public int getHasBlade1() { return hasBlade1; }
    public int getHasBlade2() { return hasBlade2; }

    private final LazyOptional<IItemHandler> internalHandler = LazyOptional.of(() -> inventory);

    public static final Map<Item, List<ItemStack>> RECIPES = new HashMap<>();

    public static void addRecipe(Item input, ItemStack... outputs) {
        if (outputs.length == 0) return;
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty()) list.add(stack.copy());
        }
        if (!list.isEmpty()) RECIPES.put(input, list);
    }

    public static void addRecipe(Item input, Item output, int count) {
        addRecipe(input, new ItemStack(output, count));
    }

    public static void copyMillstoneRecipes() {
        for (var entry : MillstoneBlockEntity.RECIPES.entrySet()) {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack s : entry.getValue().outputs()) out.add(s.copy());
            RECIPES.put(entry.getKey(), out);
        }
    }

    public DrobitelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DROBITEL_BE.get(), pos, state);
    }

    // ===================== КИНЕТИКА =====================

    @Override
    public long getMaxTorqueTolerance() { return Long.MAX_VALUE; }

    @Override
    public long getMaxTorque() { return Long.MAX_VALUE; }

    @Override
    public double getInertiaContribution() { return 20.0; }

    @Override
    public long getMaxSpeed() { return 2500L; }

    @Override
    public long getTorque() { return 0L; }

    @Override
    public boolean isSource() { return false; }

    @Override
    public long getConsumedTorque() {
        if (hasBlade1 == 0 || hasBlade2 == 0) return 0;
        long activeSlots = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                activeSlots++;
            }
        }
        return activeSlots * 50L;
    }

    @Override
    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (state.hasProperty(DrobitelBlock.FACING)) {
            Direction facing = state.getValue(DrobitelBlock.FACING);
            return new Direction[] { facing, facing.getOpposite() };
        }
        return new Direction[0];
    }
    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (neighbor instanceof com.trd.multiblock.system.MultiblockPartEntity part) {
            return part.getPartRole() == com.trd.multiblock.system.PartRole.KINETIC_PORT
                    && part.getControllerPos() != null
                    && part.getControllerPos().equals(myPos);
        }
        return false;
    }
    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        List<BlockPos> list = new ArrayList<>();
        BlockState state = getBlockState();
        if (state.hasProperty(DrobitelBlock.FACING)) {
            Direction facing = state.getValue(DrobitelBlock.FACING);
            list.add(myPos.relative(facing));
            list.add(myPos.relative(facing.getOpposite()));
        }
        return list;
    }

    @Override
    public long getVisualSpeed() {
        if (this.isOverstressed || this.isTooSlow) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(DrobitelBlock.FACING)) return this.speed;
        Direction facing = state.getValue(DrobitelBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    // ===================== ЛОГИКА =====================

    private final LazyOptional<IItemHandler> externalHandler = LazyOptional.of(() -> new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= 0 && slot < INPUT_SLOTS && !stack.isEmpty()) {
                int bestSlot = -1;
                int bestCount = Integer.MAX_VALUE;

                // Сначала ищем пустой слот для равномерного распределения
                for (int i = 0; i < INPUT_SLOTS; i++) {
                    if (inventory.getStackInSlot(i).isEmpty()) {
                        bestSlot = i;
                        break;
                    }
                }

                // Если пустых нет, стакаем с наименьшим стаком
                if (bestSlot == -1) {
                    for (int i = 0; i < INPUT_SLOTS; i++) {
                        ItemStack existing = inventory.getStackInSlot(i);
                        if (ItemStack.isSameItemSameTags(existing, stack)) {
                            int count = existing.getCount();
                            if (count < existing.getMaxStackSize() && count < bestCount) {
                                bestCount = count;
                                bestSlot = i;
                            }
                        }
                    }
                }

                if (bestSlot != -1) {
                    return inventory.insertItem(bestSlot, stack, simulate);
                }
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < INPUT_SLOTS) {
                return ItemStack.EMPTY;
            }

            int bladeSlot1 = INPUT_SLOTS + OUTPUT_SLOTS;
            int bladeSlot2 = INPUT_SLOTS + OUTPUT_SLOTS + 1;
            if (slot == bladeSlot1 || slot == bladeSlot2) {
                ItemStack blade = inventory.getStackInSlot(slot);
                if (!blade.isEmpty()) {
                    int remaining = blade.getMaxDamage() - blade.getDamageValue();
                    if (remaining >= 3) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    });

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        internalHandler.invalidate();
        externalHandler.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // Строго блокируем любой доступ к инвентарю через сам блок контроллера!
            // Все конвейеры должны работать только через Dummy-блоки с ролью CARGO_PORT.
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    public LazyOptional<net.minecraftforge.items.IItemHandler> getCargoPortCapability() {
        return externalHandler;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrobitelBlockEntity be) {
        boolean changed = false;
        be.updateBladeData();

        int newConnected = Math.abs(be.getSpeed()) > 0 ? 1 : 0;
        if (newConnected != be.networkConnected) {
            be.networkConnected = newConnected;
            changed = true;
        }

        long absSpeed = Math.abs(be.getSpeed());
        boolean wasOverstressed = be.isOverstressed;
        boolean wasTooSlow = be.isTooSlow;
        
        be.isOverstressed = false;
        be.isTooSlow = false;

        if (absSpeed > 0 && be.hasBlade1 == 1 && be.hasBlade2 == 1) {
            long minSpeed = 60;
            long maxSpeed = 120;
            long minLimit = 30;
            long maxLimit = 144;
            
            if (absSpeed < minLimit) {
                be.isTooSlow = true;
            } else if (absSpeed > maxLimit) {
                be.isOverstressed = true;
            } else {
                float bladeMultiplier = (absSpeed < minSpeed || absSpeed > maxSpeed) ? 0.5f : 1.0f;
                float baseMultiplier = absSpeed / 100.0f;
                float totalEfficiency = baseMultiplier * bladeMultiplier;
                if (totalEfficiency <= 0.05f) totalEfficiency = 0.05f;
                
                be.maxProgress = (int)(60.0f / totalEfficiency);
                if (be.maxProgress < 1) be.maxProgress = 1;
            }
        }

        if (wasOverstressed != be.isOverstressed || wasTooSlow != be.isTooSlow) {
            changed = true;
        }

        if (!be.isOverstressed && !be.isTooSlow && be.canProcess()) {
            be.progress++;

            if (be.progress % 10 == 0) {
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.MINECART_RIDING,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 0.6f);
            }

            if (be.progress >= be.maxProgress) {
                be.finishProcessing();
                be.progress = 0;
                changed = true;
            }
        } else {
            if (be.progress > 0) {
                be.progress = 0;
                changed = true;
            }
        }

        if (!be.isTooSlow && !be.isOverstressed && be.hasBlade1 == 1 && be.hasBlade2 == 1 && absSpeed > 0) {
            AABB area = new AABB(pos).inflate(1.0, 1.0, 1.0).move(0, 1.5, 0);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, area);
            for (Entity entity : entities) {
                if (entity instanceof ItemEntity itemEntity) {
                    double dx = (pos.getX() + 0.5) - itemEntity.getX();
                    double dz = (pos.getZ() + 0.5) - itemEntity.getZ();
                    
                    if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6 && itemEntity.getY() <= pos.getY() + 0.55) {
                        ItemStack stack = itemEntity.getItem();
                        IItemHandler handler = be.externalHandler.orElse(null);
                        ItemStack remainder = (handler != null) ? net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, stack, false) : stack;
                        
                        if (remainder.isEmpty()) {
                            itemEntity.discard();
                        } else {
                            itemEntity.setItem(remainder);
                        }
                    }
                } else if (entity instanceof LivingEntity livingEntity) {
                    double dx = (pos.getX() + 0.5) - livingEntity.getX();
                    double dz = (pos.getZ() + 0.5) - livingEntity.getZ();
                    
                    if (Math.abs(dx) < 1.2 && Math.abs(dz) < 1.2 && livingEntity.getY() >= pos.getY()) {
                        livingEntity.setDeltaMovement(livingEntity.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, -0.1, 0));
                        
                        livingEntity.hurt(new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, new net.minecraft.resources.ResourceLocation("trd", "crusher")))), 5.0f);
                    }
                }
            }
        }

        if (!be.isTooSlow && !be.isOverstressed && be.hasBlade1 == 1 && be.hasBlade2 == 1 && absSpeed > 0 && be.canProcess()) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                for (int i = 0; i < INPUT_SLOTS; i++) {
                    ItemStack input = be.inventory.getStackInSlot(i);
                    if (!input.isEmpty()) {
                        if (level.random.nextFloat() < 0.2f) {
                            double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
                            double py = pos.getY() + 1.4;
                            double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
                            
                            double vx = (level.random.nextDouble() - 0.5) * 0.05;
                            double vy = level.random.nextDouble() * 0.2;
                            double vz = (level.random.nextDouble() - 0.5) * 0.05;
                            
                            serverLevel.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, input), px, py, pz, 1, vx, vy, vz, 0.05);
                        }
                    }
                }
            }
        }

        if (changed || be.progress > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void updateBladeData() {
        int slot1 = INPUT_SLOTS + OUTPUT_SLOTS;
        int slot2 = INPUT_SLOTS + OUTPUT_SLOTS + 1;

        ItemStack b1 = inventory.getStackInSlot(slot1);
        ItemStack b2 = inventory.getStackInSlot(slot2);

        hasBlade1 = !b1.isEmpty() ? 1 : 0;
        hasBlade2 = !b2.isEmpty() ? 1 : 0;
        blade1Durability = hasBlade1 == 1 ? b1.getMaxDamage() - b1.getDamageValue() : 0;
        blade2Durability = hasBlade2 == 1 ? b2.getMaxDamage() - b2.getDamageValue() : 0;
    }

    private boolean canProcess() {
        if (hasBlade1 == 0 || hasBlade2 == 0) return false;
        if (Math.abs(getSpeed()) < 1) return false; // <-- нужно вращение

        boolean hasInput = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack input = inventory.getStackInSlot(i);
            if (!input.isEmpty()) {
                hasInput = true;
                List<ItemStack> results = getResults(input);
                if (!results.isEmpty()) {
                    for (ItemStack result : results) {
                        if (!canInsertResult(result)) {
                            return false;
                        }
                    }
                }
            }
        }
        return hasInput;
    }

    private List<ItemStack> getResults(ItemStack input) {
        if (input.isEmpty()) return List.of();
        List<ItemStack> result = RECIPES.get(input.getItem());
        if (result == null) {
            return List.of(new ItemStack(ModItems.TRASH.get()));
        }
        return result.stream().map(ItemStack::copy).toList();
    }

    private boolean canInsertResult(ItemStack result) {
        if (result.isEmpty()) return true;
        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void finishProcessing() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack input = inventory.getStackInSlot(i);
            if (input.isEmpty()) continue;

            List<ItemStack> results = getResults(input);
            if (results.isEmpty()) continue;

            input.shrink(1);
            for (ItemStack result : results) {
                insertResult(result.copy());
            }
        }

        damageBlade(INPUT_SLOTS + OUTPUT_SLOTS);
        damageBlade(INPUT_SLOTS + OUTPUT_SLOTS + 1);
    }

    private void insertResult(ItemStack result) {
        if (result.isEmpty()) return;

        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
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

        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                inventory.setStackInSlot(i, result);
                return;
            }
        }

        if (!result.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), result);
        }
    }

    private void damageBlade(int slot) {
        ItemStack blade = inventory.getStackInSlot(slot);
        if (blade.isEmpty() || !blade.is(ModItems.BLADE.get())) return;
        blade.hurt(1, level.random, null);
        if (blade.getDamageValue() >= blade.getMaxDamage()) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public InteractionResult handleScrewdriver(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) {
            return InteractionResult.sidedSuccess(level != null && level.isClientSide);
        }

        int slot1 = INPUT_SLOTS + OUTPUT_SLOTS;
        int slot2 = INPUT_SLOTS + OUTPUT_SLOTS + 1;

        ItemStack toExtract = ItemStack.EMPTY;
        int extractSlot = -1;

        if (!inventory.getStackInSlot(slot2).isEmpty()) {
            toExtract = inventory.getStackInSlot(slot2);
            extractSlot = slot2;
        } else if (!inventory.getStackInSlot(slot1).isEmpty()) {
            toExtract = inventory.getStackInSlot(slot1);
            extractSlot = slot1;
        }

        if (!toExtract.isEmpty()) {
            inventory.setStackInSlot(extractSlot, ItemStack.EMPTY);
            if (!player.getInventory().add(toExtract)) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), toExtract);
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public InteractionResult handleBladeInsertion(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) {
            return InteractionResult.sidedSuccess(level != null && level.isClientSide);
        }

        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.BLADE.get())) return InteractionResult.PASS;

        int slot1 = INPUT_SLOTS + OUTPUT_SLOTS;
        int slot2 = INPUT_SLOTS + OUTPUT_SLOTS + 1;

        if (inventory.getStackInSlot(slot1).isEmpty()) {
            inventory.setStackInSlot(slot1, held.split(1));
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return InteractionResult.CONSUME;
        } else if (inventory.getStackInSlot(slot2).isEmpty()) {
            inventory.setStackInSlot(slot2, held.split(1));
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putBoolean("IsOverstressed", isOverstressed);
        tag.putBoolean("IsTooSlow", isTooSlow);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        progress = tag.getInt("Progress");
        isOverstressed = tag.getBoolean("IsOverstressed");
        isTooSlow = tag.getBoolean("IsTooSlow");
        updateBladeData();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }



    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.drobitel");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DrobitelMenu(id, inv, this, data);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getData() {
        return data;
    }
}
