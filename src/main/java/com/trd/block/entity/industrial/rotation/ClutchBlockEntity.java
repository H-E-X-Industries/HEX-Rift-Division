package com.trd.block.entity.industrial.rotation;

import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.basic.industrial.rotation.ClutchBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ClutchBlockEntity extends KineticNodeBlockEntity {

    private boolean hasShaft = false;
    private ShaftMaterial shaftMaterial = null;
    private ShaftDiameter shaftDiameter = null;

    public ClutchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLUTCH_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClutchBlockEntity be) {
        if (!be.hasShaft()) {
            if (be.speed != 0) {
                be.speed = 0;
                be.syncToClient();
            }
        }
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (!hasShaft()) return false;
        
        BlockState state = getBlockState();
        if (!state.hasProperty(ClutchBlock.FACING)) return false;
        
        Direction facing = state.getValue(ClutchBlock.FACING);
        if (!neighborPos.equals(myPos.relative(facing)) && !neighborPos.equals(myPos.relative(facing.getOpposite()))) {
            return false;
        }

        if (state.hasProperty(ClutchBlock.POWERED) && !state.getValue(ClutchBlock.POWERED)) {
            // Разрываем сеть, если нет редстоун сигнала
            return false;
        }

        if (neighbor instanceof ShaftBlockEntity shaft) {
            if (shaft.getBlockState().getBlock() instanceof com.trd.block.basic.industrial.rotation.ShaftBlock sb) {
                return sb.getDiameter() == this.shaftDiameter && shaft.getBlockState().getValue(com.trd.block.basic.industrial.rotation.ShaftBlock.FACING).getAxis() == facing.getAxis();
            }
        } else if (neighbor instanceof BearingBlockEntity bearing) {
            return bearing.hasShaft() && bearing.getShaftDiameter() == this.shaftDiameter && bearing.getBlockState().getValue(com.trd.block.basic.industrial.rotation.BearingBlock.FACING).getAxis() == facing.getAxis();
        } else if (neighbor instanceof ClutchBlockEntity otherClutch) {
            return otherClutch.hasShaft() && otherClutch.getShaftDiameter() == this.shaftDiameter && otherClutch.getBlockState().getValue(ClutchBlock.FACING).getAxis() == facing.getAxis();
        } else if (neighbor instanceof TachometerBlockEntity tach) {
            return tach.hasShaft() && tach.getShaftDiameter() == this.shaftDiameter && tach.getBlockState().getValue(com.trd.block.basic.industrial.rotation.TachometerBlock.FACING).getAxis() == facing.getAxis();
        } else if (neighbor instanceof MotorElectroBlockEntity motor) {
            return this.shaftDiameter == ShaftDiameter.LIGHT && motor.getBlockState().getValue(com.trd.block.basic.industrial.rotation.MotorElectroBlock.FACING).getAxis() == facing.getAxis();
        }

        return true;
    }

    @Override
    public float calculateTransmissionRatio(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        return 1.0f;
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        if (!hasShaft()) return list;

        BlockState state = getBlockState();
        if (state.hasProperty(ClutchBlock.FACING)) {
            Direction facing = state.getValue(ClutchBlock.FACING);
            list.add(pos.relative(facing));
            list.add(pos.relative(facing.getOpposite()));
        }
        return list;
    }

    public ShaftDiameter getDiameterForNeighbor(BlockPos neighborPos) {
        return shaftDiameter; // Возвращаем диаметр вставленного вала
    }

    @Override
    public long getMaxTorqueTolerance() {
        return 10000;
    }
    
    @Override
    public double getInertiaContribution() {
        if (hasShaft && shaftMaterial != null && shaftDiameter != null) {
            return shaftMaterial.baseInertia() * shaftDiameter.inertiaMod;
        }
        return 0.2;
    }

    @Override
    public long getMaxSpeed() {
        if (hasShaft && shaftMaterial != null && shaftDiameter != null) {
            return (long) (shaftMaterial.baseSpeed() * shaftDiameter.getSpeedMultiplier());
        }
        return 1024;
    }

    @Override
    public long getMaxTorque() {
        if (hasShaft && shaftMaterial != null && shaftDiameter != null) {
            return (long) (shaftMaterial.baseTorque() * shaftDiameter.getTorqueMultiplier());
        }
        return 10000;
    }

    @Override
    public long getVisualSpeed() {
        if (!this.hasShaft) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(ClutchBlock.FACING)) return 0;
        Direction facing = state.getValue(ClutchBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public long getTorque() {
        return 0;
    }

    public boolean hasShaft() {
        return hasShaft;
    }

    public void insertShaft(ShaftMaterial material, ShaftDiameter diameter) {
        this.hasShaft = true;
        this.shaftMaterial = material;
        this.shaftDiameter = diameter;
        setChanged();
        syncToClient();
    }

    public void removeShaft() {
        this.hasShaft = false;
        this.shaftMaterial = null;
        this.shaftDiameter = null;
        setChanged();
        syncToClient();
    }

    public ShaftMaterial getShaftMaterial() {
        return shaftMaterial;
    }

    public ShaftDiameter getShaftDiameter() {
        return shaftDiameter;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("HasShaft", hasShaft);
        if (hasShaft && shaftMaterial != null && shaftDiameter != null) {
            tag.putString("ShaftMaterial", shaftMaterial.name());
            tag.putString("ShaftDiameter", shaftDiameter.name());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.hasShaft = tag.getBoolean("HasShaft");
        if (hasShaft) {
            if (tag.contains("ShaftMaterial")) {
                String matName = tag.getString("ShaftMaterial").toLowerCase();
                this.shaftMaterial = switch (matName) {
                    case "duralumin"       -> ShaftMaterial.DURALUMIN;
                    case "steel"           -> ShaftMaterial.STEEL;
                    case "titanium"        -> ShaftMaterial.TITANIUM;
                    case "tungsten_carbide"-> ShaftMaterial.TUNGSTEN_CARBIDE;
                    default                -> ShaftMaterial.IRON;
                };
            }
            if (tag.contains("ShaftDiameter")) {
                this.shaftDiameter = ShaftDiameter.valueOf(tag.getString("ShaftDiameter"));
            }
        }
    }
}
