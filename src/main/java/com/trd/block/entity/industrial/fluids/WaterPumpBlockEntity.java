package com.trd.block.entity.industrial.fluids;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.trd.block.entity.ModBlockEntities;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class WaterPumpBlockEntity extends BlockEntity {
    private final FluidTank waterTank = new FluidTank(4000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // Разрешаем заполнение только изнутри механизма
            if (action == FluidAction.EXECUTE && isInternalCall) {
                return super.fill(resource, action);
            }
            return 0; // Снаружи нельзя заливать жидкость в помпу
        }
    };
    
    private final IFluidHandler fluidHandler = waterTank;
    
    private int cachedWaterVolume = 0;
    private int handCrankTicks = 0;
    private int lastPumpedVolume = 0; // Для HUD
    private boolean isInternalCall = false;
    private float speed = 0f;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PUMP_BE.get(), pos, state);
    }

    public float getSpeed() {
        return speed;
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, WaterPumpBlockEntity pEntity) {
        // --- Client-side Visuals ---
        if (level.isClientSide) {
            float speedC = Math.abs(pEntity.getSpeed());
            if (speedC > 0) {
                float vBase = speedC * 0.4f;
                float eff = pEntity.cachedWaterVolume < 20 ? 0.0f : (pEntity.cachedWaterVolume / 1000.0f);
                eff = Math.min(eff, 1.0f);
                if (vBase * eff > 0) {
                    pEntity.lastPumpedVolume = Math.min((int) Math.ceil(vBase * eff), 300);
                } else {
                    pEntity.lastPumpedVolume = 0;
                }
            } else {
                pEntity.lastPumpedVolume = 0;
            }
            return;
        }

        // --- Server-side Logic ---
        // Десинхронизированный таймер (раз в 600 тиков / 30 сек)
        if ((level.getGameTime() + pos.getX() + pos.getZ()) % 600 == 0) {
            pEntity.scanWaterVolume();
        }

        float speed = Math.abs(pEntity.getSpeed());
        
        // Логика ручного привода
        if (speed == 0 && pEntity.handCrankTicks > 0) {
            speed = 32.0f; // Условная скорость от ручки
            pEntity.handCrankTicks--;
        }

        if (speed > 0) {
            float vBase = speed * 0.4f;
            float eff = pEntity.cachedWaterVolume < 20 ? 0.0f : (pEntity.cachedWaterVolume / 1000.0f);
            eff = Math.min(eff, 1.0f); // Ограничиваем эффективность до 100%
            
            int v = 0;
            if (vBase * eff > 0) {
                v = Math.min((int) Math.ceil(vBase * eff), 300);
            }
            pEntity.lastPumpedVolume = v;

            if (v > 0 && pEntity.waterTank.getSpace() > 0) {
                pEntity.isInternalCall = true;
                pEntity.waterTank.fill(new FluidStack(Fluids.WATER, v), IFluidHandler.FluidAction.EXECUTE);
                pEntity.isInternalCall = false;
            }
        } else {
            pEntity.lastPumpedVolume = 0;
        }
    }

    private void scanWaterVolume() {
        if (level == null) return;
        BlockPos startPos = this.getBlockPos().below(1);
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.add(startPos);
        visited.add(startPos);
        
        int volume = 0;
        
        while (!queue.isEmpty() && volume < 1000) {
            BlockPos current = queue.poll();
            
            if (level.getFluidState(current).is(Fluids.WATER) || level.getFluidState(current).is(Fluids.FLOWING_WATER)) {
                volume++;
                
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        
        int oldVolume = this.cachedWaterVolume;
        this.cachedWaterVolume = volume;
        
        if (oldVolume != this.cachedWaterVolume) {
            this.setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void crank() {
        this.handCrankTicks = 40;
    }

    public int getLastPumpedVolume() {
        return lastPumpedVolume;
    }

    public int getCachedWaterVolume() {
        return cachedWaterVolume;
    }

    // --- Kinetic API ---

    public long getMaxTorqueTolerance() {
        return Long.MAX_VALUE;
    }

    public long getMaxTorque() {
        return Long.MAX_VALUE;
    }

    public double getInertiaContribution() {
        return 10.0;
    }

    public long getMaxSpeed() {
        return 1000L;
    }

    public long getTorque() {
        return 0L;
    }

    public boolean isSource() {
        return false;
    }

    public long getConsumedTorque() {
        if (lastPumpedVolume > 0 && waterTank.getSpace() > 0) {
            return (long) (1 + (lastPumpedVolume * 0.05));
        }
        return 1L;
    }

    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) return (long)this.speed;
        Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return (long)-this.speed;
        }
        return (long)this.speed;
    }

    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            return new Direction[] { facing, facing.getOpposite() };
        }
        return new Direction[0];
    }

    public java.util.List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, BlockPos myPos) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        BlockState state = getBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            list.add(myPos.relative(facing));
            list.add(myPos.relative(facing.getOpposite()));
        }
        return list;
    }

    // --- Capabilities ---

    

    

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("WaterVolume", cachedWaterVolume);
        tag.putInt("HandCrankTicks", handCrankTicks);
        tag.put("WaterTank", waterTank.writeToNBT(provider, new CompoundTag()));
        tag.putInt("LastPumpedVolume", lastPumpedVolume);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.cachedWaterVolume = tag.getInt("WaterVolume");
        this.handCrankTicks = tag.getInt("HandCrankTicks");
        this.waterTank.readFromNBT(provider, tag.getCompound("WaterTank"));
        this.lastPumpedVolume = tag.getInt("LastPumpedVolume");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            scanWaterVolume();
        }
    }
}
