package com.trd.climate.calculation;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Регистрация всех источников тепла и холода климатической системы.
 * <p>
 * До появления этого класса таблицы источников дублировались в четырёх местах
 * ({@code HeatSourceHelper}, {@code RoomClimateHelper}, дебаг-оверлей и мёртвый
 * {@code calculateHeatBalance}), и числа в них расходились в 2–3 раза.
 * Теперь все источники описаны здесь и используются всеми подсистемами одинаково.
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
        // ── Костр ─────────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.CAMPFIRE, def("minecraft:campfire",
                        28.0f, 6.0f, 26.0f, 24.0f, 3.6f, false, true, 1.0f, 1.0f),
                state -> state.getValue(CampfireBlock.LIT));

        HeatSourceRegistry.register(Blocks.SOUL_CAMPFIRE, def("minecraft:soul_campfire",
                        21.0f, 6.0f, 24.0f, 20.0f, 2.4f, false, true, 1.0f, 1.0f),
                state -> state.getValue(CampfireBlock.LIT));

        // ── Факелы ────────────────────────────────────────────
        // Локального жара почти не дают (тепло в руке считается отдельно),
        // но в герметичном помещении подогревают воздух.
        HeatSourceRegistry.register(Blocks.TORCH, def("minecraft:torch",
                        0.0f, 2.0f, 22.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.WALL_TORCH, def("minecraft:wall_torch",
                        0.0f, 2.0f, 22.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.SOUL_TORCH, def("minecraft:soul_torch",
                        0.0f, 2.0f, 22.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);
        HeatSourceRegistry.register(Blocks.SOUL_WALL_TORCH, def("minecraft:soul_wall_torch",
                        0.0f, 2.0f, 22.0f, 18.0f, 0.4f, false, true, 1.0f, 1.0f),
                state -> true);

        // ── Печи ──────────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.FURNACE, def("minecraft:furnace",
                        20.0f, 4.0f, 32.0f, 34.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        HeatSourceRegistry.register(Blocks.SMOKER, def("minecraft:smoker",
                        20.0f, 4.0f, 32.0f, 34.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        HeatSourceRegistry.register(Blocks.BLAST_FURNACE, def("minecraft:blast_furnace",
                        24.0f, 4.0f, 40.0f, 45.0f, 5.0f, false, false, 1.0f, 1.0f),
                state -> state.getValue(AbstractFurnaceBlock.LIT));

        // ── Лава ──────────────────────────────────────────────
        // Источник лавы и текущая лава — разные источники: текущая заметно слабее.
        HeatSourceRegistry.register(Blocks.LAVA, def("minecraft:lava",
                        35.0f, 5.0f, 60.0f, 68.0f, 7.0f, false, false, 1.0f, 1.0f),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        HeatSourceRegistry.register(Blocks.LAVA, def("minecraft:flowing_lava",
                        18.0f, 3.5f, 40.0f, 45.0f, 3.5f, false, false, 1.0f, 1.0f),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // ── Огонь ─────────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.FIRE, def("minecraft:fire",
                        22.0f, 3.5f, 35.0f, 55.0f, 4.0f, false, false, 1.0f, 1.0f),
                state -> true);

        // ── Магма-блок ────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.MAGMA_BLOCK, def("minecraft:magma_block",
                        14.0f, 3.0f, 30.0f, 38.0f, 2.5f, false, false, 1.0f, 1.0f),
                state -> true);

        // ── Охладители ────────────────────────────────────────
        HeatSourceRegistry.register(Blocks.ICE, def("minecraft:ice",
                        6.0f, 2.5f, 8.0f, 8.0f, 0.6f, true, false, 1.0f, 1.0f),
                state -> true);

        HeatSourceRegistry.register(Blocks.PACKED_ICE, def("minecraft:packed_ice",
                        8.0f, 2.5f, 0.0f, 0.0f, 1.0f, true, false, 1.0f, 1.0f),
                state -> true);

        HeatSourceRegistry.register(Blocks.BLUE_ICE, def("minecraft:blue_ice",
                        12.0f, 3.0f, -10.0f, -10.0f, 2.0f, true, false, 1.0f, 1.0f),
                state -> true);
    }

    // ═══════════════════════════════════════════════════════════
    // ПРИБОРЫ МОДА
    // ═══════════════════════════════════════════════════════════

    private static void registerModMachines() {
        // Твердотопливный нагреватель: 0..1600 °C, греет сильнее костра.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(), def("trd:heater",
                40.0f, 5.0f, 45.0f, 55.0f, 5.0f, false, false, 200.0f, 1600.0f));

        // Котёл: 20..600 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(), def("trd:boiler",
                32.0f, 4.0f, 38.0f, 48.0f, 4.0f, false, false, 100.0f, 600.0f));

        // Плавильня: 0..1600 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(), def("trd:smelter",
                40.0f, 5.0f, 45.0f, 58.0f, 5.0f, false, false, 300.0f, 1600.0f));

        // Коксовая печь: 0..2700 °C — самая горячая.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(), def("trd:coking_oven",
                45.0f, 5.0f, 50.0f, 65.0f, 6.0f, false, false, 300.0f, 2700.0f));

        // Малая плавильня: 0..1100 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(), def("trd:small_smelter",
                30.0f, 4.0f, 36.0f, 45.0f, 4.0f, false, false, 200.0f, 1100.0f));

        // Нагреватель химического завода: 0 / 50 / 100 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(), def("trd:chemical_plant_heater",
                18.0f, 3.0f, 28.0f, 32.0f, 2.5f, false, false, 50.0f, 100.0f));

        // Электрическая печь: температуры нет, работает по состоянию LIT.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(), def("trd:electro_furnace",
                26.0f, 4.0f, 32.0f, 40.0f, 3.5f, false, false, 1.0f, 1.0f));
    }

    /**
     * Создаёт описание источника.
     * <p>
     * Для ванильных источников {@code tempMin == tempMax == 1}: предикат состояния
     * уже отфильтровал «негорящие» блоки, а мощность приходит из состояния блока.
     */
    private static HeatSourceDef def(String id,
                                     float maxHeat,
                                     float radius,
                                     float comfortCap,
                                     float roomTarget,
                                     float roomPower,
                                     boolean cooling,
                                     boolean negligibleInLargeRooms,
                                     float tempMin,
                                     float tempMax) {
        return new HeatSourceDef(id, maxHeat, radius, comfortCap, roomTarget, roomPower,
                cooling, negligibleInLargeRooms, tempMin, tempMax);
    }

    /**
     * Проверка, что блок является ванильным источником тепла (для диагностики).
     */
    public static boolean isVanillaSource(BlockState state) {
        return HeatSourceRegistry.resolveState(state) != null;
    }
}
