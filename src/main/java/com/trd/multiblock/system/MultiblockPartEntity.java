package com.trd.multiblock.system;

import com.trd.api.fluids.system.FluidNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.trd.multiblock.system.roles.IMultiblockPart;

import java.util.EnumSet;
import java.util.Set;

public class MultiblockPartEntity extends BlockEntity implements IMultiblockPart, com.trd.api.rotation.Rotational {

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
        boolean wasKinetic = isKineticPort();
        boolean isKinetic = (role == PartRole.KINETIC_PORT);

        this.role = role;
        setChanged();

        if (this.level != null && !this.level.isClientSide) {
            com.trd.api.energy.EnergyNetworkManager energyManager = com.trd.api.energy.EnergyNetworkManager.get((ServerLevel) this.level);
            
            if (!wasNetworked && isNetworked) {
                if (role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR) {
                    if (!energyManager.hasNode(this.getBlockPos())) energyManager.addNode(this.getBlockPos());
                }
            } else if (wasNetworked && !isNetworked) {
                energyManager.removeNode(this.getBlockPos());
            }

            if (!wasKinetic && isKinetic) {
                com.trd.api.rotation.KineticNetworkManager.get((ServerLevel) this.level).updateNetworkAfterPlace(this.getBlockPos());
            } else if (wasKinetic && !isKinetic) {
                com.trd.api.rotation.KineticNetworkManager.get((ServerLevel) this.level).updateNetworkAfterRemove(this.getBlockPos());
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
        }
    }

    public boolean isKineticPort() {
        return this.role == PartRole.KINETIC_PORT;
    }

    private com.trd.api.rotation.Rotational getControllerRotational() {
        if (controllerPos == null || level == null) return null;
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof com.trd.api.rotation.Rotational r ? r : null;
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
        com.trd.api.rotation.Rotational ctrl = getControllerRotational();
        return ctrl != null ? ctrl.getMaxSpeed() : 0;
    }

    @Override
    public long getMaxTorque() {
        com.trd.api.rotation.Rotational ctrl = getControllerRotational();
        return ctrl != null ? ctrl.getMaxTorque() : 0;
    }

    @Override
    public double getInertiaContribution() {
        return isKineticPort() ? 0.5 : 0;
    }

    @Override
    public long getMaxTorqueTolerance() {
        com.trd.api.rotation.Rotational ctrl = getControllerRotational();
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
        com.trd.api.rotation.Rotational ctrl = getControllerRotational();
        if (ctrl != null) return ctrl.getVisualSpeed();
        return getSpeed();
    }

    @Override
    public Direction[] getPropagationDirections() {
        if (!isKineticPort() || controllerPos == null || level == null) return new Direction[0];

        BlockState ctrlState = level.getBlockState(controllerPos);
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
    public boolean canConnectMechanically(net.minecraft.core.BlockPos myPos, net.minecraft.core.BlockPos neighborPos, com.trd.api.rotation.Rotational neighbor) {
        if (!isKineticPort()) return false;
        if (controllerPos != null && neighborPos.equals(controllerPos)) {
            return true;
        }
        for (Direction dir : getPropagationDirections()) {
            if (myPos.relative(dir).equals(neighborPos)) {
                return neighbor instanceof com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
            }
        }
        return false;
    }

    @Override
    public float calculateTransmissionRatio(net.minecraft.core.BlockPos myPos, net.minecraft.core.BlockPos neighborPos, com.trd.api.rotation.Rotational neighbor) {
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
        if (this.level != null && !this.level.isClientSide) {
            if (isNetworkedRole(this.role)) {
                com.trd.api.energy.EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
            }
            if (isKineticPort()) {
                com.trd.api.rotation.KineticNetworkManager.get((ServerLevel) this.level).updateNetworkAfterRemove(this.getBlockPos());
            }
        }
    }

    private static boolean isNetworkedRole(PartRole role) {
        return role == PartRole.FLUID_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR || role == PartRole.ENERGY_CONNECTOR || role == PartRole.FLUID_INPUT || role == PartRole.FLUID_OUTPUT || role == PartRole.FLUID_LADDER;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("ControllerPos", controllerPos.asLong());
        tag.putString("Role", role.getSerializedName());
        
        byte climbMask = 0;
        for (Direction d : allowedClimbSides) {
            climbMask |= (1 << d.ordinal());
        }
        tag.putByte("ClimbSides", climbMask);
        if (isKineticPort()) {
            tag.putLong("KineticSpeed", kineticSpeed);
            tag.putFloat("KineticNetworkScale", kineticNetworkScale);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ControllerPos")) controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        String roleName = tag.getString("Role");
        for (PartRole r : PartRole.values()) {
            if (r.getSerializedName().equals(roleName)) {
                this.role = r; break;
            }
        }
        if (tag.contains("KineticSpeed")) kineticSpeed = tag.getLong("KineticSpeed");
        if (tag.contains("KineticNetworkScale")) kineticNetworkScale = tag.getFloat("KineticNetworkScale");
        
        if (tag.contains("ClimbSides")) {
            byte mask = tag.getByte("ClimbSides");
            allowedClimbSides.clear();
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    allowedClimbSides.add(d);
                }
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}
