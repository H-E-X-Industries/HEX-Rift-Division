package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Объявленный боковой сокет привязки периферийных модулей (например, барабана труб).
 * Смещение задаётся относительно центра скважины в системе координат hostFacing == NORTH.
 */
public record LateralSocket(
        String id,
        RigModuleRole acceptedRole,
        BlockPos localOffsetFromWellCenter,
        Direction expectedRelativeFacing
) {
    public BlockPos resolveWorldPos(BlockPos wellCenterPos, Direction hostFacing) {
        return wellCenterPos.offset(MultiblockStructureHelper.rotate(localOffsetFromWellCenter, hostFacing));
    }

    public Direction resolveWorldFacing(Direction hostFacing) {
        BlockPos rotated = MultiblockStructureHelper.rotate(new BlockPos(expectedRelativeFacing.getNormal()), hostFacing);
        return Direction.getNearest(rotated.getX(), rotated.getY(), rotated.getZ());
    }
}
