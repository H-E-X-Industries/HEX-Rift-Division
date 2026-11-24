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
 *
 * <h2>МИНИ-СПРАВОЧНИК: как завести новый источник</h2>
 * <pre>
 * HeatSourceDef.builder("trd:my_heater")   // id уникален, совпадает с IHeatSource#getHeatSourceId
 *     .heater()                            // HEATER греет / COOLER охлаждает
 *     .active()                            // ACTIVE — прибор (печь), PASSIVE — блок (лава, лёд)
 *     .surfaceTemp(1600f)                  // паспортная температура, °C
 *     .outputHeat(20f)                     // K: жар у поверхности, °C (главный балансир)
 *     .radius(6f)                          // радиус в блоках при полной мощности
 *     .conductivity(20f / 1600f)           // пассивным: фиксированная доля от T
 *     .spread(0.40f)                       // форма спада: 1 — тепло уходит далеко, 0 — только вплотную
 *     .deviceScale(200f, 1600f)            // прибору: порог и максимум шкалы мощности
 *     .build();
 * </pre>
 * Что легко забыть:
 * <ul>
 *   <li>{@code outputHeat} (он же K) — единственный балансир жара: именно он добавляется
 *       к температуре игрока. Меняешь его — меняешь ощущения, остальное только форма.</li>
 *   <li>У активных приборов доля отдачи — <b>функция K/T</b> и падает с нагревом, поэтому
 *       печь на 2700 °C не обжигает сильнее печи на 300 °C. {@code conductivity} в дефе
 *       лишь заготовка для тултипа; в расчёте используется {@code transferFactor(T)}.</li>
 *   <li>У пассивных блоков доля фиксированная ({@code conductivity}), а жар = T × доля.
 *       Поэтому у магмы 450 °C × 2.7% = 12 °C — это честно и видно в паспорте.</li>
 *   <li>{@code spread} — <b>не</b> теплопроводность, а форма спада и радиус. Ставь 0.85..0.90
 *       для озёр и ледников (тепло уходит далеко), 0.30..0.50 для печей (держится рядом).</li>
 *   <li>{@code deviceScaleMin} — температура, ниже которой прибор не греет; совпадает
 *       с реальным минимумом машины. {@code deviceScaleMax} должен совпадать с её максимумом.</li>
 *   <li>{@code phaseTemp}/{@code drainRate}/{@code depletedBlock} — только для пассивных:
 *       до чего остывает блок, как быстро тратит запас и во что превращается.</li>
 * </ul>
 * Готовые примеры всех трёх разновидностей — ниже в {@link #registerVanilla()} и
 * {@link #registerModMachines()}.
 *
 * <h2>Паспорт: правдоподобие вместо «страшных» долей</h2>
 * У пассивных блоков температура берётся из нижней границы реального диапазона:
 * магма 450 °C (реально 450..1650), лава 550 °C (карбонатная, 510..600), костёр 100 °C.
 * У активных приборов в паспорте стоит <b>фактическая</b> температура их внутреннего
 * объёма — 1600 °C у нагревателя, 2700 °C у коксовой печи. Она нужна для плавки стали
 * (1440 °C) и не имеет отношения к жару вокруг: за отдачу отвечает K/T.
 * <p>
 * {@code spread} и {@code deviceScale} оставляем как есть — они задают форму спада и
 * мощность; всё, что нужно помнить о них, вынесено в справочник выше.
 *
 * <h2>Баланс ощущений</h2>
 * Комфорт игрока 15..25 °C, урон строго выше 40 °C. В пещере (фон ~15 °C):
 * <ul>
 *   <li>костёр вплотную — ~20 °C, в комнате ~22 °C (ют);</li>
 *   <li>печь ~24 °C, доменная ~30 °C;</li>
 *   <li>кромка лавового озера ~36 °C, в 3 блоках ~30 °C — ходить можно, заходить нет;</li>
 *   <li>коксовая печь на максимуме ~37 °C, порог 40 °C не переходит;</li>
 *   <li>горячие блоки обжигают только при касании — отдельный контактный теплообмен
 *       в {@link TemperatureCalculator}, а не этот реестр.</li>
 * </ul>
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
        // 100 °C × 7% = 7 °C в воздухе. Уют, а не печь.
        register(Blocks.CAMPFIRE, HeatSourceDef.builder("minecraft:campfire")
                .heater().passive().surfaceTemp(100.0f).outputHeat(7.0f)
                .radius(4.0f).conductivity(0.07f).spread(0.45f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        // 100 °C × 6% = 6 °C — душный костёр чуть прохладнее обычного.
        register(Blocks.SOUL_CAMPFIRE, HeatSourceDef.builder("minecraft:soul_campfire")
                .heater().passive().surfaceTemp(100.0f).outputHeat(6.0f)
                .radius(4.0f).conductivity(0.06f).spread(0.40f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        // ── Факелы ────────────────────────────────────────────
        // 100 °C × 2% = 2 °C в упор; согрев в руке считается отдельно
        // в TemperatureCalculator#getTorchHeat. В герметичной комнате дают +17 °C.
        torch(Blocks.TORCH, "minecraft:torch");
        torch(Blocks.WALL_TORCH, "minecraft:wall_torch");
        torch(Blocks.SOUL_TORCH, "minecraft:soul_torch");
        torch(Blocks.SOUL_WALL_TORCH, "minecraft:soul_wall_torch");

        // ── Печи (активные приборы: активность из состояния LIT) ──
        // Фактическая температура 900 °C и 1400 °C, а жар вокруг ~24 и ~30 °C —
        // даёт всё та же функция K/T. В комнате цели ~27 и ~34 °C, урон выше 40 °C.
        furnace(Blocks.FURNACE, "minecraft:furnace", 12.0f, 4.0f, 900.0f, 0.50f);
        furnace(Blocks.SMOKER, "minecraft:smoker", 12.0f, 4.0f, 900.0f, 0.50f);
        furnace(Blocks.BLAST_FURNACE, "minecraft:blast_furnace", 19.0f, 4.5f, 1400.0f, 0.55f);

        // ── Лава ──────────────────────────────────────────────
        // Карбонатная лава 550 °C × 4.2% = 23 °C. Радиус 6 и мягкий спад (p=1.2):
        // в 3 блоках ~30 °C — ходить вокруг озера можно, заходить нельзя.
        // workSpan стал вдвое меньше прежнего (550−300 против 1200−700),
        // поэтому drainRate тоже вдвое меньше — скорость истощения не изменилась.
        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:lava")
                .heater().passive().surfaceTemp(750.0f).outputHeat(23.0f)
                .radius(6.0f).conductivity(0.042f).spread(0.90f)
                .phaseTemp(300.0f).drainRate(0.0015f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").freezeRadius(4.0f).build(),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        // 550 °C × 3.3% = 18 °C — текучая лава чуть слабее стоячей.
        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:flowing_lava")
                .heater().passive().surfaceTemp(550.0f).outputHeat(18.0f)
                .radius(4.5f).conductivity(0.033f).spread(0.85f)
                .phaseTemp(300.0f).drainRate(0.0020f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").build(),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // ── Огонь ─────────────────────────────────────────────
        // 300 °C × 5.3% = 16 °C.
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
        // coolingPower — сколько градусов источник способен забрать у воздуха.
        // Лёд ~7 °C, плотный ~4 °C, синий ~0 °C рядом; синий лёд остаётся
        // опасным (долго стоять возле него можно замерзнуть) — это честный hazard.
        // Параметры таяния (шанс при 0°C → шанс при +30°C, кд 20 тиков → 5 тиков):
        // обычный лёд тает быстрее всего, синий — медленнее всего.
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
        // surfaceTemp — ФАКТИЧЕСКАЯ температура машины (совпадает с её шкалой и GUI).
        // Доля отдачи считается сама как K/T, поэтому 2700 °C здесь не страшнее 300 °C.

        // Нагреватель из огнеупорного кирпича с медным ядром: внутри 200..1600 °C.
        // K=20 °C, спред 0.40 — кирпич держит жар рядом, у корпуса ~31 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(),
                machine("trd:heater", 20.0f, 6.0f, 1600.0f, 0.40f, 200.0f, 1600.0f));

        // Медный котёл: внутри 100..600 °C. Медь отдаёт охотнее нагревателя,
        // и тепло уходит чуть дальше — spread 0.70.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(),
                machine("trd:boiler", 14.0f, 6.0f, 600.0f, 0.70f, 100.0f, 600.0f));

        // Плавильня: внутри 300..1600 °C. ~33 °C у корпуса.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(),
                machine("trd:smelter", 22.0f, 6.0f, 1600.0f, 0.45f, 300.0f, 1600.0f));

        // Коксовая печь: 300..2700 °C — самая горячая. K=25 °C, у корпуса ~37 °C:
        // порог 40 °C не переходит, но стоять рядом уже жарко.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(),
                machine("trd:coking_oven", 25.0f, 7.0f, 2700.0f, 0.50f, 300.0f, 2700.0f));

        // Малая плавильня: 200..1100 °C — компактная, греет на ~4 блока.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(),
                machine("trd:small_smelter", 16.0f, 5.0f, 1100.0f, 0.50f, 200.0f, 1100.0f));

        // Нагреватель химического завода: 50 / 100 °C — слабый, +8 °C в упор.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(),
                machine("trd:chemical_plant_heater", 8.0f, 3.5f, 100.0f, 0.65f, 50.0f, 100.0f));

        // Электрическая печь: работает по состоянию LIT, шкалы температур нет.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(),
                machine("trd:electro_furnace", 14.0f, 4.5f, 1200.0f, 0.55f, 1.0f, 1.0f));
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

    /** Conductivity выводится из K / T, чтобы паспорт не противоречил дефу. */
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

    /** Conductivity выводится из K / T — паспорт всегда честен. */
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
