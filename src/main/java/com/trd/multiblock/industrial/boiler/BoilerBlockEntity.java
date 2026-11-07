package com.trd.multiblock.industrial.boiler;

import com.trd.api.fluids.ModFluids;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.heaters.HeaterBlockEntity;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.IMultiblockFluidHandler;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BoilerBlockEntity extends BlockEntity implements IMultiblockFluidHandler {
    public static final float BOILING_POINT = 100.0f;
    public static final float MAX_TEMP = 600.0f;
    public static final float HEAT_COST_PER_MB = 0.5f;
    public static final int STEAM_MULTIPLIER = 1;

    private float temperature = 20.0f;

    private final FluidTank waterTank = new FluidTank(16000) {
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

    private final FluidTank steamTank = new FluidTank(64000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == ModFluids.STEAM_SOURCE.get();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    /**
     * УНИВЕРСАЛЬНЫЙ порт бойлера: объединяет оба танка.
     * Вода идёт в водяной бак, пар — в паровой; отдаём сначала пар (выходной бак),
     * затем воду. Через любой порт валидной жидкости, но только через внешние грани.
     */
    private final IFluidHandler universalHandler = new IFluidHandler() {
        @Override
        public int getTanks() { return 2; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank == 0) return waterTank.getFluid();
            if (tank == 1) return steamTank.getFluid();
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank == 0) return waterTank.getCapacity();
            if (tank == 1) return steamTank.getCapacity();
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank == 0) return waterTank.isFluidValid(stack);
            if (tank == 1) return steamTank.isFluidValid(stack);
            return false;
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (waterTank.isFluidValid(resource)) {
                int filled = waterTank.fill(resource, action);
                if (filled > 0) return filled;
            }
            if (steamTank.isFluidValid(resource)) {
                return steamTank.fill(resource, action);
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, FluidAction action) {
            if (steamTank.isFluidValid(resource)) return steamTank.drain(resource, action);
            if (waterTank.isFluidValid(resource)) return waterTank.drain(resource, action);
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = steamTank.drain(maxDrain, action);
            if (!drained.isEmpty()) return drained;
            return waterTank.drain(maxDrain, action);
        }
    };

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        boolean changed = false;

        // 1. Охлаждение
        if (be.temperature > 20.0f) {
            be.temperature = Math.max(20.0f, be.temperature - (be.temperature * be.temperature / 600000f));
            changed = true;
        }

        // 2. Нагрев от HeaterBlockEntity снизу
        BlockEntity belowEntity = level.getBlockEntity(pos.below());
        if (belowEntity instanceof HeaterBlockEntity heater) {
            float heaterTemp = heater.getTemperature();
            if (heaterTemp > be.temperature) {
                float transfer = (heaterTemp - be.temperature) * 0.1f;
                be.temperature += transfer;
                changed = true;
            }
        }

        // 3. Кипение
        if (be.temperature > BOILING_POINT && !be.waterTank.isEmpty()) {
            float availableHeat = be.temperature - BOILING_POINT;
            int maxBoilAmount = (int) (availableHeat / HEAT_COST_PER_MB);

            if (maxBoilAmount > 0) {
                int waterAvailable = be.waterTank.getFluidAmount();
                int steamSpace = (be.steamTank.getCapacity() - be.steamTank.getFluidAmount()) / STEAM_MULTIPLIER;
                
                int boilAmount = Math.min(maxBoilAmount, Math.min(waterAvailable, steamSpace));

                if (boilAmount > 0) {
                    be.waterTank.drain(boilAmount, IFluidHandler.FluidAction.EXECUTE);
                    be.steamTank.fill(new FluidStack(ModFluids.STEAM_SOURCE.get(), boilAmount * STEAM_MULTIPLIER), IFluidHandler.FluidAction.EXECUTE);
                    be.temperature -= boilAmount * HEAT_COST_PER_MB;
                    changed = true;
                }
            }
        }

        // 4. Взрыв при превышении MAX_TEMP
        if (be.temperature >= MAX_TEMP) {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10.0f, Level.ExplosionInteraction.TNT);
            level.removeBlock(pos, false);
            return;
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        if (level.isClientSide) {
            com.trd.client.sound.BoilerSoundHandler.tick(be);
        }
    }

    @Override
    public @Nullable IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role, BlockPos partPos) {
        // Универсальный порт: заливать/выливать можно через любой порт бойлера,
        // но трубы подключаются только через грани, смотрящие наружу конструкции.
        // Именно это запрещает приём воды через «внутренние» и прочие неслужебные грани.
        if (side != null && !isExternalPartFace(partPos, side)) {
            return null;
        }
        return universalHandler;
    }

    /** Грань части смотрит наружу структуры? (не упирается в соседнюю часть или в сам контроллер). */
    private boolean isExternalPartFace(BlockPos partPos, Direction side) {
        if (level == null) return true;
        BlockState state = getBlockState();
        if (!state.hasProperty(BoilerBlock.FACING)) return true;
        Direction facing = state.getValue(BoilerBlock.FACING);
        if (state.getBlock() instanceof IMultiblockController controller) {
            return controller.getStructureHelper().isPortFaceExternal(worldPosition, facing, partPos, side);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("WaterTank", waterTank.writeToNBT(provider, new CompoundTag()));
        tag.put("SteamTank", steamTank.writeToNBT(provider, new CompoundTag()));
        tag.putFloat("Temperature", temperature);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        waterTank.readFromNBT(provider, tag.getCompound("WaterTank"));
        steamTank.readFromNBT(provider, tag.getCompound("SteamTank"));
        temperature = tag.getFloat("Temperature");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.put("WaterTank", waterTank.writeToNBT(provider, new CompoundTag()));
        tag.put("SteamTank", steamTank.writeToNBT(provider, new CompoundTag()));
        tag.putFloat("Temperature", temperature);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public FluidTank getWaterTank() { return waterTank; }
    public FluidTank getSteamTank() { return steamTank; }
    public float getTemperature() { return temperature; }
}
