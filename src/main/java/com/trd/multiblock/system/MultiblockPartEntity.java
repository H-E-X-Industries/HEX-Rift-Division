package com.trd.multiblock.system;

import com.trd.api.fluids.system.FluidNetworkManager;
import com.trd.api.rotation.Rotational;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
import com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity;
import com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class MultiblockPartEntity extends BlockEntity implements IMultiblockPart, Rotational {

    private BlockPos controllerPos;
    private PartRole role = PartRole.DEFAULT;
    private Set<Direction> allowedClimbSides = EnumSet.noneOf(Direction.class);
    private long kineticSpeed = 0;
    private float kineticNetworkScale = 1.0f;
    public MultiblockPartEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_PART.get(), pos, state);
    }

    @Nullable
    @Override
    public BlockPos getControllerPos() { return controllerPos; }

    @Override
    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setPartRole(PartRole role) {
        boolean wasNetworked = isNetworkedRole(this.role);
        boolean isNetworked  = isNetworkedRole(role);

        this.role = role;
        setChanged();

        if (this.level != null && !this.level.isClientSide) {
            FluidNetworkManager fluidManager = FluidNetworkManager.get((ServerLevel) this.level);
            com.trd.api.energy.EnergyNetworkManager energyManager = com.trd.api.energy.EnergyNetworkManager.get((ServerLevel) this.level);
            
            if (!wasNetworked && isNetworked) {
                if (role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR) {
                    if (!energyManager.hasNode(this.getBlockPos())) energyManager.addNode(this.getBlockPos());
                }
            } else if (wasNetworked && !isNetworked) {
                energyManager.removeNode(this.getBlockPos());
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public PartRole getPartRole() { return role; }

    @Override
    public void setAllowedClimbSides(Set<Direction> sides) { this.allowedClimbSides = sides; }

    @Override
    public Set<Direction> getAllowedClimbSides() { return allowedClimbSides; }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            if (isNetworkedRole(this.role)) {
                if (role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR) {
                    com.trd.api.energy.EnergyNetworkManager energyManager = com.trd.api.energy.EnergyNetworkManager.get((net.minecraft.server.level.ServerLevel) this.level);
                    if (!energyManager.hasNode(this.getBlockPos())) energyManager.addNode(this.getBlockPos());
                }
            }
            if (isKineticPort()) {
                com.trd.api.rotation.KineticNetwork net = com.trd.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) this.level).getNetworkFor(worldPosition);
                if (net != null) {
                    this.kineticSpeed = (long) (net.getSpeed() * this.kineticNetworkScale);
                    net.requestRecalculation();
                }
            }
        }
    }

    // ==================== Rotational (Только для KINETIC_PORT) ====================

    public boolean isKineticPort() {
        return this.role == PartRole.KINETIC_PORT;
    }

    private Rotational getControllerRotational() {
        if (controllerPos == null || level == null) return null;
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof Rotational r ? r : null;
    }

    @Override
    public long getSpeed() {
        if (!isKineticPort()) return 0;
        return this.kineticSpeed;
    }

    @Override
    public void setSpeed(long speed) {
        if (!isKineticPort()) return;
        this.kineticSpeed = (long) (speed * this.kineticNetworkScale);
    }

    @Override
    public long getTorque() {
        return 0;
    }

    @Override
    public long getMaxSpeed() {
        Rotational ctrl = getControllerRotational();
        return ctrl != null ? ctrl.getMaxSpeed() : 0;
    }

    @Override
    public long getMaxTorque() {
        Rotational ctrl = getControllerRotational();
        return ctrl != null ? ctrl.getMaxTorque() : 0;
    }

    @Override
    public double getInertiaContribution() {
        return isKineticPort() ? 0.5 : 0;
    }

    @Override
    public long getMaxTorqueTolerance() {
        Rotational ctrl = getControllerRotational();
        return ctrl != null ? ctrl.getMaxTorqueTolerance() : 0;
    }

    @Override
    public long getConsumedTorque() {
        return 0;
    }

    @Override
    public boolean isSource() {
        return false;
    }

    @Override
    public long getVisualSpeed() {
        Rotational ctrl = getControllerRotational();
        if (ctrl != null) return ctrl.getVisualSpeed();
        return getSpeed();
    }

    @Override
    public Direction[] getPropagationDirections() {
        if (!isKineticPort() || controllerPos == null || level == null) return new Direction[0];

        // Проверяем контроллер станка (боковые порты: запад-восток)
        net.minecraft.world.level.block.entity.BlockEntity ctrlBe = level.getBlockEntity(controllerPos);
        if (ctrlBe instanceof com.trd.multiblock.industrial.stanok.StanokBlockEntity sbe) {
            BlockPos westPort = sbe.getWestPortPos();
            BlockPos eastPort = sbe.getEastPortPos();
            
            Direction facing = Direction.NORTH;
            if (sbe.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                facing = sbe.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            }
            
            if (worldPosition.equals(westPort)) return new Direction[]{facing.getCounterClockWise(), facing.getClockWise()};
            if (worldPosition.equals(eastPort)) return new Direction[]{facing.getClockWise(), facing.getCounterClockWise()};
            return new Direction[0];
        }

        BlockState ctrlState = level.getBlockState(controllerPos);

        // ❌ Было: if (!(ctrlState.getBlock() instanceof HorizontalDirectionalBlock)) return new Direction[0];
        // ✅ Стало: проверяем наличие свойства, а не класс блока
        if (!ctrlState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))
            return new Direction[0];

        Direction facing = ctrlState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        BlockPos front = controllerPos.relative(facing);
        BlockPos back = controllerPos.relative(facing.getOpposite());

        if (worldPosition.equals(front)) return new Direction[]{facing, facing.getOpposite()};
        if (worldPosition.equals(back)) return new Direction[]{facing.getOpposite(), facing};
        return new Direction[0];
    }

    @Override
    public java.util.List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos myPos) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        if (!isKineticPort()) return list;
        if (controllerPos != null) list.add(controllerPos);
        for (Direction dir : getPropagationDirections()) {
            if (dir != null) list.add(myPos.relative(dir));
        }
        return list;
    }

    @Override
    public boolean canConnectMechanically(net.minecraft.core.BlockPos myPos, net.minecraft.core.BlockPos neighborPos, Rotational neighbor) {
        if (!isKineticPort()) return false;
        // Соединение с контроллером мультиблока (дробитель или станок)
        if (controllerPos != null && neighborPos.equals(controllerPos)) {
            return neighbor instanceof com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity
                    || neighbor instanceof com.trd.multiblock.industrial.stanok.StanokBlockEntity;
        }
        // Соединение с внешними кинетическими блоками (вал, подшипник, мотор и т.д.)
        for (Direction dir : getPropagationDirections()) {
            if (myPos.relative(dir).equals(neighborPos)) {
                return neighbor instanceof com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
            }
        }
        return false;
    }

    @Override
    public float calculateTransmissionRatio(net.minecraft.core.BlockPos myPos, net.minecraft.core.BlockPos neighborPos, Rotational neighbor) {
        return 1.0f;
    }

    @Override
    public void setNetworkScale(float scale) {
        if (isKineticPort()) this.kineticNetworkScale = scale;
    }

    @Override
    public float getNetworkScale() {
        return isKineticPort() ? this.kineticNetworkScale : 1.0f;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide && isNetworkedRole(this.role)) {
            com.trd.api.energy.EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

    private static boolean isNetworkedRole(PartRole role) {
        return role == PartRole.FLUID_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR || role == PartRole.ENERGY_CONNECTOR || role == PartRole.FLUID_INPUT || role == PartRole.FLUID_OUTPUT || role == PartRole.FLUID_LADDER;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) tag.putLong("ControllerPos", controllerPos.asLong());
        tag.putString("Role", role.getSerializedName());
        
        byte climbMask = 0;
        for (Direction d : allowedClimbSides) {
            climbMask |= (1 << d.ordinal());
        }
        tag.putByte("ClimbSides", climbMask);

        if (isKineticPort()) {
            tag.putLong("KineticSpeed", this.kineticSpeed);
            tag.putFloat("KineticScale", this.kineticNetworkScale);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (controllerPos != null && level != null) {
            // === FLUID ===
            if (cap == ForgeCapabilities.FLUID_HANDLER && (role == PartRole.FLUID_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR || role == PartRole.FLUID_INPUT || role == PartRole.FLUID_OUTPUT || role == PartRole.FLUID_LADDER)) {
                BlockEntity be = level.getBlockEntity(controllerPos);
                if (be instanceof BoilerBlockEntity boiler) {
                    return boiler.getCapabilityForPart(cap, side, role);
                } else if (be instanceof FuelTankSmallBlockEntity smallTank) {
                    return smallTank.getCapabilityForPart(cap, side, role);
                } else if (be instanceof SteamEngineBlockEntity steamEngine) {
                    return steamEngine.getCapabilityForPart(cap, side, role);
                } else if (be instanceof IFluidTankProvider provider) {
                    return provider.getFluidHandlerCapability().cast();
                }
            }
            // === ENERGY ===
            else if ((cap == com.trd.capability.ModCapabilities.ENERGY_PROVIDER || cap == com.trd.capability.ModCapabilities.ENERGY_CONNECTOR)
                    && (role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR)) {
                BlockEntity be = level.getBlockEntity(controllerPos);
                if (be != null) {
                    return be.getCapability(cap, side);
                }
            }
            // === ITEMS (новое!) ===
            else if (cap == ForgeCapabilities.ITEM_HANDLER) {
                BlockEntity be = level.getBlockEntity(controllerPos);
                if (be != null) {
                    // Специальная логика для карго-портов станка
                    if (be instanceof com.trd.multiblock.industrial.stanok.StanokBlockEntity sbe) {
                        if (role == PartRole.CARGO_PORT) {
                            Direction facing = sbe.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                            // Порты должны работать строго по бокам! Убрал side == null, чтобы агрессивные трубы не обходили проверку.
                            if (side == facing.getClockWise() || side == facing.getCounterClockWise()) {
                                return sbe.getCargoPortCapability().cast();
                            }
                        }
                        return LazyOptional.empty();
                    }
                    
                    return be.getCapability(cap, side);
                }
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ControllerPos")) controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        String roleName = tag.getString("Role");
        for (PartRole r : PartRole.values()) {
            if (r.getSerializedName().equals(roleName)) {
                this.role = r; break;
            }
        }
        
        if (tag.contains("ClimbSides")) {
            byte mask = tag.getByte("ClimbSides");
            allowedClimbSides.clear();
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    allowedClimbSides.add(d);
                }
            }
        }

        if (tag.contains("KineticSpeed")) this.kineticSpeed = tag.getLong("KineticSpeed");
        if (tag.contains("KineticScale")) this.kineticNetworkScale = tag.getFloat("KineticScale");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}