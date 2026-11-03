package com.trd.event;

import com.trd.api.rotation.BeltConnectionHelper;
import com.trd.block.basic.industrial.rotation.BearingBlock;
import com.trd.block.basic.industrial.rotation.ClutchBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.basic.industrial.rotation.TachometerBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class KineticPlacementEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        Player player = event.getEntity();
        LevelAccessor level = event.getLevel();
        if (level == null) return;

        BlockHitResult hitResult = event.getHitVec();
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        // Если игрок кликает без Shift валом по подшипнику, сцеплению или тахометру,
        // в который вставляется вал — это вставка вала через useItemOn, а не размещение блока
        if (!player.isShiftKeyDown() && blockItem.getBlock() instanceof ShaftBlock) {
            if (clickedState.getBlock() instanceof BearingBlock && !clickedState.getValue(BearingBlock.HAS_SHAFT)) {
                return;
            }
            if (clickedState.getBlock() instanceof ClutchBlock && !clickedState.getValue(ClutchBlock.HAS_SHAFT)) {
                return;
            }
            if (clickedState.getBlock() instanceof TachometerBlock && !clickedState.getValue(TachometerBlock.HAS_SHAFT)) {
                return;
            }
        }

        BlockPlaceContext placeCtx = new BlockPlaceContext(new UseOnContext(player, event.getHand(), hitResult));
        placeCtx = blockItem.updatePlacementContext(placeCtx);
        if (placeCtx == null || !placeCtx.canPlace()) return;

        BlockPos targetPos = placeCtx.getClickedPos();
        Block placedBlock = blockItem.getBlock();

        // Безопасное получение состояния без вызова placedBlock.getStateForPlacement(placeCtx),
        // чтобы исключить ложные сообщения валидации опор ("Слишком длинный пролёт")
        BlockState placedState = placedBlock.defaultBlockState();
        if (placedBlock instanceof ShaftBlock && placedState.hasProperty(ShaftBlock.FACING)) {
            Direction facing = placeCtx.getNearestLookingDirection().getOpposite();
            if (clickedState.getBlock() instanceof ShaftBlock && clickedState.hasProperty(ShaftBlock.FACING)) {
                facing = clickedState.getValue(ShaftBlock.FACING);
            }
            placedState = placedState.setValue(ShaftBlock.FACING, facing);
        }

        // 1. Блокировка установки блоков в ремни (клиент + сервер до предикции)
        if (isObstructingBelt(level, targetPos, placedState)) {
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (level.isClientSide()) {
                player.displayClientMessage(Component.literal("§cЭто место заблокировано ремнём!"), true);
            }
            return;
        }

        // 2. Сканирование зоны 3х3х3 для защиты габаритов большой/средней шестерни
        String gearMsg = getGearObstruction(level, targetPos, placedBlock);
        if (gearMsg != null) {
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (level.isClientSide()) {
                player.displayClientMessage(Component.literal(gearMsg), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor level = event.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos placedPos = event.getPos();
        BlockState placedState = event.getPlacedBlock();
        Block placedBlock = placedState.getBlock();

        // 1. Блокировка установки блоков в ремни (серверный предохранитель)
        if (isObstructingBelt(level, placedPos, placedState)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal("§cЭто место заблокировано ремнём!"), true);
            }
            return;
        }

        // 2. Сканирование зоны 3х3х3 для защиты габаритов большой/средней шестерни
        String gearMsg = getGearObstruction(level, placedPos, placedBlock);
        if (gearMsg != null) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal(gearMsg), true);
            }
        }
    }

    public static String getGearObstruction(LevelAccessor level, BlockPos placedPos, Block placedBlock) {
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
                            return "§cЭто место заблокировано средней шестернёй!";
                        } else if (sum == 2) {
                            // Диагональ: Можно ставить только валы (чтобы можно было ставить маленькие шестерни)
                            if (!(placedBlock instanceof ShaftBlock)) {
                                return "§cПо диагонали от средней шестерни можно ставить только валы!";
                            }
                        }
                    }
                }
            }
        }
        return null;
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
