package com.trd.climate.calculation;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Единый реестр блоков, взаимодействующих с системой тепла.
 * Здесь каждому источнику назначаются температура, радиус, теплопроводность
 * и параметры объёмного нагрева помещений. <b>Весь баланс тепловой системы
 * правится только в этом файле.</b>
 *
 * <h2>Что означают параметры</h2>
 * <pre>
 * def(id, maxHeat, radius, conductivity, sourceTemp, roomTarget, roomPower,
 *     cooling, negligibleInLargeRooms, tempMin, tempMax)
 * </pre>
 * <ul>
 *   <li><b>id</b> — уникальный идентификатор источника ({@code "trd:heater"}).
 *       По нему блок-сущность прибора сообщает, что она источник тепла
 *       ({@link com.trd.api.climate.IHeatSource#getHeatSourceId()}).</li>
 *   <li><b>maxHeat</b> — <b>потолок выхода</b>: сколько градусов источник может
 *       добавить соседним блокам у самой поверхности, °C. Нужен, чтобы обычный
 *       костёр не разогревал комнату до 400 °C.</li>
 *   <li><b>radius</b> — максимальный радиус излучения, блоков (при 100% мощности).
 *       Итоговый радиус = radius × (0.25 + 0.75 × мощность): слабый источник
 *       греет только вплотную, раскалённый — далеко.</li>
 *   <li><b>conductivity</b> — <b>теплопроводность</b> 0..1: доля температуры
 *       самого источника, доходящая до соседей (0.1 при 0, 0.9 при 1).
 *       Медный котёл отдаёт больше, чем доломитовый нагреватель.</li>
 *   <li><b>sourceTemp</b> — температура источника, °C (лава 1200, костёр 700,
 *       доменная печь 1400, лёд 0). Это и потолок нагрева блока: соседний блок
 *       не может нагреться выше температуры источника. У приборов мода берётся
 *       их текущая температура, это значение — запасной вариант.</li>
 *   <li><b>roomTarget</b> — до какой температуры источник прогревает
 *       <b>герметичную комнату</b>, °C.</li>
 *   <li><b>roomPower</b> — скорость выхода комнаты на эту температуру
 *       (единиц в секунду при 100% мощности источника).</li>
 *   <li><b>cooling</b> — true для холодильников (лёд, снег): источник не
 *       греет, а забирает тепло.</li>
 *   <li><b>negligibleInLargeRooms</b> — true, если источник физически не может
 *       прогреть большую пещеру (костёр, факел).</li>
 *   <li><b>tempMin / tempMax</b> — шкала температур прибора: при tempMin °C
 *       мощность 0%, при tempMax °C — 100%. Для ванильных блоков обе = 1:
 *       там активность задана состоянием блока (зажжён/не зажжён).</li>
 * </ul>
 *
 * <p>
 * Ванильные источники определяются по состоянию блока (зажжён/не зажжён),
 * приборы мода — по блок-сущности, реализующей
 * {@link com.trd.api.climate.IHeatSource} (температура берётся у самого прибора).
 */
public final class ModHeatSources {

    private ModHeatSources() {
    }

    /**
     * Заполняет реестр. Вызывается один раз при старте мода на клиенте и на сервере.
     */
    public static void registerAll() {
        registerVanilla();
        registerModMachines();
    }

    // ═══════════════════════════════════════════════════════════
    // ВАНИЛЬНЫЕ ИСТОЧНИКИ
    // ═══════════════════════════════════════════════════════════

    private static void registerVanilla() {
        // ── Костры ─────────────────────────────────────────────
        // 700 °C, но потолок 45 — костёр греет соседей на десятки градусов, не на сотни
        HeatSourceRegistry.register(Blocks.CAMPFIRE, def("minecraft:campfire",
                        45.0f, 8.0f, 0.60f, 700.0f, 24.0f, 3.6f, false, true, 1.0f, 1.0f),
                state -> state.getValue(CampfireBlock.LIT));

        HeatSourceRegistry.register(Blocks.SOUL_CAMPFIRE, def("minecraft:soul_campfire",
                        40.0f, 8.0f, 0.55f, 700.0f, 20.0f, 2.4f, false, true, 1.0f, 1.0f),
                state -> state.getValue(CampfireBlock.LIT));

        // ── Факелы ────────────────────────────────────────────
        // Локального жара почти не дают (тепло в руке считается отдельно),
        // но в герметичном помещении подогревают воздух.
        HeatSourceRegistry.register(Blocks.TORCH, def("minecraft:torch",
                        0.0f, 3.0f, 0.30f, 800.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.WALL_TORCH, def("minecraft:wall_torch",
                        0.0f, 3.0f, 0.30f, 800.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.SOUL_TORCH, def("minecraft:soul_torch",
                        0.0f, 3.0f, 0.30f, 800.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.SOUL_WALL_TORCH, def("minecraft:soul_wall_torch",
                        0.0f, 3.0f, 0.30f, 800.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);

        // ── Печи ──────────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.FURNACE, def("minecraft:furnace",
                        150.0f, 6.0f, 0.50f, 900.0f, 34.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        HeatSourceRegistry.register(Blocks.SMOKER, def("minecraft:smoker",
                        150.0f, 6.0f, 0.50f, 900.0f, 34.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        HeatSourceRegistry.register(Blocks.BLAST_FURNACE, def("minecraft:blast_furnace",
                        200.0f, 6.0f, 0.55f, 1400.0f, 45.0f, 5.0f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        // ── Лава ──────────────────────────────────────────────
        // 1200 °C и отдаёт 86% своего тепла — у кромки лавы реально жарко.
        HeatSourceRegistry.register(Blocks.LAVA, def("minecraft:lava",
                        500.0f, 6.0f, 0.95f, 1200.0f, 68.0f, 7.0f, false, false, 1.0f, 1.0f),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        HeatSourceRegistry.register(Blocks.LAVA, def("minecraft:flowing_lava",
                        300.0f, 4.0f, 0.90f, 1200.0f, 45.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // ── Огонь ─────────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.FIRE, def("minecraft:fire",
                        130.0f, 5.0f, 0.60f, 900.0f, 55.0f, 4.0f, false, false, 1.0f, 1.0f),
                state -> true);

        // ── Магма-блок ────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.MAGMA_BLOCK, def("minecraft:magma_block",
                        120.0f, 4.0f, 0.70f, 700.0f, 38.0f, 2.5f, false, false, 1.0f, 1.0f),
                state -> true);

        // ── Охладители ────────────────────────────────────────
        // Для холодильников maxHeat — сколько градусов они забирают у окружения.
        HeatSourceRegistry.register(Blocks.ICE, def("minecraft:ice",
                        8.0f, 4.0f, 0.50f, 0.0f, 8.0f, 0.6f, true, false, 1.0f, 1.0f),
                state -> true);

        HeatSourceRegistry.register(Blocks.PACKED_ICE, def("minecraft:packed_ice",
                        11.0f, 4.0f, 0.45f, -18.0f, 0.0f, 1.0f, true, false, 1.0f, 1.0f),
                state -> true);

        HeatSourceRegistry.register(Blocks.BLUE_ICE, def("minecraft:blue_ice",
                        15.0f, 4.5f, 0.40f, -50.0f, -10.0f, 2.0f, true, false, 1.0f, 1.0f),
                state -> true);
    }

    // ═══════════════════════════════════════════════════════════
    // ПРИБОРЫ МОДА
    // ═══════════════════════════════════════════════════════════

    private static void registerModMachines() {
        // Твердотопливный нагреватель из доломитовых кирпичей: 0..1600 °C.
        // Доломит держит тепло плохо: при 1000 °C соседние блоки получают
        // около 380 °C (0.38 × 1000), при 1600 °C — потолок 600 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(), def("trd:heater",
                600.0f, 8.0f, 0.35f, 1600.0f, 55.0f, 5.0f, false, false, 200.0f, 1600.0f));

        // Медный котёл: 20..600 °C. Медь проводит тепло лучше доломита,
        // поэтому при той же температуре котёл греет окружение сильнее.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(), def("trd:boiler",
                400.0f, 7.0f, 0.60f, 600.0f, 48.0f, 4.0f, false, false, 100.0f, 600.0f));

        // Плавильня: 0..1600 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(), def("trd:smelter",
                600.0f, 8.0f, 0.40f, 1600.0f, 58.0f, 5.0f, false, false, 300.0f, 1600.0f));

        // Коксовая печь: 0..2700 °C — самая горячая.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(), def("trd:coking_oven",
                900.0f, 8.0f, 0.45f, 2700.0f, 65.0f, 6.0f, false, false, 300.0f, 2700.0f));

        // Малая плавильня: 0..1100 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(), def("trd:small_smelter",
                450.0f, 6.0f, 0.45f, 1100.0f, 45.0f, 4.0f, false, false, 200.0f, 1100.0f));

        // Нагреватель химического завода: 0 / 50 / 100 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(), def("trd:chemical_plant_heater",
                120.0f, 5.0f, 0.60f, 100.0f, 32.0f, 2.5f, false, false, 50.0f, 100.0f));

        // Электрическая печь: температуры нет, работает по состоянию LIT.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(), def("trd:electro_furnace",
                250.0f, 7.0f, 0.50f, 1200.0f, 40.0f, 3.5f, false, false, 1.0f, 1.0f));
    }

    /**
     * Создаёт описание источника. Подробное описание параметров — в javadoc класса.
     *
     * @param maxHeat       потолок выхода источника у поверхности, °C
     * @param radius        максимальный радиус излучения, блоков
     * @param conductivity  теплопроводность 0..1 (доля температуры, отдаваемая окружению)
     * @param sourceTemp    температура источника, °C
     * @param roomTarget    температура, до которой источник прогревает герметичную комнату, °C
     * @param roomPower     скорость выхода комнаты на эту температуру
     * @param cooling       true — источник холода
     * @param negligibleInLargeRooms true — не греет большие помещения
     * @param tempMin       температура прибора, дающая 0% мощности
     * @param tempMax       температура прибора, дающая 100% мощности
     */
    private static HeatSourceDef def(String id,
                                     float maxHeat,
                                     float radius,
                                     float conductivity,
                                     float sourceTemp,
                                     float roomTarget,
                                     float roomPower,
                                     boolean cooling,
                                     boolean negligibleInLargeRooms,
                                     float tempMin,
                                     float tempMax) {
        return new HeatSourceDef(id, maxHeat, radius, conductivity, sourceTemp, roomTarget, roomPower,
                cooling, negligibleInLargeRooms, tempMin, tempMax);
    }

    /**
     * Проверка, что блок является ванильным источником тепла (для диагностики).
     */
    public static boolean isVanillaSource(BlockState state) {
        return HeatSourceRegistry.resolveState(state) != null;
    }
}
