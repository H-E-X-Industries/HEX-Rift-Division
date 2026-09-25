package com.trd.block.entity.industrial.rotation;

import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.block.basic.industrial.rotation.BearingBlock;
import com.trd.block.basic.industrial.rotation.HandCrankBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class ShaftBlockEntity extends KineticNodeBlockEntity {

    private ItemStack attachedPulley = ItemStack.EMPTY;
    private BlockPos connectedPulley = null;

    private ItemStack attachedGear = ItemStack.EMPTY;

    private ItemStack attachedBevelStart = ItemStack.EMPTY;
    private ItemStack attachedBevelEnd = ItemStack.EMPTY;
    private ItemStack attachedRotor = ItemStack.EMPTY;
    private ItemStack attachedFlywheel = ItemStack.EMPTY;

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BE.get(), pos, state);
    }

    public boolean hasRotor() {
        return !attachedRotor.isEmpty();
    }

    public com.trd.api.rotation.RotorType getRotorType() {
        if (hasRotor()) {
            return com.trd.api.rotation.RotorType.COPPER;
        }
        return null;
    }

    public ItemStack getAttachedRotor() {
        return attachedRotor;
    }

    public void setAttachedRotor(ItemStack rotor) {
        this.attachedRotor = rotor;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean hasFlywheel() {
        return !attachedFlywheel.isEmpty() || (getBlockState().hasProperty(ShaftBlock.HAS_FLYWHEEL) && getBlockState().getValue(ShaftBlock.HAS_FLYWHEEL));
    }

    public boolean hasCentralAttachment() {
        return hasGear() || hasPulley() || hasRotor() || hasFlywheel();
    }

    public boolean isBevelTJunctionShaft() {
        if (level == null || worldPosition == null) return false;
        if (!level.isLoaded(worldPosition)) return false;

        BlockState myState = getBlockState();
        if (!myState.hasProperty(ShaftBlock.FACING)) return false;
        Direction.Axis myAxis = myState.getValue(ShaftBlock.FACING).getAxis();

        for (Direction perpDir : Direction.values()) {
            if (perpDir.getAxis() == myAxis) continue;

            BlockPos neighborPos = worldPosition.relative(perpDir);
            if (!level.isLoaded(neighborPos)) continue;

            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (!(neighborBE instanceof ShaftBlockEntity neighborShaft)) continue;

            Direction neighborBevelDir = neighborShaft.getBevelDirection();
            if (neighborBevelDir == null) continue;

            BlockPos neighborRayEnd = neighborPos.relative(neighborBevelDir);
            if (neighborRayEnd.equals(worldPosition)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getAttachedFlywheel() {
        return attachedFlywheel;
    }

    public void setAttachedFlywheel(ItemStack flywheel) {
        this.attachedFlywheel = flywheel;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean hasGear() {
        return !attachedGear.isEmpty() || (getBlockState().hasProperty(ShaftBlock.GEAR_SIZE) && getBlockState().getValue(ShaftBlock.GEAR_SIZE) > 0);
    }

    public ItemStack getAttachedGear() {
        return attachedGear;
    }

    public boolean hasPulley() {
        return !attachedPulley.isEmpty() || (getBlockState().hasProperty(ShaftBlock.PULLEY_SIZE) && getBlockState().getValue(ShaftBlock.PULLEY_SIZE) > 0);
    }

    public ItemStack getAttachedPulley() {
        return attachedPulley;
    }

    public void setAttachedPulley(ItemStack pulley) {
        this.attachedPulley = pulley;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public BlockPos getConnectedPulley() {
        return connectedPulley;
    }

    public void setConnectedPulley(BlockPos pos) {
        this.connectedPulley = pos;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void setAttachedGear(ItemStack gear) {
        this.attachedGear = gear;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean hasBevelStart() {
        return !attachedBevelStart.isEmpty() || (getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_START) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_START));
    }

    public ItemStack getAttachedBevelStart() {
        return attachedBevelStart;
    }

    public void setAttachedBevelStart(ItemStack bevel) {
        this.attachedBevelStart = bevel;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean hasBevelEnd() {
        return !attachedBevelEnd.isEmpty() || (getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_END) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_END));
    }

    public ItemStack getAttachedBevelEnd() {
        return attachedBevelEnd;
    }

    public void setAttachedBevelEnd(ItemStack bevel) {
        this.attachedBevelEnd = bevel;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private boolean isPosBetween(BlockPos mid, BlockPos a, BlockPos b) {
        if (a.getX() == b.getX() && mid.getX() == a.getX() && a.getY() == b.getY() && mid.getY() == a.getY()) {
            return (mid.getZ() > Math.min(a.getZ(), b.getZ())) && (mid.getZ() < Math.max(a.getZ(), b.getZ()));
        }
        if (a.getX() == b.getX() && mid.getX() == a.getX() && a.getZ() == b.getZ() && mid.getZ() == a.getZ()) {
            return (mid.getY() > Math.min(a.getY(), b.getY())) && (mid.getY() < Math.max(a.getY(), b.getY()));
        }
        if (a.getY() == b.getY() && mid.getY() == a.getY() && a.getZ() == b.getZ() && mid.getZ() == a.getZ()) {
            return (mid.getX() > Math.min(a.getX(), b.getX())) && (mid.getX() < Math.max(a.getX(), b.getX()));
        }
        return false;
    }

    private List<BlockPos> getPulleysBetween(net.minecraft.world.level.Level level, BlockPos a, BlockPos b) {
        List<BlockPos> list = new ArrayList<>();
        int dx = Integer.compare(b.getX(), a.getX());
        int dy = Integer.compare(b.getY(), a.getY());
        int dz = Integer.compare(b.getZ(), a.getZ());

        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)
            return list;

        BlockPos current = a.offset(dx, dy, dz);
        while (!current.equals(b)) {
            if (level.isLoaded(current)) {
                BlockEntity be = level.getBlockEntity(current);
                if (be instanceof ShaftBlockEntity shaft && shaft.hasPulley()) {
                    list.add(current.immutable());
                }
            }
            current = current.offset(dx, dy, dz);
        }
        return list;
    }

    @Override
    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING))
            return new Direction[0];
        Direction facing = state.getValue(ShaftBlock.FACING);
        if (hasGear())
            return Direction.values();
        return new Direction[] { facing, facing.getOpposite() };
    }

    @org.jetbrains.annotations.Nullable
    public Direction getBevelDirection() {
        if (!hasBevelStart() && !hasBevelEnd()) return null;
        Direction.Axis axis = getBlockState().getValue(ShaftBlock.FACING).getAxis();
        if (hasBevelStart()) {
            return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        } else {
            return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        }
    }

    @Override
    public List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, BlockPos myPos) {
        List<BlockPos> list = new ArrayList<>();
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING))
            return list;

        Direction facing = state.getValue(ShaftBlock.FACING);
        Direction.Axis axis = facing.getAxis();
        int gearSize = state.getValue(ShaftBlock.GEAR_SIZE);

        if (this.hasPulley()) {
            if (this.connectedPulley != null) {
                list.add(this.connectedPulley);
                list.addAll(getPulleysBetween(level, myPos, this.connectedPulley));
            } else {
                int radius = 16;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius) {
                                if (dx == 0 && dy == 0 && dz == 0) continue;
                                BlockPos scanPos = myPos.offset(dx, dy, dz);
                                if (level.isLoaded(scanPos)) {
                                    BlockEntity be = level.getBlockEntity(scanPos);
                                    if (be instanceof ShaftBlockEntity otherShaft && otherShaft.hasPulley()) {
                                        BlockPos theirTarget = otherShaft.getConnectedPulley();
                                        if (theirTarget != null) {
                                            if (theirTarget.equals(myPos) || isPosBetween(myPos, scanPos, theirTarget)) {
                                                list.add(scanPos);
                                                if (!theirTarget.equals(myPos)) {
                                                    list.add(theirTarget);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        list.add(myPos.relative(facing));
        list.add(myPos.relative(facing.getOpposite()));

        if (gearSize > 0) {
            for (BlockPos pos : BlockPos.betweenClosed(myPos.offset(-2, -2, -2), myPos.offset(2, 2, 2))) {
                if (pos.equals(myPos))
                    continue;

                BlockState otherState = level.getBlockState(pos);
                if (otherState.getBlock() instanceof ShaftBlock) {
                    int otherSize = otherState.hasProperty(ShaftBlock.GEAR_SIZE) ? otherState.getValue(ShaftBlock.GEAR_SIZE) : 0;
                    if (otherSize <= 0)
                        continue;

                    Direction.Axis otherAxis = otherState.getValue(ShaftBlock.FACING).getAxis();

                    if (axis == otherAxis) {
                        if (axis == Direction.Axis.X && pos.getX() != myPos.getX())
                            continue;
                        if (axis == Direction.Axis.Y && pos.getY() != myPos.getY())
                            continue;
                        if (axis == Direction.Axis.Z && pos.getZ() != myPos.getZ())
                            continue;

                        int d1 = 0, d2 = 0;
                        if (axis == Direction.Axis.X) {
                            d1 = Math.abs(pos.getY() - myPos.getY());
                            d2 = Math.abs(pos.getZ() - myPos.getZ());
                        }
                        if (axis == Direction.Axis.Y) {
                            d1 = Math.abs(pos.getX() - myPos.getX());
                            d2 = Math.abs(pos.getZ() - myPos.getZ());
                        }
                        if (axis == Direction.Axis.Z) {
                            d1 = Math.abs(pos.getX() - myPos.getX());
                            d2 = Math.abs(pos.getY() - myPos.getY());
                        }

                        if (gearSize == 1) {
                            if (otherSize == 1 && ((d1 == 1 && d2 == 0) || (d1 == 0 && d2 == 1)))
                                list.add(pos.immutable());
                            if (otherSize == 2 && d1 == 1 && d2 == 1)
                                list.add(pos.immutable());
                        } else if (gearSize == 2) {
                            if (otherSize == 1 && d1 == 1 && d2 == 1)
                                list.add(pos.immutable());
                        }
                    } else {
                        if (gearSize == 2 && otherSize == 2) {
                            int dx = Math.abs(pos.getX() - myPos.getX());
                            int dy = Math.abs(pos.getY() - myPos.getY());
                            int dz = Math.abs(pos.getZ() - myPos.getZ());

                            if (axis != Direction.Axis.X && otherAxis != Direction.Axis.X && dx != 0)
                                continue;
                            if (axis != Direction.Axis.Y && otherAxis != Direction.Axis.Y && dy != 0)
                                continue;
                            if (axis != Direction.Axis.Z && otherAxis != Direction.Axis.Z && dz != 0)
                                continue;

                            if (axis == Direction.Axis.X || otherAxis == Direction.Axis.X) {
                                if (dx != 1)
                                    continue;
                            }
                            if (axis == Direction.Axis.Y || otherAxis == Direction.Axis.Y) {
                                if (dy != 1)
                                    continue;
                            }
                            if (axis == Direction.Axis.Z || otherAxis == Direction.Axis.Z) {
                                if (dz != 1)
                                    continue;
                            }

                            list.add(pos.immutable());
                        }
                    }
                }
            }
        }

        if (this.hasBevelStart() || this.hasBevelEnd()) {
            Direction bevelDir = getBevelDirection();
            if (bevelDir != null) {
                BlockPos intersectionPoint = myPos.relative(bevelDir);

                for (Direction perpDir : Direction.values()) {
                    if (perpDir.getAxis() == bevelDir.getAxis()) continue;

                    BlockPos partnerPos = intersectionPoint.relative(perpDir.getOpposite());
                    if (partnerPos.equals(myPos)) continue;
                    if (!level.isLoaded(partnerPos)) continue;

                    BlockState otherState = level.getBlockState(partnerPos);
                    if (!(otherState.getBlock() instanceof ShaftBlock)) continue;

                    boolean otherHasBevel = (otherState.hasProperty(ShaftBlock.HAS_BEVEL_START) && otherState.getValue(ShaftBlock.HAS_BEVEL_START))
                            || (otherState.hasProperty(ShaftBlock.HAS_BEVEL_END) && otherState.getValue(ShaftBlock.HAS_BEVEL_END));
                    if (!otherHasBevel) continue;

                    Direction.Axis otherAxis = otherState.getValue(ShaftBlock.FACING).getAxis();
                    if (otherAxis == bevelDir.getAxis()) continue;

                    list.add(partnerPos.immutable());
                }
            }
        }

        return list;
    }

    @Override
    public float calculateTransmissionRatio(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (this.hasPulley() && neighbor instanceof ShaftBlockEntity neighborShaft && neighborShaft.hasPulley()) {
            boolean isBeltConnection = neighborPos.equals(this.connectedPulley);

            if (!isBeltConnection && neighborShaft.getConnectedPulley() != null) {
                BlockPos theirTarget = neighborShaft.getConnectedPulley();
                if (theirTarget.equals(myPos) || isPosBetween(myPos, neighborPos, theirTarget)) {
                    isBeltConnection = true;
                }
            }
            if (!isBeltConnection && this.connectedPulley != null) {
                if (isPosBetween(neighborPos, myPos, this.connectedPulley)) {
                    isBeltConnection = true;
                }
            }

            if (isBeltConnection) {
                if (this.getAttachedPulley().getItem() instanceof com.trd.item.industrial.rotation.PulleyItem p1 &&
                        neighborShaft.getAttachedPulley().getItem() instanceof com.trd.item.industrial.rotation.PulleyItem p2) {
                    return (float) p1.getDiameterPixels() / p2.getDiameterPixels();
                }
                int p1 = this.getBlockState().hasProperty(ShaftBlock.PULLEY_SIZE) ? this.getBlockState().getValue(ShaftBlock.PULLEY_SIZE) : 1;
                int p2 = neighborShaft.getBlockState().hasProperty(ShaftBlock.PULLEY_SIZE) ? neighborShaft.getBlockState().getValue(ShaftBlock.PULLEY_SIZE) : 1;
                if (p2 > 0) {
                    return (float) p1 / p2;
                }
            }
        }

        if (!(neighbor instanceof ShaftBlockEntity neighborShaft))
            return 1.0f;

        int mySize = this.getBlockState().getValue(ShaftBlock.GEAR_SIZE);
        int neighborSize = neighborShaft.getBlockState().getValue(ShaftBlock.GEAR_SIZE);

        Direction myFacing = getBlockState().getValue(ShaftBlock.FACING);
        Direction neighborFacing = neighborShaft.getBlockState().getValue(ShaftBlock.FACING);
        Direction.Axis myAxis = myFacing.getAxis();
        Direction.Axis neighborAxis = neighborFacing.getAxis();

        if (myAxis != neighborAxis && (this.hasBevelStart() || this.hasBevelEnd())
                && (neighborShaft.hasBevelStart() || neighborShaft.hasBevelEnd())) {
            Direction dirA = this.getBevelDirection();
            Direction dirB = neighborShaft.getBevelDirection();
            if (dirA != null && dirB != null && dirA.getAxis() != dirB.getAxis()) {
                int dx = neighborPos.getX() - myPos.getX();
                int dy = neighborPos.getY() - myPos.getY();
                int dz = neighborPos.getZ() - myPos.getZ();
                int d1 = switch (myAxis) { case X -> dx; case Y -> dy; case Z -> dz; };
                int d2 = switch (neighborAxis) { case X -> dx; case Y -> dy; case Z -> dz; };
                double prod = (double) d1 * d2;
                if (Math.abs(prod) > 0.001) {
                    return (float) Math.signum(prod);
                }
                return -1.0f;
            }
        }

        if (myAxis != neighborAxis && mySize == 2 && neighborSize == 2) {
            int diff1 = 0, diff2 = 0;
            if (myAxis == Direction.Axis.X)
                diff1 = neighborPos.getX() - myPos.getX();
            if (myAxis == Direction.Axis.Y)
                diff1 = neighborPos.getY() - myPos.getY();
            if (myAxis == Direction.Axis.Z)
                diff1 = neighborPos.getZ() - myPos.getZ();

            if (neighborAxis == Direction.Axis.X)
                diff2 = neighborPos.getX() - myPos.getX();
            if (neighborAxis == Direction.Axis.Y)
                diff2 = neighborPos.getY() - myPos.getY();
            if (neighborAxis == Direction.Axis.Z)
                diff2 = neighborPos.getZ() - myPos.getZ();

            return (float) (Math.signum(diff1) * Math.signum(diff2));
        }

        if (myAxis == neighborAxis && (myPos.relative(myFacing).equals(neighborPos)
                || myPos.relative(myFacing.getOpposite()).equals(neighborPos))) {
            return 1.0f;
        }

        float ratio = -1.0f;

        if (mySize == 1 && neighborSize == 2) {
            ratio = -0.5f;
        } else if (mySize == 2 && neighborSize == 1) {
            ratio = -2.0f;
        }

        return ratio;
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (this.hasPulley() && neighbor instanceof ShaftBlockEntity neighborShaft && neighborShaft.hasPulley()) {
            if (neighborPos.equals(this.connectedPulley))
                return true;
            if (neighborShaft.getConnectedPulley() != null) {
                BlockPos theirTarget = neighborShaft.getConnectedPulley();
                if (theirTarget.equals(myPos) || isPosBetween(myPos, neighborPos, theirTarget))
                    return true;
            }
            if (this.connectedPulley != null && isPosBetween(neighborPos, myPos, this.connectedPulley))
                return true;
        }

        ShaftDiameter thisDiameter = ((ShaftBlock) this.getBlockState().getBlock()).getDiameter();
        Direction thisFacing = getBlockState().getValue(ShaftBlock.FACING);
        Direction.Axis myAxis = thisFacing.getAxis();

        boolean isCollinear = myPos.relative(thisFacing).equals(neighborPos) ||
                myPos.relative(thisFacing.getOpposite()).equals(neighborPos);

        if (neighbor instanceof ShaftBlockEntity otherShaft) {
            Direction otherFacing = otherShaft.getBlockState().getValue(ShaftBlock.FACING);
            ShaftDiameter otherDiameter = ((ShaftBlock) otherShaft.getBlockState().getBlock()).getDiameter();
            Direction.Axis otherAxis = otherFacing.getAxis();

            boolean isEndToEnd = isCollinear && (myAxis == otherAxis);

            if (isEndToEnd) {
                return thisDiameter == otherDiameter;
            } else {
                if (myAxis != otherAxis && (this.hasBevelStart() || this.hasBevelEnd())
                        && (otherShaft.hasBevelStart() || otherShaft.hasBevelEnd())) {
                    Direction dirA = this.getBevelDirection();
                    Direction dirB = otherShaft.getBevelDirection();

                    if (dirA != null && dirB != null) {
                        if (dirA.getAxis() != dirB.getAxis()) {
                            BlockPos intersectionA = myPos.relative(dirA);
                            BlockPos intersectionB = neighborPos.relative(dirB);

                            if (intersectionA.equals(intersectionB)) {
                                BlockPos intersection = intersectionA;
                                if (level != null && level.isLoaded(intersection)) {
                                    BlockEntity intBE = level.getBlockEntity(intersection);
                                    if (intBE instanceof ShaftBlockEntity intShaft) {
                                        if (intShaft.hasCentralAttachment()) {
                                            return false;
                                        }
                                    }
                                }
                                return true;
                            }
                        }
                    }
                }

                if (!this.hasGear() || !otherShaft.hasGear())
                    return false;

                if (myAxis == otherAxis) {
                    int mySize = this.getBlockState().getValue(ShaftBlock.GEAR_SIZE);
                    int otherSize = otherShaft.getBlockState().getValue(ShaftBlock.GEAR_SIZE);

                    int d1 = 0, d2 = 0;
                    if (myAxis == Direction.Axis.X) {
                        d1 = Math.abs(neighborPos.getY() - myPos.getY());
                        d2 = Math.abs(neighborPos.getZ() - myPos.getZ());
                    }
                    if (myAxis == Direction.Axis.Y) {
                        d1 = Math.abs(neighborPos.getX() - myPos.getX());
                        d2 = Math.abs(neighborPos.getZ() - myPos.getZ());
                    }
                    if (myAxis == Direction.Axis.Z) {
                        d1 = Math.abs(neighborPos.getX() - myPos.getX());
                        d2 = Math.abs(neighborPos.getY() - myPos.getY());
                    }

                    if (mySize == 1 && otherSize == 1) {
                        return (d1 == 1 && d2 == 0) || (d1 == 0 && d2 == 1);
                    } else if (mySize == 2 && otherSize == 2) {
                        return false;
                    } else {
                        return d1 == 1 && d2 == 1;
                    }
                } else {
                    int mySize = this.getBlockState().getValue(ShaftBlock.GEAR_SIZE);
                    int otherSize = otherShaft.getBlockState().getValue(ShaftBlock.GEAR_SIZE);

                    if (mySize == 2 && otherSize == 2) {
                        int dx = Math.abs(neighborPos.getX() - myPos.getX());
                        int dy = Math.abs(neighborPos.getY() - myPos.getY());
                        int dz = Math.abs(neighborPos.getZ() - myPos.getZ());

                        if (myAxis != Direction.Axis.X && otherAxis != Direction.Axis.X && dx != 0)
                            return false;
                        if (myAxis != Direction.Axis.Y && otherAxis != Direction.Axis.Y && dy != 0)
                            return false;
                        if (myAxis != Direction.Axis.Z && otherAxis != Direction.Axis.Z && dz != 0)
                            return false;

                        if (myAxis == Direction.Axis.X || otherAxis == Direction.Axis.X) {
                            if (dx != 1)
                                return false;
                        }
                        if (myAxis == Direction.Axis.Y || otherAxis == Direction.Axis.Y) {
                            if (dy != 1)
                                return false;
                        }
                        if (myAxis == Direction.Axis.Z || otherAxis == Direction.Axis.Z) {
                            if (dz != 1)
                                return false;
                        }

                        return true;
                    }
                }
            }
        }
        if (neighbor instanceof BearingBlockEntity bearing) {
            boolean axisMatch = bearing.getBlockState().hasProperty(BearingBlock.FACING)
                    && bearing.getBlockState().getValue(BearingBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && bearing.hasShaft() && bearing.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof ClutchBlockEntity clutch) {
            boolean axisMatch = clutch.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.ClutchBlock.FACING)
                    && clutch.getBlockState().getValue(com.trd.block.basic.industrial.rotation.ClutchBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && clutch.hasShaft() && clutch.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof MotorElectroBlockEntity motor) {
            boolean axisMatch = motor.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.MotorElectroBlock.FACING)
                    && motor.getBlockState().getValue(com.trd.block.basic.industrial.rotation.MotorElectroBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && thisDiameter == ShaftDiameter.LIGHT;
        }
        if (neighbor instanceof HandCrankBlockEntity) {
            return isCollinear && thisDiameter == ShaftDiameter.LIGHT;
        }
        return isCollinear;
    }

    @Override
    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING)) return 0;
        Direction facing = state.getValue(ShaftBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        if (!attachedGear.isEmpty())
            tag.put("AttachedGear", attachedGear.saveOptional(provider));
        if (!attachedBevelStart.isEmpty())
            tag.put("AttachedBevelStart", attachedBevelStart.saveOptional(provider));
        if (!attachedBevelEnd.isEmpty())
            tag.put("AttachedBevelEnd", attachedBevelEnd.saveOptional(provider));
        if (!attachedRotor.isEmpty())
            tag.put("AttachedRotor", attachedRotor.saveOptional(provider));
        if (!attachedFlywheel.isEmpty())
            tag.put("AttachedFlywheel", attachedFlywheel.saveOptional(provider));
        if (!attachedPulley.isEmpty())
            tag.put("AttachedPulley", attachedPulley.saveOptional(provider));
        if (connectedPulley != null)
            tag.put("ConnectedPulley", NbtUtils.writeBlockPos(connectedPulley));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        this.attachedGear = tag.contains("AttachedGear") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedGear")) : ItemStack.EMPTY;
        this.attachedBevelStart = tag.contains("AttachedBevelStart") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedBevelStart")) : ItemStack.EMPTY;
        this.attachedBevelEnd = tag.contains("AttachedBevelEnd") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedBevelEnd")) : ItemStack.EMPTY;
        this.attachedRotor = tag.contains("AttachedRotor") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedRotor")) : ItemStack.EMPTY;
        this.attachedPulley = tag.contains("AttachedPulley") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedPulley")) : ItemStack.EMPTY;
        this.attachedFlywheel = tag.contains("AttachedFlywheel") ? ItemStack.parseOptional(provider, tag.getCompound("AttachedFlywheel")) : ItemStack.EMPTY;
        this.connectedPulley = tag.contains("ConnectedPulley") ? NbtUtils.readBlockPos(tag, "ConnectedPulley").orElse(null) : null;

        if (this.attachedBevelStart.isEmpty() && getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_START) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_START)) {
            this.attachedBevelStart = new ItemStack(com.trd.item.ModItems.BEVEL_GEAR.get());
        }
        if (this.attachedBevelEnd.isEmpty() && getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_END) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_END)) {
            this.attachedBevelEnd = new ItemStack(com.trd.item.ModItems.BEVEL_GEAR.get());
        }
        if (this.attachedGear.isEmpty() && getBlockState().hasProperty(ShaftBlock.GEAR_SIZE) && getBlockState().getValue(ShaftBlock.GEAR_SIZE) > 0) {
            int gSize = getBlockState().getValue(ShaftBlock.GEAR_SIZE);
            this.attachedGear = new ItemStack(gSize == 2 ? com.trd.item.ModItems.GEAR2_STEEL.get() : com.trd.item.ModItems.GEAR1_STEEL.get());
        }
        if (this.attachedPulley.isEmpty() && getBlockState().hasProperty(ShaftBlock.PULLEY_SIZE) && getBlockState().getValue(ShaftBlock.PULLEY_SIZE) > 0) {
            this.attachedPulley = new ItemStack(com.trd.item.ModItems.PULLEY.get());
        }
    }

    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);
        if (connectedPulley != null) {
            return box.minmax(new AABB(connectedPulley)).inflate(1.5D);
        }
        return box.inflate(1.0D);
    }

    @Override
    public long getTorque() {
        return 0;
    }

    @Override
    public long getMaxSpeed() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) (shaft.getMaterial().baseSpeed() * shaft.getDiameter().getSpeedMultiplier());
        }
        return 256;
    }

    @Override
    public long getMaxTorque() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) (shaft.getMaterial().baseTorque() * shaft.getDiameter().getTorqueMultiplier());
        }
        return 1024;
    }

    @Override
    public double getInertiaContribution() {
        double inertia = 0.0;
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            inertia = (double) (shaft.getMaterial().baseInertia() * shaft.getDiameter().inertiaMod);
        }

        if (hasFlywheel()) {
            inertia += 500.0;
        }

        return inertia;
    }

    @Override
    public long getConsumedTorque() {
        return 0;
    }

    @Override
    public long getMaxTorqueTolerance() {
        return 1000;
    }
}
