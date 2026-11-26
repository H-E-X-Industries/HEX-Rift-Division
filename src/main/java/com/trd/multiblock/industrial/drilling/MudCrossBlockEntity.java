package com.trd.multiblock.industrial.drilling;

import com.trd.api.capabilities.ModDataComponents;
import com.trd.api.industiral.fluids.ModFluids;
import com.trd.api.industiral.fluids.system.ITankWithMode;
import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.client.particle.ExplosionParticleUtils;
import com.trd.entity.industrial.drilling.LaunchedDrillPipeEntity;
import com.trd.event.explosion.logic.ExplosionStandard;
import com.trd.multiblock.industrial.drilling.oilrig.core.*;
import com.trd.multiblock.system.IFluidTankProvider;
import com.trd.multiblock.system.IMultiblockFluidHandler;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Устьевой BlockEntity буровой крестовины (MudCross).
 * Буфер 16000 mB скважинного флюида, хранение параметров жилы,
 * расчёт давления, стравливание (200 бар) и детонация (250 бар),
 * а также состояние готовности Этапа 1 для голограммы сборки.
 */
public class MudCrossBlockEntity extends BlockEntity
        implements IWellheadModule, IMultiblockFluidHandler, IFluidTankProvider, ITankWithMode {

    public static final Set<MudCrossBlockEntity> CLIENT_WELLHEADS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static final int BUFFER_CAPACITY_MB = 16000;
    private static final BlockPos LOCAL_HOLE_FROM_CONTROLLER = new BlockPos(0, 0, -1);

    private final FluidTank fluidTank = new FluidTank(BUFFER_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == ModFluids.WELLHEAD_FLUID_SOURCE.get();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            syncToClient();
        }
    };

    /** Внешние трубы могут только забирать скважинный флюид (как из источника). */
    private final IFluidHandler outputOnlyHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return fluidTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluidTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || fluidTank.isEmpty()) return FluidStack.EMPTY;
            if (fluidTank.getFluid().getFluid() != resource.getFluid()) return FluidStack.EMPTY;
            return fluidTank.drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return fluidTank.drain(maxDrain, action);
        }
    };

    private float pressureBar = 0.0f;
    @Nullable
    private OilComposition reservoirComposition;
    @Nullable
    private BlockPos activePocketPos;
    @Nullable
    private UUID activeVeinId;

    @Nullable
    private transient OilRigAssembly assembly;
    private boolean hasPlatformAbove = false;
    private boolean hasRotaryTableAbove = false;
    private int structureScanCooldown = 0;
    private boolean detonating = false;

    public MudCrossBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUD_CROSS_BE.get(), pos, state);
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MudCrossBlock.FACING) ? state.getValue(MudCrossBlock.FACING) : Direction.NORTH;
    }

    @Override
    public BlockPos getRigPos() {
        return worldPosition;
    }

    @Override
    public BlockPos getWellheadCenterPos() {
        return worldPosition.offset(MultiblockStructureHelper.rotate(LOCAL_HOLE_FROM_CONTROLLER, getFacing()));
    }

    @Override
    public WellboreColumn getWellboreColumn() {
        BlockPos hole = getWellheadCenterPos();
        return new WellboreColumn(hole.getX(), hole.getZ());
    }

    @Override
    public RigModuleRole getModuleRole() {
        return RigModuleRole.WELLHEAD;
    }

    @Override
    public void setAssembly(@Nullable OilRigAssembly assembly) {
        this.assembly = assembly;
    }

    @Override
    @Nullable
    public OilRigAssembly getAssembly() {
        return assembly;
    }

    @Override
    public boolean isModuleValid() {
        return !this.isRemoved() && !detonating && level != null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && !detonating) {
            OilRigManager.get(serverLevel).onModuleLoaded(serverLevel, this);
        } else if (level != null && level.isClientSide) {
            CLIENT_WELLHEADS.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT_WELLHEADS.remove(this);
        if (level instanceof ServerLevel serverLevel) {
            OilRigManager.get(serverLevel).onModuleRemoved(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        CLIENT_WELLHEADS.remove(this);
        super.onChunkUnloaded();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MudCrossBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || be.detonating) return;

        if (--be.structureScanCooldown <= 0) {
            be.structureScanCooldown = 10;
            be.refreshStage1Status(serverLevel);
        }

        OilRigAssembly asm = be.assembly;
        if (asm != null) {
            asm.serverTick(serverLevel);
        }
    }

    private void refreshStage1Status(ServerLevel level) {
        BlockPos wellCenter = getWellheadCenterPos();
        int platformY = worldPosition.getY() + 1;
        boolean platformOk = true;
        for (int dx = -4; dx <= 4 && platformOk; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos checkPos = new BlockPos(wellCenter.getX() + dx, platformY, wellCenter.getZ() + dz);
                boolean isSteel = level.getBlockState(checkPos).is(ModBlocks.DECO_STEEL.get());
                if (dx == 0 && dz == 0) {
                    if (isSteel) {
                        platformOk = false;
                        break;
                    }
                } else if (!isSteel) {
                    platformOk = false;
                    break;
                }
            }
        }

        boolean rotaryOk = assembly != null
                && assembly.getDrive() != null
                && assembly.getDrive().isModuleValid()
                && assembly.getDrive().getWellCenterPos().getY() == worldPosition.getY() + 2;

        if (platformOk != this.hasPlatformAbove || rotaryOk != this.hasRotaryTableAbove) {
            this.hasPlatformAbove = platformOk;
            this.hasRotaryTableAbove = rotaryOk;
            setChanged();
            syncToClient();
        }
    }

    public boolean hasPlatformAbove() {
        return hasPlatformAbove;
    }

    public boolean hasRotaryTableAbove() {
        return hasRotaryTableAbove;
    }

    public boolean isStage1Complete() {
        return hasPlatformAbove && hasRotaryTableAbove;
    }

    // ==================== СКВАЖИННЫЙ ФЛЮИД И ПОРТЫ ====================

    @Override
    public int getFluidAmount() {
        return fluidTank.getFluidAmount();
    }

    @Override
    public int getFluidCapacity() {
        return BUFFER_CAPACITY_MB;
    }

    @Override
    public int fillWellheadFluid(FluidStack stack, boolean simulate) {
        if (stack.isEmpty() || stack.getFluid() != ModFluids.WELLHEAD_FLUID_SOURCE.get()) {
            return 0;
        }
        FluidStack current = fluidTank.getFluid();
        if (current.isEmpty()) {
            return fluidTank.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }
        if (FluidStack.isSameFluidSameComponents(current, stack)) {
            return fluidTank.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }
        int space = Math.max(0, BUFFER_CAPACITY_MB - current.getAmount());
        int toAdd = Math.min(space, stack.getAmount());
        if (!simulate && toAdd > 0) {
            FluidStack updated = current.copyWithAmount(current.getAmount() + toAdd);
            var compType = ModDataComponents.WELLHEAD_FLUID_DATA.get();
            ModDataComponents.WellheadFluidData blended = ModDataComponents.WellheadFluidData.blend(
                    current.get(compType), current.getAmount(),
                    stack.get(compType), toAdd
            );
            if (blended != null) {
                updated.set(compType, blended);
            }
            fluidTank.setFluid(updated);
            setChanged();
            syncToClient();
        }
        return toAdd;
    }

    @Override
    public int extractWellheadFluid(int amountMb, @Nullable OilComposition composition, boolean simulate) {
        if (amountMb <= 0) return 0;
        FluidStack stack = new FluidStack(ModFluids.WELLHEAD_FLUID_SOURCE.get(), amountMb);
        OilComposition effectiveComp = composition != null ? composition : this.reservoirComposition;
        stack.set(ModDataComponents.WELLHEAD_FLUID_DATA.get(),
                ModDataComponents.WellheadFluidData.fromComposition(effectiveComp));
        return fillWellheadFluid(stack, simulate);
    }

    @Override
    public IFluidHandler getFluidHandlerCapability() {
        return outputOnlyHandler;
    }

    @Nullable
    public IFluidHandler getControllerFluidHandler(@Nullable Direction side) {
        if (side == null) return outputOnlyHandler;
        if (side == Direction.UP) return null;
        if (getBlockState().getBlock() instanceof MudCrossBlock block) {
            if (block.getStructureHelper().isPortFaceExternal(worldPosition, getFacing(), worldPosition, side)) {
                return outputOnlyHandler;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role, BlockPos partPos) {
        if (role != PartRole.FLUID_CONNECTOR) return null;
        if (side == null) return outputOnlyHandler;
        if (side == Direction.UP) return null;
        if (getBlockState().getBlock() instanceof MudCrossBlock block) {
            if (block.getStructureHelper().isPortFaceExternal(worldPosition, getFacing(), partPos, side)) {
                return outputOnlyHandler;
            }
        }
        return null;
    }

    @Override
    public int getMode() {
        return 2; // Только отдача в трубы (OUTPUT)
    }

    @Override
    public void changeMode() {
    }

    // ==================== ЖИЛА, ДАВЛЕНИЕ И АВАРИЯ ====================

    @Override
    @Nullable
    public OilComposition getReservoirComposition() {
        return reservoirComposition;
    }

    @Override
    @Nullable
    public BlockPos getActivePocketPos() {
        return activePocketPos;
    }

    @Override
    @Nullable
    public UUID getActiveVeinId() {
        return activeVeinId;
    }

    @Override
    public void bindReservoir(@Nullable OilComposition composition, @Nullable BlockPos pocketPos, @Nullable UUID veinId) {
        this.reservoirComposition = composition;
        this.activePocketPos = pocketPos != null ? pocketPos.immutable() : null;
        this.activeVeinId = veinId;
        setChanged();
        syncToClient();
    }

    @Override
    public float getPressureBar() {
        return pressureBar;
    }

    @Override
    public void setPressureBar(float pressureBar) {
        float clamped = Math.max(0.0f, pressureBar);
        if (Math.abs(clamped - this.pressureBar) > 0.05f || (clamped == 0.0f && this.pressureBar != 0.0f)) {
            this.pressureBar = clamped;
            setChanged();
            syncToClient();
        } else {
            this.pressureBar = clamped;
        }
    }

    @Override
    public void onGasVentTick(ServerLevel level) {
        BlockPos wellCenter = getWellheadCenterPos();
        double cx = wellCenter.getX() + 0.5;
        double cy = wellCenter.getY() + 1.05;
        double cz = wellCenter.getZ() + 0.5;

        if (level.getGameTime() % 6 == 0) {
            level.playSound(null, cx, cy, cz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.75f, 1.85f);
        }
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 4, 0.2, 0.25, 0.2, 0.08);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 2, 0.15, 0.2, 0.15, 0.04);
    }

    @Override
    public void onBlowoutDetonate(ServerLevel level, @Nullable IDrillDriveModule drive) {
        if (detonating) return;
        detonating = true;

        BlockPos wellCenter = getWellheadCenterPos();
        Vec3 wellVec = Vec3.atCenterOf(wellCenter);

        int purgedPipes = 0;
        BlockPos drivePos = null;
        BlockPos driveCenter = null;
        if (drive != null && drive.isModuleValid()) {
            drivePos = drive.getRigPos();
            driveCenter = drive.getWellCenterPos();
            purgedPipes = drive.purgeWellborePipesForBlowout(level);
        }

        if (drivePos != null && !level.getBlockState(drivePos).isAir()) {
            level.removeBlock(drivePos, false);
        }
        OilRigManager.get(level).onModuleRemoved(level, worldPosition);
        if (!level.getBlockState(worldPosition).isAir()) {
            level.removeBlock(worldPosition, false);
        }

        ExplosionStandard.explode(level, wellVec, null, 5.5f, 34.0f, ModSounds.GRENADE_EXPLOSION);
        if (driveCenter != null) {
            ExplosionStandard.explode(level, Vec3.atCenterOf(driveCenter), null, 4.5f, 24.0f, ModSounds.GRENADE_EXPLOSION);
        }
        ExplosionParticleUtils.spawnAirBombExplosion(level, wellVec.x, wellVec.y + 1.0, wellVec.z);

        for (int i = 0; i < purgedPipes; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double speed = 0.18 + level.random.nextDouble() * 0.42;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.78 + level.random.nextDouble() * 0.58;

            Vec3 spawnPos = new Vec3(
                    wellCenter.getX() + 0.5 + vx * 0.35,
                    wellCenter.getY() + 2.5 + (i % 4) * 0.45,
                    wellCenter.getZ() + 0.5 + vz * 0.35
            );
            LaunchedDrillPipeEntity launched = LaunchedDrillPipeEntity.launchFromWellhead(
                    level,
                    spawnPos,
                    new Vec3(vx, vy, vz),
                    level.random.nextFloat() * 360.0f,
                    (level.random.nextFloat() - 0.5f) * 14.0f,
                    5.0f + level.random.nextFloat() * 10.0f
            );
            level.addFreshEntity(launched);
        }
    }

    // ==================== NBT И СИНХРОНИЗАЦИЯ ====================

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag fluidNbt = new CompoundTag();
        fluidTank.writeToNBT(registries, fluidNbt);
        tag.put("FluidTank", fluidNbt);
        tag.putFloat("PressureBar", pressureBar);
        tag.putBoolean("HasPlatform", hasPlatformAbove);
        tag.putBoolean("HasRotary", hasRotaryTableAbove);
        if (reservoirComposition != null) {
            tag.put("ReservoirComp", reservoirComposition.save());
        }
        if (activePocketPos != null) {
            tag.putLong("PocketPos", activePocketPos.asLong());
        }
        if (activeVeinId != null) {
            tag.putUUID("VeinId", activeVeinId);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FluidTank")) {
            fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        }
        pressureBar = tag.getFloat("PressureBar");
        hasPlatformAbove = tag.getBoolean("HasPlatform");
        hasRotaryTableAbove = tag.getBoolean("HasRotary");
        reservoirComposition = tag.contains("ReservoirComp")
                ? OilComposition.load(tag.getCompound("ReservoirComp"))
                : null;
        activePocketPos = tag.contains("PocketPos") ? BlockPos.of(tag.getLong("PocketPos")) : null;
        activeVeinId = tag.hasUUID("VeinId") ? tag.getUUID("VeinId") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
