package com.trd.multiblock.system;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class MultiblockBlockItem extends BlockItem {

    public MultiblockBlockItem(Block block, Properties properties) {
        super(block, properties);
        // Защита от дурака: этот Item можно использовать только с блоками-контроллерами
        if (!(block instanceof IMultiblockController)) {
            throw new IllegalArgumentException("MultiblockBlockItem can only be used with blocks that implement IMultiblockController!");
        }
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        IMultiblockController controller = (IMultiblockController) this.getBlock();
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // 1. Проверяем помехи от энергетических проводов
        if (controller.getStructureHelper().hasWireObstruction(level, context.getClickedPos(), state)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.trd.multiblock.wire_obstructed"), true);
            }
            return false;
        }

        // 2. Проверяем свободное место для структуры мультиблока
        boolean canPlace;
        if (state.hasProperty(BlockStateProperties.AXIS) && state.hasProperty(BlockStateProperties.FACING)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            Direction facing = state.getValue(BlockStateProperties.FACING);
            canPlace = controller.getStructureHelper().checkPlacementStator(level, context.getClickedPos(), facing, axis, player);
        } else if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            canPlace = controller.getStructureHelper().checkPlacement(level, context.getClickedPos(), axis, player);
        } else if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            canPlace = controller.getStructureHelper().checkPlacement(level, context.getClickedPos(), facing, player);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            canPlace = controller.getStructureHelper().checkPlacement(level, context.getClickedPos(), facing, player);
        } else {
            // Без направления (например, центрифуги)
            canPlace = controller.getStructureHelper().checkPlacement(level, context.getClickedPos(), Direction.NORTH, player);
        }

        if (canPlace) {
            return super.placeBlock(context, state);
        } else {
            return false; // Места нет - блок не ставится, предмет не тратится!
        }
    }
}
