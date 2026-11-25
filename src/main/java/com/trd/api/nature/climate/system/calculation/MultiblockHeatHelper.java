package com.trd.api.nature.climate.system.calculation;

import com.trd.multiblock.system.IMultiblockController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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

    /** Кэш структур мультиблоков: контроллер -> список его блоков. */
    private static final java.util.Map<BlockPos, CachedCells> CELL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Время жизни записи в кэше, мс. */
    private static final long CACHE_TTL_MS = 2000L;

    /** Запись кэша со временем последнего обновления. */
    private record CachedCells(List<BlockPos> cells, long stamp) {}

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
     * Части ищутся по блок-сущностям соседних чанков (у каждой есть ссылка на
     * свой контроллер) — <b>без обращения к {@code getStructureHelper()}</b>.
     * Тот метод создаёт структуру лениво и кэширует её в статическом поле
     * блока, поэтому вызов из другого потока (клиентский тик против рендера)
     * иногда приводил к падению. Здесь же такой риск убирается полностью.
     * <p>
     * Если структура собрать не удалось (например, контроллер разобран),
     * возвращается список из одного контроллера.
     */
    public static List<BlockPos> structureCells(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CachedCells cached = CELL_CACHE.get(controllerPos);
        if (cached != null && now - cached.stamp() < CACHE_TTL_MS) {
            return cached.cells();
        }

        List<BlockPos> cells = new ArrayList<>();
        cells.add(controllerPos.immutable());

        // Части мультиблока лежат рядом с контроллером: ищем их среди
        // блок-сущностей чанков вокруг
        int cx = controllerPos.getX() >> 4;
        int cz = controllerPos.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunkSource()
                        .getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity be : levelChunk.getBlockEntities().values()) {
                    if (be instanceof com.trd.multiblock.system.roles.IMultiblockPart part
                            && controllerPos.equals(part.getControllerPos())) {
                        BlockPos pos = be.getBlockPos();
                        if (!cells.contains(pos)) {
                            cells.add(pos.immutable());
                        }
                    }
                }
            }
        }

        List<BlockPos> result = Collections.unmodifiableList(cells);
        CELL_CACHE.put(controllerPos.immutable(), new CachedCells(result, now));
        // Чистим старые записи, чтобы кэш не рос бесконечно
        if (CELL_CACHE.size() > 256) {
            CELL_CACHE.entrySet().removeIf(e -> now - e.getValue().stamp() > CACHE_TTL_MS * 4);
        }
        return result;
    }
}
