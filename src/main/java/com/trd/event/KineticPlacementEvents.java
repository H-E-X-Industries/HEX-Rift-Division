package com.trd.event;

import com.trd.api.rotation.BeltConnectionHelper;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class KineticPlacementEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor level = event.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos placedPos = event.getPos();
        BlockState placedState = event.getPlacedBlock();
        Block placedBlock = placedState.getBlock();

        // 1. Блокировка установки блоков в ремни
        if (isObstructingBelt(level, placedPos, placedState)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal("§cЭто место заблокировано ремнём!"), true);
            }
            return;
        }

        // 2. Сканирование зоны 3х3х3 для защиты габаритов большой/средней шестерни
        for (BlockPos neighborPos : BlockPos.betweenClosed(placedPos.offset(-1, -1, -1), placedPos.offset(1, 1, 1))) {
            if (neighborPos.equals(placedPos)) continue;

            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof ShaftBlock && neighborState.hasProperty(ShaftBlock.GEAR_SIZE)) {
                if (neighborState.getValue(ShaftBlock.GEAR_SIZE) == 2 && neighborState.hasProperty(ShaftBlock.FACING)) {
                    Direction.Axis axis = neighborState.getValue(ShaftBlock.FACING).getAxis();

                    int dx = Math.abs(placedPos.getX() - neighborPos.getX());
                    int dy = Math.abs(placedPos.getY() - neighborPos.getY());
                    int dz = Math.abs(placedPos.getZ() - neighborPos.getZ());

                    boolean inPlane = false;
                    int sum = 0;

                    if (axis == Direction.Axis.X && dx == 0) {
                        inPlane = true;
                        sum = dy + dz;
                    } else if (axis == Direction.Axis.Y && dy == 0) {
                        inPlane = true;
                        sum = dx + dz;
                    } else if (axis == Direction.Axis.Z && dz == 0) {
                        inPlane = true;
                        sum = dx + dy;
                    }

                    if (inPlane) {
                        if (sum == 1) {
                            // Крестовина: 100% заблокировано шестерней
                            event.setCanceled(true);
                            if (event.getEntity() instanceof Player player) {
                                player.displayClientMessage(Component.literal("§cЭто место заблокировано средней шестернёй!"), true);
                            }
                            return;
                        } else if (sum == 2) {
                            // Диагональ: Можно ставить только валы (чтобы можно было ставить маленькие шестерни)
                            if (!(placedBlock instanceof ShaftBlock)) {
                                event.setCanceled(true);
                                if (event.getEntity() instanceof Player player) {
                                    player.displayClientMessage(Component.literal("§cПо диагонали от средней шестерни можно ставить только валы!"), true);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isObstructingBelt(LevelAccessor level, BlockPos placedPos, BlockState placedState) {
        int radius = 16;
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (int du = -radius; du <= radius; du++) {
                for (int dv = -radius; dv <= radius; dv++) {
                    if (du == 0 && dv == 0) continue;
                    if (du * du + dv * dv > radius * radius) continue;

                    BlockPos scanPos;
                    if (axis == Direction.Axis.X) {
                        scanPos = placedPos.offset(0, du, dv);
                    } else if (axis == Direction.Axis.Y) {
                        scanPos = placedPos.offset(du, 0, dv);
                    } else {
                        scanPos = placedPos.offset(du, dv, 0);
                    }

                    if (!level.hasChunkAt(scanPos)) continue;
                    BlockEntity be = level.getBlockEntity(scanPos);
                    if (be instanceof ShaftBlockEntity shaft && shaft.hasPulley()) {
                        BlockPos target = shaft.getConnectedPulley();
                        if (target != null && !scanPos.equals(placedPos) && !target.equals(placedPos)) {
                            BlockState shaftState = shaft.getBlockState();
                            if (shaftState.getBlock() instanceof ShaftBlock && shaftState.hasProperty(ShaftBlock.FACING)) {
                                if (shaftState.getValue(ShaftBlock.FACING).getAxis() == axis) {
                                    boolean straight = BeltConnectionHelper.isPosBetween(placedPos, scanPos, target);
                                    boolean intersects = straight;
                                    if (!intersects) {
                                        AABB box = new AABB(placedPos);
                                        intersects = box.clip(Vec3.atCenterOf(scanPos), Vec3.atCenterOf(target)).isPresent();
                                    }

                                    if (intersects) {
                                        if (straight && placedState.getBlock() instanceof ShaftBlock
                                                && placedState.hasProperty(ShaftBlock.FACING)
                                                && placedState.getValue(ShaftBlock.FACING).getAxis() == axis) {
                                            // Прямолинейный вал с той же осью вращения может стоять в трассе ремня
                                            continue;
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
