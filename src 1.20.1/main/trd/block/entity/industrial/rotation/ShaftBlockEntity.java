package com.trd.block.entity.industrial.rotation;

import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.industrial.rotation.PulleyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ShaftBlockEntity extends KineticNodeBlockEntity {

    private ItemStack attachedPulley = ItemStack.EMPTY;
    private BlockPos connectedPulley = null;

    private ItemStack attachedGear = ItemStack.EMPTY;

    private ItemStack attachedBevelStart = ItemStack.EMPTY;
    private ItemStack attachedBevelEnd = ItemStack.EMPTY;
    private ItemStack attachedRotor = ItemStack.EMPTY;
    private ItemStack attachedFlywheel = ItemStack.EMPTY;

    public boolean hasRotor() {
        return !attachedRotor.isEmpty();
    }

    public com.trd.api.rotation.RotorType getRotorType() {
        if (hasRotor()) {
            return com.trd.api.rotation.RotorType.COPPER; // Hardcoded for now as only basic rotor exists
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

    /**
     * Проверяет, является ли этот вал T-образным узлом конических шестерней.
     * Т.е. проходит ли через позицию этого вала луч зубцов хотя бы одной конической
     * шестерни на соседнем перпендикулярном вале.
     *
     * Если да — на этот вал нельзя надевать центральные насадки (шестерни, шкивы, маховики, роторы).
     * Конические шестерни при этом могут стоять, но только на концах самого вала.
     */
    public boolean isBevelTJunctionShaft() {
        if (level == null || worldPosition == null) return false;
        if (!level.isLoaded(worldPosition)) return false;

        BlockState myState = getBlockState();
        if (!myState.hasProperty(ShaftBlock.FACING)) return false;
        Direction.Axis myAxis = myState.getValue(ShaftBlock.FACING).getAxis();

        // Проверяем все 4 перпендикулярных направления от этого вала
        for (Direction perpDir : Direction.values()) {
            if (perpDir.getAxis() == myAxis) continue; // вдоль оси — пропускаем

            // Сосед по перпендикуляру
            BlockPos neighborPos = worldPosition.relative(perpDir);
            if (!level.isLoaded(neighborPos)) continue;

            net.minecraft.world.level.block.entity.BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (!(neighborBE instanceof ShaftBlockEntity neighborShaft)) continue;

            // Если у соседа есть коническая шестерня и её луч проходит через нас
            Direction neighborBevelDir = neighborShaft.getBevelDirection();
            if (neighborBevelDir == null) continue;

            // Луч соседа: neighborPos → neighborPos.relative(neighborBevelDir)
            // Проходит ли этот луч через worldPosition?
            BlockPos neighborRayEnd = neighborPos.relative(neighborBevelDir);
            if (neighborRayEnd.equals(worldPosition)) {
                return true; // Луч конической шестерни соседа попадает прямо в нас!
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

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BE.get(), pos, state);
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
            // Флаг 2! Тихо обновляем данные на клиенте
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

    private java.util.List<BlockPos> getPulleysBetween(net.minecraft.world.level.Level level, BlockPos a, BlockPos b) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        int dx = Integer.compare(b.getX(), a.getX());
        int dy = Integer.compare(b.getY(), a.getY());
        int dz = Integer.compare(b.getZ(), a.getZ());

        // Только прямые линии
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)
            return list;

        BlockPos current = a.offset(dx, dy, dz);
        while (!current.equals(b)) {
            if (level.isLoaded(current)) {
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(current);
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
        // Шестерни (gear) позволяют распространение во все стороны (боковые зубья).
        // Конические шестерни — НЕТ: соединение идёт через getPotentialConnections,
        // поэтому пропагируем только вдоль оси вала.
        if (hasGear())
            return Direction.values();
        return new Direction[] { facing, facing.getOpposite() };
    }

    /**
     * Возвращает Direction зубцов конической шестерни этого вала.
     * HAS_BEVEL_START означает, что шестерня стоит на стороне с меньшей координатой (Negative)
     * HAS_BEVEL_END означает, что шестерня стоит на стороне с большей координатой (Positive)
     * Направление зубцов всегда указывает наружу (от центра вала).
     */
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
    public java.util.List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, BlockPos myPos) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING))
            return list;

        Direction facing = state.getValue(ShaftBlock.FACING);
        Direction.Axis axis = facing.getAxis();
        int gearSize = state.getValue(ShaftBlock.GEAR_SIZE);

        // 2. РЕМЕННЫЕ СВЯЗИ (Динамическое сканирование)
        if (this.hasPulley()) {
            if (this.connectedPulley != null) {
                // Цель ремня уже известна — добавляем её и промежуточные шкивы
                list.add(this.connectedPulley);
                list.addAll(getPulleysBetween(level, myPos, this.connectedPulley));
                // Пропускаем полный скан: при построении сети BFS дойдёт до нас с другого конца
            } else {
                // connectedPulley == null: полный скан для поиска ремней, указывающих НА НАС
                // Это выполняется только при первом построении сети или когда связь не установлена
                int radius = 16;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius) {
                                if (dx == 0 && dy == 0 && dz == 0) continue;
                                BlockPos scanPos = myPos.offset(dx, dy, dz);
                                if (level.isLoaded(scanPos)) {
                                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(scanPos);
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

        // 1. Осевые соединения (Вал-к-Валу). Всегда добавляем перед и зад.
        list.add(myPos.relative(facing));
        list.add(myPos.relative(facing.getOpposite()));

        // 2. Если есть шестерня, ищем соседей в плоскости
        if (gearSize > 0) {
            // Проверяем квадрат 5x5 вокруг шестерни
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
                        // Отсекаем блоки не в нашей плоскости
                        if (axis == Direction.Axis.X && pos.getX() != myPos.getX())
                            continue;
                        if (axis == Direction.Axis.Y && pos.getY() != myPos.getY())
                            continue;
                        if (axis == Direction.Axis.Z && pos.getZ() != myPos.getZ())
                            continue;

                        // Считаем дистанцию по осям плоскости
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
                        // Перпендикулярные оси (только для больших шестерней)
                        if (gearSize == 2 && otherSize == 2) {
                            int dx = Math.abs(pos.getX() - myPos.getX());
                            int dy = Math.abs(pos.getY() - myPos.getY());
                            int dz = Math.abs(pos.getZ() - myPos.getZ());

                            // Для перпендикулярных 2x2 шестерней: смещение по двум осям шестерней должно
                            // быть 1, а по третьей 0
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

        // 3. Конические шестерни (Bevel Gears) — поиск по лучу
        // Алгоритм: луч из нашей шестерни в направлении зубцов попадает в точку пересечения
        // (myPos + bevelDir). Затем сканируем 4 перпендикулярных позиции вокруг этой точки —
        // там могут стоять валы с конической шестернёй, луч которых тоже достигает этой точки.
        if (this.hasBevelStart() || this.hasBevelEnd()) {
            Direction bevelDir = getBevelDirection();
            if (bevelDir != null) {
                // Точка пересечения: 1 блок в направлении зубцов
                BlockPos intersectionPoint = myPos.relative(bevelDir);

                // Сканируем 4 перпендикулярных направления от точки пересечения
                for (Direction perpDir : Direction.values()) {
                    if (perpDir.getAxis() == bevelDir.getAxis()) continue; // только перпендикулярные

                    // Потенциальный партнёр находится по другую сторону от точки пересечения
                    // (т.е. его луч dirB = perpDir, и он идёт ОТ него К точке пересечения)
                    BlockPos partnerPos = intersectionPoint.relative(perpDir.getOpposite());
                    if (partnerPos.equals(myPos)) continue;
                    if (!level.isLoaded(partnerPos)) continue;

                    BlockState otherState = level.getBlockState(partnerPos);
                    if (!(otherState.getBlock() instanceof ShaftBlock)) continue;

                    boolean otherHasBevel = (otherState.hasProperty(ShaftBlock.HAS_BEVEL_START) && otherState.getValue(ShaftBlock.HAS_BEVEL_START))
                            || (otherState.hasProperty(ShaftBlock.HAS_BEVEL_END) && otherState.getValue(ShaftBlock.HAS_BEVEL_END));
                    if (!otherHasBevel) continue;

                    Direction.Axis otherAxis = otherState.getValue(ShaftBlock.FACING).getAxis();
                    // Партнёр должен быть строго перпендикулярного вала
                    if (otherAxis == bevelDir.getAxis()) continue;

                    list.add(partnerPos.immutable());
                }
            }
        }

        // 4. СТАТОРЫ: проверяем все 4 боковых стороны (перпендикулярно оси вала)
        // Статор должен смотреть FACING прямо на вал, чтобы попасть в его сеть
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == axis) continue; // пропускаем вдоль оси — там уже добавлены
            BlockPos sidePos = myPos.relative(dir);
            BlockState statorState = level.getBlockState(sidePos);
            if (statorState.getBlock() instanceof com.trd.block.basic.industrial.rotation.StatorBlock) {
                if (statorState.hasProperty(com.trd.block.basic.industrial.rotation.StatorBlock.FACING) && statorState.hasProperty(com.trd.block.basic.industrial.rotation.StatorBlock.AXIS)) {
                    Direction statorFacing = statorState.getValue(com.trd.block.basic.industrial.rotation.StatorBlock.FACING);
                    Direction.Axis statorAxis = statorState.getValue(com.trd.block.basic.industrial.rotation.StatorBlock.AXIS);
                    BlockPos holeOffset = com.trd.multiblock.system.MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), statorFacing, statorAxis);
                    if (sidePos.offset(holeOffset).equals(myPos)) {
                        list.add(sidePos);
                    }
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
                if (this.getAttachedPulley().getItem() instanceof PulleyItem p1 &&
                        neighborShaft.getAttachedPulley().getItem() instanceof PulleyItem p2) {
                    return (float) p1.getDiameterPixels() / p2.getDiameterPixels();
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

        // Соединение конических шестерней (Bevel Gears) — расчёт знака передачи
        // Коническая пара 1:1, знак определяется относительным расположением валов.
        // Физика: d1 = компонента (neighbor-my) вдоль оси A, d2 — вдоль оси B.
        // Если оба вала "расходятся" от точки пересечения → знак отрицательный (реверс),
        // если один к точке, другой от → положительный.
        if (myAxis != neighborAxis && (this.hasBevelStart() || this.hasBevelEnd())
                && (neighborShaft.hasBevelStart() || neighborShaft.hasBevelEnd())) {
            Direction dirA = this.getBevelDirection();
            Direction dirB = neighborShaft.getBevelDirection();
            if (dirA != null && dirB != null && dirA.getAxis() != dirB.getAxis()) {
                // Компонента вектора (B→A) вдоль оси A и вдоль оси B
                int dx = neighborPos.getX() - myPos.getX();
                int dy = neighborPos.getY() - myPos.getY();
                int dz = neighborPos.getZ() - myPos.getZ();
                int d1 = switch (myAxis) { case X -> dx; case Y -> dy; case Z -> dz; };
                int d2 = switch (neighborAxis) { case X -> dx; case Y -> dy; case Z -> dz; };
                double prod = (double) d1 * d2;
                if (Math.abs(prod) > 0.001) {
                    return (float) Math.signum(prod);
                }
                // Fallback: разные направления зубцов → реверс
                return -1.0f;
            }
        }

        if (myAxis != neighborAxis && mySize == 2 && neighborSize == 2) {
            // Перпендикулярное соединение (большие шестерни)
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

        // Если соединение по оси (вал-вал) - передача 1:1, знак не меняется
        if (myAxis == neighborAxis && (myPos.relative(myFacing).equals(neighborPos)
                || myPos.relative(myFacing.getOpposite()).equals(neighborPos))) {
            return 1.0f;
        }

        // Если соединение боковое (через зубья шестерней) - ЗНАК ВСЕГДА ИНВЕРТИРУЕТСЯ
        float ratio = -1.0f;

        // Считаем передаточное число
        if (mySize == 1 && neighborSize == 2) {
            ratio = -0.5f; // От малой к большой скорость падает в 2 раза
        } else if (mySize == 2 && neighborSize == 1) {
            ratio = -2.0f; // От большой к малой скорость возрастает в 2 раза
        }

        return ratio;
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (neighbor instanceof StatorBlockEntity) {
            return getPotentialConnections(level, myPos).contains(neighborPos);
        }

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

        // Проверяем, находятся ли блоки на одной геометрической линии перед/зад
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
                // Проверка соединения конических шестерней (Bevel Gears) — метод пересечения лучей
                // Принцип: каждая коническая шестерня испускает луч длиной 1 блок в направлении
                // зубцов. Соединение происходит ТОЛЬКО если оба луча встречаются в одной точке,
                // и оси валов строго перпендикулярны.
                //
                // Формула: myPos.relative(dirA) == neighborPos.relative(dirB)
                //
                // Это автоматически отсекает случай разрыва вала (два конца одной оси — у них
                // лучи параллельны и встречаются в одной точке, но оси НЕ перпендикулярны).
                if (myAxis != otherAxis && (this.hasBevelStart() || this.hasBevelEnd())
                        && (otherShaft.hasBevelStart() || otherShaft.hasBevelEnd())) {
                    Direction dirA = this.getBevelDirection();
                    Direction dirB = otherShaft.getBevelDirection();

                    if (dirA != null && dirB != null) {
                        // Строгая проверка: оси ДОЛЖНЫ быть перпендикулярны (уже гарантировано myAxis != otherAxis)
                        // и направления зубцов тоже не должны быть коллинеарны
                        if (dirA.getAxis() != dirB.getAxis()) {
                            BlockPos intersectionA = myPos.relative(dirA);
                            BlockPos intersectionB = neighborPos.relative(dirB);

                            if (intersectionA.equals(intersectionB)) {
                                // Лучи встретились — проверяем Т-образное пересечение:
                                // если точка пересечения — это вал с центральными насадками,
                                // соединение запрещено (нельзя надевать шестерни/шкивы на такой вал)
                                BlockPos intersection = intersectionA;
                                if (level != null && level.isLoaded(intersection)) {
                                    net.minecraft.world.level.block.entity.BlockEntity intBE = level.getBlockEntity(intersection);
                                    if (intBE instanceof ShaftBlockEntity intShaft) {
                                        if (intShaft.hasCentralAttachment()) {
                                            return false; // T-образный вал занят другой насадкой
                                        }
                                    }
                                }
                                return true;
                            }
                        }
                    }
                }

                // Боковое или диагональное соединение шестерней
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
                        return false; // Отключено по запросу
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
            boolean axisMatch = bearing.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.BearingBlock.FACING)
                    && bearing.getBlockState().getValue(com.trd.block.basic.industrial.rotation.BearingBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && bearing.hasShaft() && bearing.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof ClutchBlockEntity clutch) {
            boolean axisMatch = clutch.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.ClutchBlock.FACING)
                    && clutch.getBlockState().getValue(com.trd.block.basic.industrial.rotation.ClutchBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && clutch.hasShaft() && clutch.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof TachometerBlockEntity tach) {
            boolean axisMatch = tach.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.TachometerBlock.FACING)
                    && tach.getBlockState().getValue(com.trd.block.basic.industrial.rotation.TachometerBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && tach.hasShaft() && tach.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof MotorElectroBlockEntity motor) {
            boolean axisMatch = motor.getBlockState().hasProperty(com.trd.block.basic.industrial.rotation.MotorElectroBlock.FACING)
                    && motor.getBlockState().getValue(com.trd.block.basic.industrial.rotation.MotorElectroBlock.FACING).getAxis() == myAxis;
            return isCollinear && axisMatch && thisDiameter == ShaftDiameter.LIGHT;
        }
        if (neighbor instanceof StatorBlockEntity stator) {
            // Вал разрешает соединение со статором, если тот смотрит на вал
            return stator.canConnectMechanically(neighborPos, myPos, this);
        }
        return isCollinear;
    }


    // setSpeed, shouldSyncSpeed, setNetworkScale, getNetworkScale — унаследованы от KineticNodeBlockEntity

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); // speed, lastSyncedSpeed, networkScale

        if (!attachedGear.isEmpty())
            tag.put("AttachedGear", attachedGear.save(new CompoundTag()));
        if (!attachedBevelStart.isEmpty())
            tag.put("AttachedBevelStart", attachedBevelStart.save(new CompoundTag()));
        if (!attachedBevelEnd.isEmpty())
            tag.put("AttachedBevelEnd", attachedBevelEnd.save(new CompoundTag()));
        if (!attachedRotor.isEmpty())
            tag.put("AttachedRotor", attachedRotor.save(new CompoundTag()));
        if (!attachedFlywheel.isEmpty())
            tag.put("AttachedFlywheel", attachedFlywheel.save(new CompoundTag()));
        if (!attachedPulley.isEmpty())
            tag.put("AttachedPulley", attachedPulley.save(new CompoundTag()));
        if (connectedPulley != null)
            tag.put("ConnectedPulley", net.minecraft.nbt.NbtUtils.writeBlockPos(connectedPulley));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag); // speed, lastSyncedSpeed, networkScale

        this.attachedGear = tag.contains("AttachedGear") ? ItemStack.of(tag.getCompound("AttachedGear"))
                : ItemStack.EMPTY;
        this.attachedBevelStart = tag.contains("AttachedBevelStart")
                ? ItemStack.of(tag.getCompound("AttachedBevelStart"))
                : ItemStack.EMPTY;
        this.attachedBevelEnd = tag.contains("AttachedBevelEnd") ? ItemStack.of(tag.getCompound("AttachedBevelEnd"))
                : ItemStack.EMPTY;
        this.attachedRotor = tag.contains("AttachedRotor") ? ItemStack.of(tag.getCompound("AttachedRotor"))
                : ItemStack.EMPTY;
        this.attachedPulley = tag.contains("AttachedPulley") ? ItemStack.of(tag.getCompound("AttachedPulley"))
                : ItemStack.EMPTY;
        this.attachedFlywheel = tag.contains("AttachedFlywheel") ? ItemStack.of(tag.getCompound("AttachedFlywheel"))
                : ItemStack.EMPTY;
        this.connectedPulley = tag.contains("ConnectedPulley")
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("ConnectedPulley"))
                : null;

        if (this.attachedBevelStart.isEmpty() && getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_START) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_START)) {
            this.attachedBevelStart = new ItemStack(com.trd.item.ModItems.BEVEL_GEAR.get());
        }
        if (this.attachedBevelEnd.isEmpty() && getBlockState().hasProperty(ShaftBlock.HAS_BEVEL_END) && getBlockState().getValue(ShaftBlock.HAS_BEVEL_END)) {
            this.attachedBevelEnd = new ItemStack(com.trd.item.ModItems.BEVEL_GEAR.get());
        }
        if (this.attachedFlywheel.isEmpty() && getBlockState().hasProperty(ShaftBlock.HAS_FLYWHEEL) && getBlockState().getValue(ShaftBlock.HAS_FLYWHEEL)) {
            this.attachedFlywheel = new ItemStack(com.trd.item.ModItems.FLYWHEEL_LIGHT.get());
        }
    }

    // getUpdateTag, getUpdatePacket, onDataPacket, onLoad — унаследованы от KineticNodeBlockEntity

    @Override
    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING))
            return this.speed;

        Direction facing = state.getValue(ShaftBlock.FACING);
        // Инвертируем визуальную скорость для позитивных осей (правило правой руки)
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        BlockPos connectedPos = getConnectedPulley();
        net.minecraft.world.phys.AABB box = super.getRenderBoundingBox();
        
        if (this.hasGear()) {
            box = box.inflate(1.5D); 
        } else {
            box = box.inflate(0.5D);
        }
        
        if (connectedPos != null) {
            return box.minmax(new net.minecraft.world.phys.AABB(connectedPos)).inflate(1.5D);
        }
        return box;
    }

    // getSpeed() — унаследован от KineticNodeBlockEntity

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

    /**
     * Вал не потребляет момент напрямую — каждый статор сам декларирует своё потребление
     * через NodeRole.CONSUMER и getConsumedTorque() в StatorBlockEntity.
     */
    @Override
    public long getConsumedTorque() {
        return 0;
    }

    @Override
    public long getMaxTorqueTolerance() {
        return 1000;
    }
}