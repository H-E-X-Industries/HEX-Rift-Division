package com.trd.climate.calculation;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * <b>Стержень тепловой системы.</b> Все числа всех источников живут только здесь —
 * по образцу {@code com.trd.api.metallurgy.ModMetallurgy}.
 * <p>
 * Регистрация идёт через {@link HeatSourceDef#builder(String)}: каждое поле имеет имя,
 * поэтому нельзя случайно перепутать набор позиционных float. Поля {@code role} и {@code kind}
 * задают три оси из спецификации: роль (нагреватель/охладитель), тип (активный/пассивный)
 * и состояние (физичный/нефизичный — последнее живёт в
 * {@link com.trd.climate.room.ThermalStateStore}).
 *
 * <h2>Паспорт: правдоподобие вместо «страшных» долей</h2>
 * Температура источника берётся из нижней границы реального диапазона (закон +
 * игровая условность), а не ради красивой большой цифры:
 * <ul>
 *   <li>магма — 450 °C (реальный диапазон 450..1650 °C);</li>
 *   <li>лава — 550 °C (карбонатная лава, самая холодная и вязкая, 510..600 °C);</li>
 *   <li>костёр и факел — 100 °C (тлеющие угли, а не ядро пламени);</li>
 *   <li>огонь — 300 °C, печь — 300 °C, доменная — 500 °C.</li>
 * </ul>
 * При такой паспортной температуре честная доля отдачи
 * ({@code conductivity = выход / температура}) получается человеческой: 2.5..8% вместо
 * пугавших раньше 0.9%. Игрок видит «Магма, 450 °C, 2.7%» и спокойно умножает:
 * ~12 °C у блока.
 * <p>
 * У активных приборов в паспорте пишется <b>макс. температура поверхности</b>: огнеупорная
 * кладка держит корпус намного холоднее внутреннего объёма (1600 °C внутри нагревателя,
 * но ~400 °C снаружи). Внутренняя шкала остаётся в {@code deviceScale} и в самой машине —
 * она нужна для плавки (сталь 1440 °C) и не трогается. Conductivity по-прежнему равна
 * {@code outputHeat / surfaceTemp}, поэтому паспорт всегда самосогласован.
 * <p>
 * {@code spread} (не показывается в паспорте) — прежние значения проводимости 0.30..0.90,
 * отвечают только за форму спада и радиус. Благодаря ему одобренный профиль нагрева не изменился.
 *
 * <h2>Баланс ощущений</h2>
 * Комфорт игрока 15..25 °C, урон строго выше 40 °C. В пещере (фон ~15 °C):
 * <ul>
 *   <li>костёр вплотную — ~20 °C, в комнате ~22 °C (ют);</li>
 *   <li>печь ~24 °C, доменная ~30 °C;</li>
 *   <li>кромка лавового озера ~36 °C, в 3 блоках ~30 °C — ходить можно, заходить нет;</li>
 *   <li>коксовая печь на максимуме ~37 °C, порог 40 °C не переходит;</li>
 *   <li>горячие блоки обжигают только при касании — это отдельный контактный теплообмен
 *       в {@link TemperatureCalculator}, а не этот реестр.</li>
 * </ul>
 *
 * <h2>Параметры пассивных источников</h2>
 * <ul>
 *   <li>{@code phaseTemp} — порог истощения (лава остыла → базальт, лёд → вода);</li>
 *   <li>{@code drainRate} — доля запаса в секунду при полном дисбалансе с комнатой;</li>
 *   <li>{@code restoreRate} — доля запаса в секунду при «перегреве» среды в 2 раза;</li>
 *   <li>{@code depletedBlock} — во что превращается блок при истощении.</li>
 * </ul>
 * Костры, факелы и огонь — пассивные, но {@code drainRate = 0}: их запас «съедает»
 * ванильная механика догорания, наш движок их не трогает.
 */
public final class ModHeatSources {

    private ModHeatSources() {
    }

    /** Заполняет реестр. Вызывается один раз при старте мода на клиенте и на сервере. */
    public static void registerAll() {
        registerVanilla();
        registerModMachines();
    }

    // ═══════════════════════════════════════════════════════════
    // ВАНИЛЬНЫЕ ИСТОЧНИКИ
    // ═══════════════════════════════════════════════════════════

    private static void registerVanilla() {
        // ── Костры (пассивные, но догорают по ванильной механике) ──
        register(Blocks.CAMPFIRE, HeatSourceDef.builder("minecraft:campfire")
                .heater().passive().surfaceTemp(100.0f).outputHeat(7.0f)
                .radius(4.0f).conductivity(0.07f).spread(0.45f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        register(Blocks.SOUL_CAMPFIRE, HeatSourceDef.builder("minecraft:soul_campfire")
                .heater().passive().surfaceTemp(100.0f).outputHeat(6.0f)
                .radius(4.0f).conductivity(0.06f).spread(0.40f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        // ── Факелы ────────────────────────────────────────────
        torch(Blocks.TORCH, "minecraft:torch");
        torch(Blocks.WALL_TORCH, "minecraft:wall_torch");
        torch(Blocks.SOUL_TORCH, "minecraft:soul_torch");
        torch(Blocks.SOUL_WALL_TORCH, "minecraft:soul_wall_torch");

        // ── Печи (активные приборы: активность из состояния LIT) ──
        furnace(Blocks.FURNACE, "minecraft:furnace", 12.0f, 4.0f, 300.0f, 0.50f);
        furnace(Blocks.SMOKER, "minecraft:smoker", 12.0f, 4.0f, 300.0f, 0.50f);
        furnace(Blocks.BLAST_FURNACE, "minecraft:blast_furnace", 19.0f, 4.5f, 500.0f, 0.55f);

        // ── Лава ──────────────────────────────────────────────
        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:lava")
                .heater().passive().surfaceTemp(750.0f).outputHeat(23.0f)
                .radius(6.0f).conductivity(0.042f).spread(0.90f)
                .phaseTemp(300.0f).drainRate(0.0015f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").freezeRadius(4.0f).build(),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:flowing_lava")
                .heater().passive().surfaceTemp(550.0f).outputHeat(18.0f)
                .radius(4.5f).conductivity(0.033f).spread(0.85f)
                .phaseTemp(300.0f).drainRate(0.0020f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").build(),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // ── Огонь ─────────────────────────────────────────────
        register(Blocks.FIRE, HeatSourceDef.builder("minecraft:fire")
                .heater().passive().surfaceTemp(300.0f).outputHeat(16.0f)
                .radius(4.0f).conductivity(0.053f).spread(0.60f)
                .phaseTemp(0.0f).build(),
                state -> true);

        // ── Магма-блок: остывает «на месте», пока не перестанет греть ──
        // Магма 450 °C × 2.7% = 12 °C; остыла до 150 °C → всего ~4 °C.
        register(Blocks.MAGMA_BLOCK, HeatSourceDef.builder("minecraft:magma_block")
                .heater().passive().surfaceTemp(450.0f).outputHeat(12.0f)
                .radius(3.0f).conductivity(0.027f).spread(0.65f)
                .phaseTemp(150.0f).drainRate(0.0015f).restoreRate(0.0010f).build(),
                state -> true);

        // ── Охладители ────────────────────────────────────────
        cooler(Blocks.ICE, "minecraft:ice", 8.0f, 4.0f, 0.50f, 0.0f,
                0.0030f, 0.0015f, 0.02f, 0.30f, 20, 5, 30.0f, 0.02f);
        cooler(Blocks.PACKED_ICE, "minecraft:packed_ice", 11.0f, 4.0f, 0.45f, -18.0f,
                0.0020f, 0.0012f, 0.01f, 0.20f, 24, 7, 35.0f, 0.015f);
        cooler(Blocks.BLUE_ICE, "minecraft:blue_ice", 15.0f, 4.5f, 0.40f, -50.0f,
                0.0015f, 0.0010f, 0.005f, 0.12f, 30, 10, 40.0f, 0.01f);
    }

    // ═══════════════════════════════════════════════════════════
    // ПРИБОРЫ МОДА (активные, со своей температурой)
    // ═══════════════════════════════════════════════════════════

    private static void registerModMachines() {
        // surfaceTemp — макс. температура КОРПУСА (в паспорте), conductivity — доля отдачи,
        // spread — форма спада. Внутренняя шкала deviceScale осталась прежней: она нужна
        // для плавки и взрывов котла, а не для ощущаемого жара.

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(),
                machine("trd:heater", 20.0f, 6.0f, 400.0f, 0.40f, 200.0f, 1600.0f));

        // Медный котёл: внутри 100..600 °C, корпус до 200 °C. Медь отдаёт охотнее
        // нагревателя, и тепло уходит чуть дальше — spread 0.70.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(),
                machine("trd:boiler", 14.0f, 6.0f, 200.0f, 0.70f, 100.0f, 600.0f));

        // Плавильня: внутри 300..1600 °C, корпус до 500 °C. ~33 °C у корпуса.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(),
                machine("trd:smelter", 22.0f, 6.0f, 500.0f, 0.45f, 300.0f, 1600.0f));

        // Коксовая печь: внутри 300..2700 °C, корпус до 700 °C. 700 °C × 3.6% = 25 °C,
        // у корпуса ~37 °C: порог 40 °C не переходит, но стоит рядом уже жарко.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(),
                machine("trd:coking_oven", 25.0f, 7.0f, 700.0f, 0.50f, 300.0f, 2700.0f));

        // Малая плавильня: внутри 200..1100 °C, корпус до 350 °C. Греет на ~4 блока.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(),
                machine("trd:small_smelter", 16.0f, 5.0f, 350.0f, 0.50f, 200.0f, 1100.0f));

        // Нагреватель химического завода: 50 / 100 °C. Паспорт низкий, поэтому
        // доля отдачи 8%, итого всё те же +8 °C в упор.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(),
                machine("trd:chemical_plant_heater", 8.0f, 3.5f, 100.0f, 0.65f, 50.0f, 100.0f));

        // Электрическая печь: работает по состоянию LIT, шкалы температур нет.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(),
                machine("trd:electro_furnace", 14.0f, 4.5f, 300.0f, 0.55f, 1.0f, 1.0f));
    }

    // ═══════════════════════════════════════════════════════════
    // ОБЩИЕ ШАБЛОНЫ РЕГИСТРАЦИИ
    // ═══════════════════════════════════════════════════════════

    private static void register(Block block, HeatSourceDef def, Predicate<BlockState> stateTest) {
        HeatSourceRegistry.register(block, def, stateTest);
    }

    private static void torch(Block block, String id) {
        register(block, HeatSourceDef.builder(id)
                .heater().passive().surfaceTemp(100.0f).outputHeat(2.0f)
                .radius(3.0f).conductivity(0.02f).spread(0.30f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> true);
    }

    /** Conductivity выводится из outputHeat / surfaceTemp, чтобы паспорт не противоречил дефу. */
    private static void furnace(Block block, String id, float output, float radius,
                                float temp, float spread) {
        register(block, HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(output)
                .radius(radius).conductivity(output / temp).spread(spread).build(),
                state -> state.getValue(AbstractFurnaceBlock.LIT));
    }

    private static void cooler(Block block, String id, float output, float radius,
                               float conductivity, float temp,
                               float drainRate, float restoreRate,
                               float meltChanceCold, float meltChanceHot,
                               int meltCooldownCold, int meltCooldownHot,
                               float meltTempSpan, float meltLoss) {
        register(block, HeatSourceDef.builder(id)
                .cooler().passive().surfaceTemp(temp).coolingPower(output)
                .radius(radius).conductivity(conductivity).spread(conductivity)
                .phaseTemp(0.0f).drainRate(drainRate).restoreRate(restoreRate)
                .melt(meltChanceCold, meltChanceHot, meltCooldownCold, meltCooldownHot, meltTempSpan, meltLoss)
                .depletedBlock("minecraft:water").build(), state -> true);
    }

    /** Conductivity выводится из outputHeat / surfaceTemp — паспорт всегда честен. */
    private static HeatSourceDef machine(String id, float output, float radius, float temp,
                                         float spread, float scaleMin, float scaleMax) {
        return HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(output)
                .radius(radius).conductivity(output / temp).spread(spread)
                .deviceScale(scaleMin, scaleMax)
                .build();
    }

    /** Проверка, что блок является источником тепла (для диагностики). */
    public static boolean isVanillaSource(BlockState state) {
        return HeatSourceRegistry.resolveState(state) != null;
    }
}
