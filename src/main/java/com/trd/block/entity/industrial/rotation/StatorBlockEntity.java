package com.trd.block.entity.industrial.rotation;

import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyProvider;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.Rotational;
import com.trd.block.basic.industrial.rotation.StatorBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.industrial.energy.StatorCoilItem;
import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Статор — кинетический потребитель и электрический генератор.
 *
 * В кинетической сети:
 *   - Реализует Rotational с NodeRole.CONSUMER
 *   - Потребляет единицы момента из кинетической сети
 *
 * В электрической сети:
 *   - Реализует IEnergyProvider: генерирует JE пропорционально скорости вала с ротором
 */
public class StatorBlockEntity extends KineticNodeBlockEntity implements IEnergyProvider, IEnergyConnector {

    public static final long MAX_EXTRACT = 2000;

    private long energyStored = 0;
    private boolean wasFull = false;
    private long lastSyncedEnergy = -1;
    private int activelyGeneratingTicks = 0;
    private boolean wasGenerating = false;

    private final ItemStackHandler coilsInventory = new ItemStackHandler(12) {
        @Override
        protected void onContentsChanged(int slot) {
            long maxEn = getMaxEnergyDynamic();
            if (energyStored > maxEn) {
                energyStored = maxEn;
            }
            checkFullStateChange();
            requestKineticRecalculation();
            setChanged();
            syncToClient();
        }
    };

    public ItemStackHandler getCoilsInventory() {
        return coilsInventory;
    }

    public StatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATOR_BE.get(), pos, state);
    }

    // ===================== CAPABILITY ACCESSORS =====================

    public @Nullable IEnergyProvider getEnergyProvider(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    public @Nullable IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    // ===================== IEnergyConnector =====================
    @Override
    public boolean canConnectEnergy(@Nullable Direction side) {
        return true;
    }

    // ===================== IEnergyProvider =====================
    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getMaxEnergyStored() {
        return getMaxEnergyDynamic();
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0, Math.min(energy, getMaxEnergyDynamic()));
        checkFullStateChange();
    }

    @Override
    public long getProvideSpeed() {
        return MAX_EXTRACT;
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        long toExtract = Math.min(energyStored, Math.min(maxExtract, MAX_EXTRACT));
        if (!simulate) {
            energyStored -= toExtract;
            setChanged();
            if (toExtract > 0 && activelyGeneratingTicks <= 0) {
                activelyGeneratingTicks = 20;
                if (!wasGenerating) {
                    wasGenerating = true;
                    requestKineticRecalculation();
                }
            }
            checkFullStateChange();
        }
        return toExtract;
    }

    @Override
    public boolean canExtract() {
        return energyStored > 0;
    }

    // ===================== Rotational: КИНЕТИЧЕСКОЕ ПОТРЕБЛЕНИЕ =====================

    @Override
    public NodeRole getNodeRole() {
        return NodeRole.CONSUMER;
    }

    @Override
    public long getConsumedTorque() {
        if (!hasActiveRotor()) return 0L;

        long totalLoad = 0;
        for (int i = 0; i < 12; i++) {
            ItemStack stack = coilsInventory.getStackInSlot(i);
            if (stack.getItem() instanceof StatorCoilItem coil) {
                long load = coil.getBaseTorqueLoad();
                ItemStack opposite = coilsInventory.getStackInSlot((i + 6) % 12);
                if (opposite.isEmpty()) {
                    load = (long) (load * coil.getAsymmetryMultiplier());
                }
                totalLoad += load;
            }
        }
        if (totalLoad == 0) return 0L;

        // При выработке электричества или если буфер не заполнен — полная нагрузка на кинетическую сеть
        if (energyStored < getMaxEnergyDynamic() || activelyGeneratingTicks > 0 || wasGenerating) {
            return totalLoad;
        }

        // Вхолостую (буфер полон, энергия не вырабатывается) — минимальная нагрузка (10% трение)
        return Math.max(1L, (long) (totalLoad * 0.1));
    }

    private boolean hasActiveRotor() {
        if (level == null) return false;
        BlockState state = getBlockState();
        if (!state.hasProperty(StatorBlock.FACING) || !state.hasProperty(StatorBlock.AXIS)) return false;
        Direction facing = state.getValue(StatorBlock.FACING);
        Direction.Axis axis = state.getValue(StatorBlock.AXIS);
        BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
        BlockPos shaftPos = worldPosition.offset(holeOffset);
        if (!level.isLoaded(shaftPos)) return false;
        var be = level.getBlockEntity(shaftPos);
        return be instanceof ShaftBlockEntity shaft && shaft.hasRotor();
    }

    @Override
    public long getTorque() {
        return 0;
    }

    @Override
    public boolean isSource() {
        return false;
    }

    @Override
    public double getInertiaContribution() {
        return 0.2;
    }

    @Override
    public long getMaxTorqueTolerance() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxSpeed() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxTorque() {
        return Long.MAX_VALUE;
    }

    @Override
    public Direction[] getPropagationDirections() {
        return new Direction[0];
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        BlockState state = getBlockState();
        if (!state.hasProperty(StatorBlock.FACING) || !state.hasProperty(StatorBlock.AXIS)) return Collections.emptyList();
        Direction facing = state.getValue(StatorBlock.FACING);
        Direction.Axis axis = state.getValue(StatorBlock.AXIS);
        BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
        return List.of(myPos.offset(holeOffset));
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        BlockState state = getBlockState();
        if (!state.hasProperty(StatorBlock.FACING) || !state.hasProperty(StatorBlock.AXIS)) return false;
        Direction facing = state.getValue(StatorBlock.FACING);
        Direction.Axis axis = state.getValue(StatorBlock.AXIS);
        BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
        return neighborPos.equals(myPos.offset(holeOffset));
    }

    // ===================== TICK =====================

    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityTicker<T> createTicker() {
        return (level, pos, state, be) -> {
            if (!level.isClientSide && be instanceof StatorBlockEntity stator) {
                stator.tick();
            }
        };
    }

    private void tick() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        if (!state.hasProperty(StatorBlock.FACING) || !state.hasProperty(StatorBlock.AXIS)) return;

        Direction facing = state.getValue(StatorBlock.FACING);
        Direction.Axis axis = state.getValue(StatorBlock.AXIS);
        BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
        BlockPos shaftPos = worldPosition.offset(holeOffset);

        boolean isGeneratingNow = false;
        if (level.getBlockEntity(shaftPos) instanceof ShaftBlockEntity shaft) {
            if (shaft.hasRotor()) {
                long speed = Math.abs(shaft.getSpeed());
                long maxEn = getMaxEnergyDynamic();
                if (speed > 0 && energyStored < maxEn) {
                    float rotorEfficiency = 1.0f;
                    com.trd.api.rotation.RotorType rotorType = shaft.getRotorType();
                    if (rotorType != null) rotorEfficiency = rotorType.getEfficiency();

                    long totalConversion = 0;
                    for (int i = 0; i < 12; i++) {
                        ItemStack stack = coilsInventory.getStackInSlot(i);
                        if (stack.getItem() instanceof StatorCoilItem coil) {
                            totalConversion += coil.getEnergyConversionRate();
                        }
                    }

                    long generated = (long) ((speed * totalConversion * rotorEfficiency * 3.0f) / 40.0f);
                    if (generated > 0) {
                        energyStored = Math.min(maxEn, energyStored + generated);
                        setChanged();
                        activelyGeneratingTicks = 20;
                        isGeneratingNow = true;
                        checkFullStateChange();
                    }
                }
            }
        }

        if (!isGeneratingNow && activelyGeneratingTicks > 0) {
            activelyGeneratingTicks--;
        }

        boolean currentGenState = activelyGeneratingTicks > 0;
        if (currentGenState != wasGenerating) {
            wasGenerating = currentGenState;
            requestKineticRecalculation();
        }

        if (energyStored != lastSyncedEnergy && level.getGameTime() % 10 == 0) {
            lastSyncedEnergy = energyStored;
            syncToClient();
        }
    }

    public long getMaxEnergyDynamic() {
        long sum = 0;
        for (int i = 0; i < coilsInventory.getSlots(); i++) {
            ItemStack stack = coilsInventory.getStackInSlot(i);
            if (stack.getItem() instanceof StatorCoilItem coil) {
                sum += coil.getEnergyBuffer();
            }
        }
        return sum;
    }

    private void checkFullStateChange() {
        boolean isFull = energyStored >= getMaxEnergyDynamic();
        if (isFull != wasFull) {
            wasFull = isFull;
            requestKineticRecalculation();
        }
    }

    private void requestKineticRecalculation() {
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            if (state.hasProperty(StatorBlock.FACING) && state.hasProperty(StatorBlock.AXIS)) {
                Direction facing = state.getValue(StatorBlock.FACING);
                Direction.Axis axis = state.getValue(StatorBlock.AXIS);
                BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
                BlockPos shaftPos = worldPosition.offset(holeOffset);
                var net = KineticNetworkManager.get(serverLevel).getNetworkFor(shaftPos);
                if (net != null) {
                    net.requestRecalculation();
                }
            }
        }
    }

    // ===================== NBT =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("EnergyStored", energyStored);
        tag.putBoolean("WasFull", wasFull);
        tag.putInt("ActivelyGeneratingTicks", activelyGeneratingTicks);
        tag.putBoolean("WasGenerating", wasGenerating);
        tag.put("CoilsInventory", coilsInventory.serializeNBT(provider));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("EnergyStored");
        wasFull = tag.getBoolean("WasFull");
        activelyGeneratingTicks = tag.getInt("ActivelyGeneratingTicks");
        wasGenerating = tag.getBoolean("WasGenerating");
        if (tag.contains("CoilsInventory")) {
            coilsInventory.deserializeNBT(provider, tag.getCompound("CoilsInventory"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }
}
