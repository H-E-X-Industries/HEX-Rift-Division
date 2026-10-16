package com.trd.item.tools;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.block.entity.InsulationControllerBlockEntity;
import com.trd.climate.room.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class ScrewdriverItem extends Item {

    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static BlockPos getLinkedPos(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("linking") && tag.contains("ctrl_x")) {
            return new BlockPos(tag.getInt("ctrl_x"), tag.getInt("ctrl_y"), tag.getInt("ctrl_z"));
        }
        return null;
    }

    public static void setLinkedPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("linking", true);
        tag.putInt("ctrl_x", pos.getX());
        tag.putInt("ctrl_y", pos.getY());
        tag.putInt("ctrl_z", pos.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearLinking(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("linking");
        tag.remove("ctrl_x");
        tag.remove("ctrl_y");
        tag.remove("ctrl_z");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Преобразует позицию клика по внешней грани оболочки (полу, потолку, стене)
     * во внутреннюю координату комнаты (Interior Bounds).
     */
    public static BlockPos resolveInnerCorner(BlockPos clickedPos, Direction clickedFace, BlockPos ctrlPos) {
        if (clickedPos == null || ctrlPos == null || clickedFace == null) return clickedPos;
        int x = clickedPos.getX(), y = clickedPos.getY(), z = clickedPos.getZ();
        if (clickedFace == Direction.UP && y < ctrlPos.getY()) y++;
        else if (clickedFace == Direction.DOWN && y > ctrlPos.getY()) y--;
        else if (clickedFace == Direction.WEST && x > ctrlPos.getX()) x--;
        else if (clickedFace == Direction.EAST && x < ctrlPos.getX()) x++;
        else if (clickedFace == Direction.NORTH && z > ctrlPos.getZ()) z--;
        else if (clickedFace == Direction.SOUTH && z < ctrlPos.getZ()) z++;
        return new BlockPos(x, y, z);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        boolean isSneaking = player.isShiftKeyDown();

        // 1. Shift+ПКМ сбрасывает разметку куба
        if (isSneaking && getLinkedPos(stack) != null) {
            clearLinking(stack);
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.trd.insulation.selection_cancelled"), true);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 2. Клик по контроллеру термоизоляции — запоминаем первую точку куба
        if (state.is(ModBlocks.INSULATION_CONTROLLER.get())) {
            BlockPos currentLinked = getLinkedPos(stack);
            if (currentLinked != null && currentLinked.equals(pos)) {
                // Повторный клик по контроллеру отменяет режим
                clearLinking(stack);
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.trd.insulation.selection_cancelled"), true);
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            setLinkedPos(stack, pos);
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.trd.insulation.controller_selected",
                        pos.getX(), pos.getY(), pos.getZ()
                ), false);
                level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0f, 1.5f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 3. Если уже привязан контроллер — клик по противоположному углу куба
        BlockPos ctrlPos = getLinkedPos(stack);
        if (ctrlPos != null) {
            BlockPos targetPos = resolveInnerCorner(pos, context.getClickedFace(), ctrlPos);
            // Валидация помещения на сервере
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(ctrlPos);
                if (be instanceof InsulationControllerBlockEntity controller) {
                    ValidationResult result = controller.validateAndSeal(serverLevel, targetPos, player);
                    if (result.isSuccess()) {
                        clearLinking(stack);
                        player.displayClientMessage(Component.translatable(
                                "message.trd.insulation.sealed_success", result.volume()
                        ), false);
                        serverLevel.playSound(null, targetPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    } else {
                        BlockPos leak = result.leakPos();
                        if (leak != null) {
                            if (result.isDoor()) {
                                player.displayClientMessage(Component.literal(String.format(
                                        java.util.Locale.ROOT,
                                        "§cПомещение не герметично! Открыта дверь в [%d, %d, %d]",
                                        leak.getX(), leak.getY(), leak.getZ()
                                        )), false);
                            } else {
                                player.displayClientMessage(Component.literal(String.format(
                                        java.util.Locale.ROOT,
                                        "§cПомещение не герметично! Разгерметизация в [%d, %d, %d]",
                                        leak.getX(), leak.getY(), leak.getZ()
                                )), false);
                            }
                        } else {
                            player.displayClientMessage(Component.literal(
                                    result.errorMessage() != null ? "§c" + result.errorMessage() : "§cОшибка герметизации!"
                            ), false);
                        }
                        serverLevel.playSound(null, targetPos, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1.0f, 0.8f);
                    }
                } else {
                    clearLinking(stack);
                    player.displayClientMessage(Component.translatable("message.trd.insulation.controller_missing"), false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        BlockPos linked = getLinkedPos(stack);
        if (linked != null) {
            tooltipComponents.add(Component.translatable(
                    "tooltip.trd.screwdriver.linking",
                    linked.getX(), linked.getY(), linked.getZ()
            ).withStyle(ChatFormatting.GREEN));
            tooltipComponents.add(Component.translatable(
                    "tooltip.trd.screwdriver.cancel_hint"
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Проверяет соседние валы и разворачивает текущий, если нужно (фронт к фронту, зад к заду)
     * Логика: вал должен смотреть в ту же сторону что и сосед (продолжать линию)
     */
//    private void syncWithNeighbors(Level level, BlockPos pos, BlockState state) {
//        Direction myFacing = state.getValue(ShaftBlock.FACING);
//
//        // Проверяем соседей вдоль оси вала (фронт и тыл)
//        for (Direction dir : new Direction[]{myFacing, myFacing.getOpposite()}) {
//            BlockPos neighborPos = pos.relative(dir);
//            BlockState neighborState = level.getBlockState(neighborPos);
//
//            if (neighborState.getBlock() instanceof ShaftBlock) {
//                Direction neighborFacing = neighborState.getValue(ShaftBlock.FACING);
//
//                // Если сосед смотрит в ту же сторону — всё ок, ничего не делаем
//                // Если сосед смотрит в противоположную сторону — разворачиваемся
//                if (neighborFacing == myFacing.getOpposite()) {
//                    Direction correctedFacing = myFacing.getOpposite();
//                    BlockState correctedState = state.setValue(ShaftBlock.FACING, correctedFacing);
//                    level.setBlock(pos, correctedState, 3);
//                    return;
//                }
//            }
//        }
//    }

    /**
     * Поворачивает направление на 90°
     * - Горизонтальный взгляд → вращение вокруг Y
     * - Вертикальный взгляд → вращение вокруг X
     */
    private Direction rotate90(Direction current, Direction lookDir) {
        if (lookDir.getAxis() == Direction.Axis.Y) {
            // Смотрим вверх/вниз - вращаем вокруг X
            return rotateAroundX(current);
        } else {
            // Смотрим горизонтально - вращаем вокруг Y
            return rotateAroundY(current);
        }
    }

    private Direction rotateAroundY(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP, DOWN -> dir;
        };
    }

    private Direction rotateAroundX(Direction dir) {
        return switch (dir) {
            case UP -> Direction.SOUTH;
            case SOUTH -> Direction.DOWN;
            case DOWN -> Direction.NORTH;
            case NORTH -> Direction.UP;
            case EAST, WEST -> dir;
        };
    }

    private Direction getLookDirection(Player player) {
        float pitch = player.getXRot();
        float yaw = player.getYRot();

        if (pitch < -45) return Direction.UP;
        if (pitch > 45) return Direction.DOWN;

        float normalizedYaw = (yaw % 360 + 360) % 360;

        if (normalizedYaw < 45 || normalizedYaw >= 315) return Direction.SOUTH;
        if (normalizedYaw < 135) return Direction.WEST;
        if (normalizedYaw < 225) return Direction.NORTH;
        return Direction.EAST;
    }
}