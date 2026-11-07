package com.trd.multiblock.industrial.steam_engine;

import com.trd.api.rotation.KineticNetwork;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.Rotational;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.IMultiblockFluidHandler;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SteamEngineBlockEntity extends KineticNodeBlockEntity implements IMultiblockFluidHandler {

    public final FluidTank steamTank = new FluidTank(16_000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == com.trd.api.fluids.ModFluids.STEAM_SOURCE.get();
        }
    };
    public final FluidTank lowPressureSteamTank = new FluidTank(64_000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == com.trd.api.fluids.ModFluids.LOW_PRESSURE_STEAM_SOURCE.get();
        }
    };

    /**
     * УНИВЕРСАЛЬНЫЙ порт двигателя: объединяет оба танка (вход/выход).
     * Заливать/выливать жидкости можно через любой порт; жидкости распределяются
     * по валидности танков: высокий/низкий пар идут в свои танки.
     */
    private final IFluidHandler universalCap = new IFluidHandler() {
        @Override
        public int getTanks() { return 2; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank == 0) return steamTank.getFluid();
            if (tank == 1) return lowPressureSteamTank.getFluid();
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank == 0) return steamTank.getCapacity();
            if (tank == 1) return lowPressureSteamTank.getCapacity();
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank == 0) return steamTank.isFluidValid(stack);
            if (tank == 1) return lowPressureSteamTank.isFluidValid(stack);
            return false;
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (steamTank.isFluidValid(resource)) {
                int filled = steamTank.fill(resource, action);
                if (filled > 0) return filled;
            }
            if (lowPressureSteamTank.isFluidValid(resource)) {
                return lowPressureSteamTank.fill(resource, action);
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, FluidAction action) {
            if (lowPressureSteamTank.isFluidValid(resource)) return lowPressureSteamTank.drain(resource, action);
            if (steamTank.isFluidValid(resource)) return steamTank.drain(resource, action);
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            // Сначала отдаём выходной (низкое давление), затем входной запас.
            FluidStack drained = lowPressureSteamTank.drain(maxDrain, action);
            if (!drained.isEmpty()) return drained;
            return steamTank.drain(maxDrain, action);
        }
    };

    private long currentSpeed = 0;
    private long currentTorque = 0;
    private boolean isGenerating = false;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ENGINE_BE.get(), pos, state);
    }

    @Override
    public @Nullable IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role, BlockPos partPos) {
        // Универсальный порт: заливать/выливать можно через любой порт двигателя,
        // но подключение (и capability, и визуальный коннект трубы — они завязаны
        // на одну и ту же capability) разрешено ТОЛЬКО на портовых гранях:
        //  - верхний блок (входной порт, 'I') — сбоку, перпендикулярно оси facing;
        //  - блок ниже (выходной порт, 'O') — спереди и сзади, вдоль оси facing.
        if (side != null && !isPortAccessFace(partPos, side, role)) {
            return null;
        }
        return universalCap;
    }

    /** Грань порта валидна для подключения? Структурно внешняя И соответствует расположению самого порта. */
    private boolean isPortAccessFace(BlockPos partPos, Direction side, PartRole role) {
        BlockState state = getBlockState();
        if (!state.hasProperty(SteamEngineBlock.FACING)) return true;
        // Структурно-внутренние грани закрыты всегда (за гранью часть/контроллер).
        if (!isExternalPartFace(partPos, side)) return false;
        Direction facing = state.getValue(SteamEngineBlock.FACING);
        if (role == PartRole.FLUID_OUTPUT) {
            // Выходной порт (блок ниже верхнего): спереди и сзади — вдоль оси facing.
            return side.getAxis() == facing.getAxis();
        }
        if (role == PartRole.FLUID_INPUT) {
            // Входной (верхний) порт: сбоку — перпендикулярно оси facing, по горизонтали.
            return side.getAxis() != facing.getAxis() && side.getAxis() != Direction.Axis.Y;
        }
        // Прочие роли (если появятся) — любая структурно-внешняя грань.
        return true;
    }

    /** Грань части смотрит наружу структуры? (не упирается в соседнюю часть или в сам контроллер). */
    private boolean isExternalPartFace(BlockPos partPos, Direction side) {
        if (level == null) return true;
        BlockState state = getBlockState();
        if (!state.hasProperty(SteamEngineBlock.FACING)) return true;
        Direction facing = state.getValue(SteamEngineBlock.FACING);
        if (state.getBlock() instanceof IMultiblockController controller) {
            return controller.getStructureHelper().isPortFaceExternal(worldPosition, facing, partPos, side);
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamEngineBlockEntity be) {
        if (!level.isClientSide) {
            be.serverTick((ServerLevel) level);
        }
    }

    private void serverTick(ServerLevel level) {
        int maxConvert = 100; // 2k/s is 100 per tick (20 ticks per second)
        int availableToConvert = Math.min(steamTank.getFluidAmount(), lowPressureSteamTank.getSpace());
        int converted = Math.min(availableToConvert, maxConvert);

        if (converted > 0 && steamTank.getFluidAmount() > 0 && lowPressureSteamTank.getSpace() > 0) {
            FluidStack drained = steamTank.drain(converted, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                int amount = drained.getAmount();
                FluidStack lowPressure = new FluidStack(com.trd.api.fluids.ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), amount);
                lowPressureSteamTank.fill(lowPressure, IFluidHandler.FluidAction.EXECUTE);

                long targetSpeed = Math.min(amount * 10L, 250L);
                long targetTorque = Math.min(amount * 2L, 80L);

                if (currentSpeed != targetSpeed || currentTorque != targetTorque || !isGenerating) {
                    currentSpeed = targetSpeed;
                    currentTorque = targetTorque;
                    isGenerating = true;
                    requestKineticRecalculation();
                    setChanged();
                }
            }
        } else {
            if (isGenerating) {
                currentSpeed = 0;
                currentTorque = 0;
                isGenerating = false;
                requestKineticRecalculation();
                setChanged();
            }
        }

        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        setChanged();
    }

    private void requestKineticRecalculation() {
        if (level instanceof ServerLevel serverLevel) {
            KineticNetwork net = KineticNetworkManager.get(serverLevel).getNetworkFor(worldPosition);
            if (net != null) net.requestRecalculation();
        }
    }

    @Override
    public long getGeneratedSpeed() {
        return isGenerating ? currentSpeed : 0;
    }

    @Override
    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(SteamEngineBlock.FACING)) return 0;
        Direction facing = state.getValue(SteamEngineBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public long getTorque() {
        return isGenerating ? currentTorque : 0;
    }

    @Override
    public boolean isSource() {
        return true;
    }

    @Override
    public double getInertiaContribution() {
        return 10.0;
    }

    @Override
    public long getMaxTorqueTolerance() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxSpeed() {
        return 2500L;
    }

    @Override
    public long getMaxTorque() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        return true;
    }

    @Override
    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (!state.hasProperty(SteamEngineBlock.FACING)) return new Direction[0];
        Direction facing = state.getValue(SteamEngineBlock.FACING);
        Direction.Axis crankshaftAxis = facing.getAxis();
        return new Direction[] {
            Direction.get(Direction.AxisDirection.POSITIVE, crankshaftAxis),
            Direction.get(Direction.AxisDirection.NEGATIVE, crankshaftAxis)
        };
    }

    @Override
    public java.util.List<BlockPos> getPotentialConnections(Level lvl, BlockPos myPos) {
        Direction[] dirs = getPropagationDirections();
        if (dirs.length < 2) return java.util.List.of();
        return java.util.List.of(myPos.relative(dirs[0]), myPos.relative(dirs[1]));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            KineticNetwork net = KineticNetworkManager
                    .get((ServerLevel) level)
                    .getNetworkFor(worldPosition);
            if (net != null) {
                this.speed = net.getSpeed();
                this.lastSyncedSpeed = this.speed;
                net.requestRecalculation();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("SteamTank", steamTank.writeToNBT(provider, new CompoundTag()));
        tag.put("LowPressureSteamTank", lowPressureSteamTank.writeToNBT(provider, new CompoundTag()));
        tag.putLong("CurrentSpeed", currentSpeed);
        tag.putLong("CurrentTorque", currentTorque);
        tag.putBoolean("IsGenerating", isGenerating);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("SteamTank")) steamTank.readFromNBT(provider, tag.getCompound("SteamTank"));
        if (tag.contains("LowPressureSteamTank")) lowPressureSteamTank.readFromNBT(provider, tag.getCompound("LowPressureSteamTank"));
        currentSpeed = tag.getLong("CurrentSpeed");
        currentTorque = tag.getLong("CurrentTorque");
        isGenerating = tag.getBoolean("IsGenerating");
    }
}
