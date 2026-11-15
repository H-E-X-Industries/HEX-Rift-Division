package com.trd.climate.calculation;

import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Связь мультиблоков с климатической системой.
 * <p>
 * Мультиблок — это контроллер (например, блок нагревателя) плюс блоки-парты.
 * Для температуры такой станок должен ощущаться как <b>единое целое</b>:
 * греет он всю конструкцию сразу, и касание любой её грани ощущается игроком.
 * <p>
 * Здесь собираются все блоки структуры (контроллер + парты), чтобы:
 * <ul>
 *   <li>источник тепла считался <b>один</b> на весь мультиблок, а не по источнику на каждую часть;</li>
 *   <li>расстояние до источника считалось до ближайшей части структуры;</li>
 *   <li>в отладочном оверлее подсвечивался весь мультиблок целиком.</li>
 * </ul>
 */
public final class MultiblockHeatHelper {

    private MultiblockHeatHelper() {
    }

    /**
     * Контроллер мультиблока для указанной позиции.
     * <p>
     * Если позиция — блок-парт, контроллер берётся у парта ({@code getControllerPos()}).
     *
     * @return позиция контроллера или {@code null}, если блок не часть мультиблока
     */
    public static BlockPos resolveControllerPos(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IMultiblockController) {
            return pos;
        }
        if (!state.hasBlockEntity()) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof com.trd.multiblock.system.roles.IMultiblockPart part
                && part.getControllerPos() != null) {
            return part.getControllerPos();
        }
        return null;
    }

    /**
     * Все блоки структуры мультиблока: контроллер и его части.
     * <p>
     * Если структура собрать не удалось (например, контроллер разобран),
     * возвращается список из одного контроллера.
     */
    public static List<BlockPos> structureCells(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            return List.of();
        }
        BlockState state = level.getBlockState(controllerPos);
        if (!(state.getBlock() instanceof IMultiblockController controller)) {
            return List.of(controllerPos.immutable());
        }
        try {
            MultiblockStructureHelper helper = controller.getStructureHelper();
            if (helper == null) {
                return List.of(controllerPos.immutable());
            }
            Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                    ? state.getValue(HorizontalDirectionalBlock.FACING)
                    : Direction.NORTH;

            List<BlockPos> cells = new ArrayList<>();
            cells.add(controllerPos.immutable());
            for (BlockPos grid : helper.getPartOffsets()) {
                BlockPos world = helper.getRotatedPos(controllerPos, grid, facing);
                if (world != null && !cells.contains(world)) {
                    cells.add(world.immutable());
                }
            }
            return Collections.unmodifiableList(cells);
        } catch (Throwable e) {
            // Структура может быть в промежуточном состоянии (разбирается) —
            // тогда считаем источником один только контроллер
            return List.of(controllerPos.immutable());
        }
    }
}
