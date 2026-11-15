package com.trd.api.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Реестр источников тепла климатической системы.
 * <p>
 * Хранит единственное описание каждого источника (см. {@link HeatSourceDef}) и
 * умеет находить его по блоку:
 * <ul>
 *   <li><b>по состоянию блока</b> — ванильные источники (костёр, печь, лава, огонь, лёд);</li>
 *   <li><b>по блок-сущности</b> — приборы мода, реализующие {@link IHeatSource}.</li>
 * </ul>
 * Реестр заполняется один раз при старте мода (клиент и сервер заполняют его одинаково),
 * поэтому клиентский дебаг-оверлей и серверные расчёты используют одни и те же числа.
 */
public final class HeatSourceRegistry {

    /**
     * Запись реестра: предикат состояния блока + описание источника.
     */
    public record Entry(Predicate<BlockState> stateTest, HeatSourceDef def) {}

    /** Описания по идентификатору — для блоков мода. */
    private static final Map<String, HeatSourceDef> BY_ID = new ConcurrentHashMap<>();

    /** Записи, сгруппированные по типу блока — быстрый поиск по состоянию. */
    private static final Map<Block, List<Entry>> BY_BLOCK = new ConcurrentHashMap<>();

    /** Типы блоков, у которых источник тепла определяется блок-сущностью. */
    private static final java.util.Set<Block> BLOCK_ENTITY_BLOCKS = ConcurrentHashMap.newKeySet();

    /** Максимальный радиус излучения среди всех зарегистрированных источников. */
    private static volatile float maxRadius = 0.0f;

    private HeatSourceRegistry() {
    }

    /**
     * Регистрирует источник, определяемый состоянием блока.
     * <p>
     * Предикат должен возвращать {@code true} только для <b>активного</b> источника
     * (зажжённый костёр, работающая печь), иначе мощность будет посчитана как нулевая.
     *
     * @param def       описание источника
     * @param stateTest проверка активного состояния блока
     */
    public static void register(HeatSourceDef def, Predicate<BlockState> stateTest) {
        BY_ID.put(def.id(), def);
        maxRadius = Math.max(maxRadius, def.radius());
    }

    /**
     * Регистрирует источник, определяемый состоянием блока, для конкретного типа блока.
     */
    public static void register(Block block, HeatSourceDef def, Predicate<BlockState> stateTest) {
        register(def, stateTest);
        BY_BLOCK.computeIfAbsent(block, k -> new ArrayList<>()).add(new Entry(stateTest, def));
    }

    /**
     * Регистрирует источник прибора мода (по идентификатору, без привязки к типу блока).
     * <p>
     * Блок-сущность сама сообщает свой идентификатор через {@link IHeatSource#getHeatSourceId()}.
     */
    public static void registerIdOnly(HeatSourceDef def) {
        BY_ID.put(def.id(), def);
        maxRadius = Math.max(maxRadius, def.radius());
    }

    /**
     * Регистрирует источник прибора мода, указывая тип блока.
     * <p>
     * Тип блока нужен только для оптимизации: без него при поиске источников
     * пришлось бы спрашивать блок-сущность у <b>каждого</b> блока в области сканирования.
     */
    public static void registerIdOnly(Block block, HeatSourceDef def) {
        registerIdOnly(def);
        if (block != null) {
            BLOCK_ENTITY_BLOCKS.add(block);
        }
    }

    /**
     * Нужно ли для этого блока проверять блок-сущность (а не только состояние блока).
     */
    public static boolean usesBlockEntity(Block block) {
        return block != null && BLOCK_ENTITY_BLOCKS.contains(block);
    }

    /**
     * Описание источника по идентификатору ({@code "trd:heater"}).
     */
    public static HeatSourceDef byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /**
     * Ищет описание источника по состоянию блока (ванильные источники).
     */
    public static HeatSourceDef resolveState(BlockState state) {
        if (state == null) {
            return null;
        }
        List<Entry> entries = BY_BLOCK.get(state.getBlock());
        if (entries == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.stateTest().test(state)) {
                return entry.def();
            }
        }
        return null;
    }

    /**
     * Разрешает источник в произвольной точке мира.
     * <p>
     * Сначала проверяются блоки мода (по {@link IHeatSource}), затем — ванильные источники
     * по состоянию блока.
     *
     * @return описание источника или {@code null}, если источника нет
     */
    public static HeatSourceDef resolveAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (usesBlockEntity(state.getBlock())
                && level.getBlockEntity(pos) instanceof IHeatSource heatSource) {
            HeatSourceDef def = byId(heatSource.getHeatSourceId());
            if (def != null) {
                return def;
            }
        }
        return resolveState(state);
    }

    /**
     * Может ли этот блок вообще быть источником тепла.
     * <p>
     * Быстрая отсевка при массовом сканировании мира: дешевле проверить
     * тип блока по хеш-таблице, чем доставать блок-сущность и прогонять
     * предикаты состояния у каждого блока подряд.
     */
    public static boolean isPotentialSource(Block block) {
        return block != null && (BY_BLOCK.containsKey(block) || BLOCK_ENTITY_BLOCKS.contains(block));
    }

    /**
     * Максимальный радиус излучения среди всех источников — размер области сканирования.
     */
    public static float maxRadius() {
        return maxRadius;
    }
}
