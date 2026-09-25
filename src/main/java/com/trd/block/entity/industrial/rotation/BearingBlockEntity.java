package com.trd.block.entity.industrial.rotation;

import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.block.basic.industrial.rotation.BearingBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.List;

public class BearingBlockEntity extends KineticNodeBlockEntity {

    private boolean hasShaft = false;
    private ShaftMaterial shaftMaterial = null;
    private ShaftDiameter shaftDiameter = null;

    public BearingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BEARING_BE.get(), pos, state);
    }

    public boolean hasShaft() { return hasShaft; }
    public ShaftMaterial getShaftMaterial() { return shaftMaterial; }
    public ShaftDiameter getShaftDiameter() { return shaftDiameter; }

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

    // ===================== Rotational =====================

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (!this.hasShaft()) return false;

        if (neighbor instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == this.getShaftDiameter();
            }
        }
        if (neighbor instanceof BearingBlockEntity otherBearing) {
            return otherBearing.hasShaft() && otherBearing.getShaftDiameter() == this.getShaftDiameter();
        }
        return true;
    }

    @Override
    public Direction[] getPropagationDirections() {
        if (!hasShaft) return new Direction[0];
        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        return new Direction[]{facing, facing.getOpposite()};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        if (!hasShaft) return Collections.emptyList();
        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        return List.of(myPos.relative(facing), myPos.relative(facing.getOpposite()));
    }

    @Override
    public long getVisualSpeed() {
        if (!this.hasShaft) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(BearingBlock.FACING)) return 0;
        Direction facing = state.getValue(BearingBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public long getTorque() { return 0; }

    @Override
    public boolean isSource() { return false; }

    @Override
    public double getInertiaContribution() {
        return 1.0;
    }

    @Override
    public long getMaxTorqueTolerance() { return 10000; }

    @Override
    public long getMaxSpeed() {
        if (hasShaft() && getShaftMaterial() != null && getShaftDiameter() != null) {
            return (long) (getShaftMaterial().baseSpeed() * getShaftDiameter().getSpeedMultiplier());
        }
        return 1024;
    }

    @Override
    public long getMaxTorque() {
        if (hasShaft() && getShaftMaterial() != null && getShaftDiameter() != null) {
            return (long) (getShaftMaterial().baseTorque() * getShaftDiameter().getTorqueMultiplier());
        }
        return 10000;
    }

    // ===================== NBT =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("HasShaft", this.hasShaft);
        if (this.hasShaft && this.shaftMaterial != null && this.shaftDiameter != null) {
            tag.putString("ShaftMaterial", this.shaftMaterial.name());
            tag.putString("ShaftDiameter", this.shaftDiameter.name());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.hasShaft = tag.getBoolean("HasShaft");
        if (this.hasShaft) {
            String matName = tag.getString("ShaftMaterial").toLowerCase();
            String diaName = tag.getString("ShaftDiameter");
            this.shaftMaterial = switch (matName) {
                case "duralumin" -> ShaftMaterial.DURALUMIN;
                case "steel"     -> ShaftMaterial.STEEL;
                case "titanium"  -> ShaftMaterial.TITANIUM;
                case "tungsten_carbide" -> ShaftMaterial.TUNGSTEN_CARBIDE;
                default -> ShaftMaterial.IRON;
            };
            try {
                this.shaftDiameter = ShaftDiameter.valueOf(diaName);
            } catch (IllegalArgumentException e) {
                this.shaftDiameter = ShaftDiameter.MEDIUM;
            }
        }
    }

    // ===================== РЕНДЕР =====================

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D);
    }
}
