package com.trd.api.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class PathMath {

    /**
     * Вычисляет позицию и поворот предмета (yaw) на конвейерной ленте,
     * учитывая возможные повороты на стыке блоков.
     *
     * @param prevPos Позиция предыдущего блока (откуда приехал)
     * @param currentPos Позиция текущего блока
     * @param nextPos Позиция следующего блока (куда едет)
     * @param localProgress Прогресс внутри текущего блока (от 0.0 до 1.0)
     * @return Массив [x, y, z, rotY]
     */
    public static double[] calculatePathPoint(BlockPos prevPos, BlockPos currentPos, BlockPos nextPos, double localProgress, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level) {
        Vec3 center = Vec3.atCenterOf(currentPos).add(0, 2.0 / 16.0, 0);

        if (state != null && state.getBlock() instanceof com.trd.block.basic.industrial.ConveyorElevatorBlock) {
            com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart part = state.getValue(com.trd.block.basic.industrial.ConveyorElevatorBlock.PART);
            Direction facing = state.getValue(com.trd.block.basic.industrial.ConveyorBlock.FACING);
            double baseRotY = facing.toYRot();
            double trackOffset = 2.5 / 16.0; // 2.5 pixels towards the input side (back)
            
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.BOTTOM) {
                if (localProgress <= 0.5) {
                    double fraction = localProgress / 0.5;
                    double currentOffset = 0.5 - fraction * (0.5 - trackOffset);
                    double offsetZ = facing.getOpposite().getStepZ() * currentOffset;
                    double offsetX = facing.getOpposite().getStepX() * currentOffset;
                    return new double[]{center.x + offsetX, center.y, center.z + offsetZ, baseRotY};
                } else {
                    double offsetY = (localProgress - 0.5);
                    double offsetZ = facing.getOpposite().getStepZ() * trackOffset;
                    double offsetX = facing.getOpposite().getStepX() * trackOffset;
                    return new double[]{center.x + offsetX, center.y + offsetY, center.z + offsetZ, baseRotY};
                }
            } else if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.MIDDLE) {
                double offsetY = -0.5 + localProgress;
                double offsetZ = facing.getOpposite().getStepZ() * trackOffset;
                double offsetX = facing.getOpposite().getStepX() * trackOffset;
                return new double[]{center.x + offsetX, center.y + offsetY, center.z + offsetZ, baseRotY};
            } else if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.TOP) {
                if (localProgress <= 0.5) {
                    double offsetY = -0.5 + localProgress;
                    double offsetZ = facing.getOpposite().getStepZ() * trackOffset;
                    double offsetX = facing.getOpposite().getStepX() * trackOffset;
                    return new double[]{center.x + offsetX, center.y + offsetY, center.z + offsetZ, baseRotY};
                } else {
                    double fraction = (localProgress - 0.5) / 0.5;
                    double currentOffset = -trackOffset + fraction * (0.5 + trackOffset);
                    double offsetX = facing.getStepX() * currentOffset;
                    double offsetZ = facing.getStepZ() * currentOffset;
                    return new double[]{center.x + offsetX, center.y, center.z + offsetZ, baseRotY};
                }
            }
        }

        Direction inDir = getDirectionFromTo(prevPos, currentPos);
        Direction outDir = getDirectionFromTo(currentPos, nextPos);

        if (inDir == null) inDir = outDir != null ? outDir : Direction.NORTH;
        if (outDir == null) outDir = inDir;

        // Прямой участок
        if (inDir == outDir) {
            double offsetX = outDir.getStepX() * (localProgress - 0.5);
            double offsetZ = outDir.getStepZ() * (localProgress - 0.5);
            return new double[]{center.x + offsetX, center.y, center.z + offsetZ, outDir.toYRot()};
        }

        // Поворот (угол 90 градусов)
        // Используем кривую Безье (квадратичную) для плавного поворота
        Vec3 p0 = center.add(inDir.getStepX() * -0.5, 0, inDir.getStepZ() * -0.5); // Точка входа
        Vec3 p1 = center; // Контрольная точка (центр блока)
        Vec3 p2 = center.add(outDir.getStepX() * 0.5, 0, outDir.getStepZ() * 0.5); // Точка выхода

        // Формула Безье: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
        double t = localProgress;
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;

        double x = uu * p0.x + 2 * u * t * p1.x + tt * p2.x;
        double y = center.y;
        double z = uu * p0.z + 2 * u * t * p1.z + tt * p2.z;

        // Вычисляем касательную (производную Безье) для поворота предмета
        double dx = 2 * u * (p1.x - p0.x) + 2 * t * (p2.x - p1.x);
        double dz = 2 * u * (p1.z - p0.z) + 2 * t * (p2.z - p1.z);
        double rotY = Math.toDegrees(Math.atan2(-dx, dz));

        return new double[]{x, y, z, rotY};
    }

    private static Direction getDirectionFromTo(BlockPos from, BlockPos to) {
        if (from == null || to == null) return null;
        for (Direction dir : Direction.values()) {
            if (from.relative(dir).equals(to)) return dir;
        }
        return null;
    }
}
