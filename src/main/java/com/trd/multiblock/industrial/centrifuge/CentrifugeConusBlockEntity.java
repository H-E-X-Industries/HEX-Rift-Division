package com.trd.multiblock.industrial.centrifuge;

import com.trd.block.entity.ModBlockEntities;
import com.trd.item.energy.EnergyCellItem;
import com.trd.item.energy.ModBatteryItem;
import com.trd.menu.industrial.CentrifugeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CentrifugeConusBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int FIRST_OUTPUT_SLOT = 1;
    public static final int OUTPUT_SLOTS = 6;
    public static final int BATTERY_SLOT = 7;
    public static final int TOTAL_SLOTS = 8;

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
            if (slot == INPUT_SLOT) return CentrifugeRecipes.hasRecipe(stack);
            if (slot == BATTERY_SLOT) return isBattery(stack);
            return false;
        }
    };

    private long energyStored = 0L;
    private int progress = 0;
    private int maxProgress = 0;
    private double jeCarry = 0.0;
    private CentrifugeRecipe currentRecipe = null;

    private final LazyOptional<IItemHandler> selfHandler = LazyOptional.of(() -> inventory);

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
        public int getCount() {
            return 4;
        }
    };

    public CentrifugeConusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE_CONUS_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    public long getEnergyStored() { return energyStored; }

    public void addEnergy(long amount) {
        energyStored = Math.max(0, Math.min(MAX_ENERGY, energyStored + amount));
        setChanged();
    }

    public long getMaxEnergy() { return MAX_ENERGY; }

    public int getProgress() { return progress; }

    public int getMaxProgress() { return maxProgress; }

    @Nullable
    public CentrifugeRecipe getCurrentRecipe() { return currentRecipe; }

    private static boolean isBattery(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).isPresent()
                || stack.getItem() instanceof ModBatteryItem
                || stack.getItem() instanceof EnergyCellItem;
    }

    // ===================== ТИК =====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeConusBlockEntity be) {
        boolean changed = be.chargeFromBattery();

        CentrifugeRecipe recipe = CentrifugeRecipes.findMatching(be.inventory.getStackInSlot(INPUT_SLOT));
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
        }

        if (changed || be.progress > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void finishProcessing(CentrifugeRecipe recipe) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (!input.isEmpty()) {
            input.shrink(recipe.getInput().getCount());
        }

        for (List<ItemStack> group : groupOutputs(recipe.getOutputs())) {
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

    private boolean canFitOutputs(CentrifugeRecipe recipe) {
        ItemStack[] sim = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            sim[i] = inventory.getStackInSlot(FIRST_OUTPUT_SLOT + i).copy();
        }
        for (List<ItemStack> group : groupOutputs(recipe.getOutputs())) {
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

    // ===================== CAPABILITIES =====================

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) return selfHandler.cast();
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        selfHandler.invalidate();
    }

    // ===================== NBT / SYNC =====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putLong("Energy", energyStored);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        energyStored = tag.getLong("Energy");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
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
        return Component.translatable("block.trd.centrifuge_conus");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CentrifugeMenu(id, inv, this, data);
    }
}
