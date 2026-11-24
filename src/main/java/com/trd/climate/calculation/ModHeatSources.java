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
 * <h2>Баланс: какие числа сюда писать</h2>
 * {@code outputHeat} — это не «физическая температура источника», а <b>сколько градусов
 * источник добавляет к температуре воздуха у своей поверхности</b>. Эта величина
 * складывается с температурой тела игрока, шкала которого («Комфорт 15..25, урон >40»)
 * описана в {@link com.trd.climate.effect.TemperatureEffectApplier}. Поэтому потолок
 * здесь — единицы-десятки градусов, а не сотни. Физическая температура при этом остаётся
 * в {@code surfaceTemp} (лава 1200 °C, коксовая печь 2700 °C) и используется для потолка
 * излучения, контакта и шкалы мощности прибора — её менять не надо.
 * <p>
 * Целевые ощущения в пещере (фон ~15 °C, укрытие ~1.0):
 * <ul>
 *   <li>костёр вплотную — ~20 °C, в герметичной комнате доведёт до ~22 °C (уют);</li>
 *   <li>рабочая печь вплотную — ~24 °C, доменная — ~30 °C (жара, но без урона);</li>
 *   <li>кромка лавового озера — ~36 °C, в 3 блоках — ~30 °C
 *       (жарко, хочется уйти, но идти можно — именно это и нужно);</li>
 *   <li>максимально разогретая коксовая печь — ~40 °C (порог перегрева,
 *       урон начинается строго выше 40 °C);</li>
 *   <li>горячие блоки обжигают только при прямом касании — за это отвечает отдельный
 *       контактный теплообмен в {@link TemperatureCalculator}, а не этот реестр.</li>
 * </ul>
 * {@code radius} теперь почти равен реальному радиусу в блоках: после правки
 * {@link HeatSourceDef#HEAT_RADIUS_MIN_SCALE} множитель тепловыхода равен 1.0 для всех
 * сбалансированных источников (outputHeat &lt; 100). Остаётся только множитель
 * теплопроводности ({@link HeatSourceDef#spreadByConductivity()}, 0.74..0.98).
 *
 * <h2>Параметры пассивных источников</h2>
 * <ul>
 *   <li>{@code phaseTemp} — порог истощения (лава 1200 → 700 °C, лёд → 0 °C);</li>
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
        // 700 °C «внутри», но соседний воздух получает всего +7 °C — уют, а не печь.
        register(Blocks.CAMPFIRE, HeatSourceDef.builder("minecraft:campfire")
                .heater().passive().surfaceTemp(700.0f).outputHeat(7.0f)
                .radius(4.0f).conductivity(0.45f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        register(Blocks.SOUL_CAMPFIRE, HeatSourceDef.builder("minecraft:soul_campfire")
                .heater().passive().surfaceTemp(700.0f).outputHeat(6.0f)
                .radius(4.0f).conductivity(0.40f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        // ── Факелы ────────────────────────────────────────────
        // Почти не греют воздух (+2 °C в упор); согрев в руке считается отдельно
        // в TemperatureCalculator#getTorchHeat. В герметичной комнате дают +17 °C.
        torch(Blocks.TORCH, "minecraft:torch");
        torch(Blocks.WALL_TORCH, "minecraft:wall_torch");
        torch(Blocks.SOUL_TORCH, "minecraft:soul_torch");
        torch(Blocks.SOUL_WALL_TORCH, "minecraft:soul_wall_torch");

        // ── Печи (активные приборы: активность из состояния LIT) ──
        // Соседний воздух ~24 °C, доменная ~30 °C; в комнате цели ~27 и ~34 °C.
        // Тепло, но без ожога — урон начинается только выше 40 °C.
        furnace(Blocks.FURNACE, "minecraft:furnace", 12.0f, 4.0f, 0.50f, 900.0f);
        furnace(Blocks.SMOKER, "minecraft:smoker", 12.0f, 4.0f, 0.50f, 900.0f);
        furnace(Blocks.BLAST_FURNACE, "minecraft:blast_furnace", 19.0f, 4.5f, 0.55f, 1400.0f);

        // ── Лава ──────────────────────────────────────────────
        // Кромка озера ~36-38 °C, но радиус 6 и мягкий спад (p=1.2) означают,
        // что в 3 блоках уже ~30 °C: ходить вокруг озера можно, заходить — нет.
        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:lava")
                .heater().passive().surfaceTemp(1200.0f).outputHeat(23.0f)
                .radius(6.0f).conductivity(0.90f)
                .phaseTemp(700.0f).drainRate(0.0030f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").freezeRadius(4.0f).build(),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:flowing_lava")
                .heater().passive().surfaceTemp(1200.0f).outputHeat(18.0f)
                .radius(4.5f).conductivity(0.85f)
                .phaseTemp(700.0f).drainRate(0.0040f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").build(),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // ── Огонь ─────────────────────────────────────────────
        register(Blocks.FIRE, HeatSourceDef.builder("minecraft:fire")
                .heater().passive().surfaceTemp(900.0f).outputHeat(16.0f)
                .radius(4.0f).conductivity(0.60f)
                .phaseTemp(0.0f).build(),
                state -> true);

        // ── Магма-блок: остывает «на месте», пока не перестанет греть ──
        register(Blocks.MAGMA_BLOCK, HeatSourceDef.builder("minecraft:magma_block")
                .heater().passive().surfaceTemp(700.0f).outputHeat(12.0f)
                .radius(3.0f).conductivity(0.65f)
                .phaseTemp(200.0f).drainRate(0.0025f).restoreRate(0.0010f).build(),
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
        // surfaceTemp остаётся физической (шкала плавки), а outputHeat — ощущаемый жар.
        // deviceScale задаёт мощность: холодный прибор греет только вплотную.

        // Нагреватель из огнеупорного кирпича с медным ядром: 200..1600 °C.
        // Кирпич плохо проводит тепло — жар держится рядом (~35 °C у корпуса).
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(),
                machine("trd:heater", 20.0f, 6.0f, 0.40f, 1600.0f, 200.0f, 1600.0f));

        // Медный котёл: 100..600 °C. Медь проводит лучше — радиус чуть больше.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(),
                machine("trd:boiler", 14.0f, 6.0f, 0.70f, 600.0f, 100.0f, 600.0f));

        // Плавильня: 300..1600 °C — горячее нагревателя, но тоже в пределах ~37 °C.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(),
                machine("trd:smelter", 22.0f, 6.0f, 0.45f, 1600.0f, 300.0f, 1600.0f));

        // Коксовая печь: 300..2700 °C — самая горячая. У корпуса ровно ~40 °C:
        // это порог, за которым начинается урон, но стоять рядом ещё не убивает.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(),
                machine("trd:coking_oven", 25.0f, 7.0f, 0.50f, 2700.0f, 300.0f, 2700.0f));

        // Малая плавильня: 200..1100 °C — компактная, греет на ~4 блока.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(),
                machine("trd:small_smelter", 16.0f, 5.0f, 0.50f, 1100.0f, 200.0f, 1100.0f));

        // Нагреватель химического завода: 50 / 100 °C — слабый, ~23 °C в упор.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(),
                machine("trd:chemical_plant_heater", 8.0f, 3.5f, 0.65f, 100.0f, 50.0f, 100.0f));

        // Электрическая печь: работает по состоянию LIT, шкалы температур нет.
        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(),
                machine("trd:electro_furnace", 14.0f, 4.5f, 0.55f, 1200.0f, 1.0f, 1.0f));
    }

    // ═══════════════════════════════════════════════════════════
    // ОБЩИЕ ШАБЛОНЫ РЕГИСТРАЦИИ
    // ═══════════════════════════════════════════════════════════

    private static void register(Block block, HeatSourceDef def, Predicate<BlockState> stateTest) {
        HeatSourceRegistry.register(block, def, stateTest);
    }

    private static void torch(Block block, String id) {
        register(block, HeatSourceDef.builder(id)
                .heater().passive().surfaceTemp(800.0f).outputHeat(2.0f)
                .radius(3.0f).conductivity(0.30f).negligibleInLargeRooms(true)
                .phaseTemp(0.0f).build(), state -> true);
    }

    private static void furnace(Block block, String id, float output, float radius,
                                float conductivity, float temp) {
        register(block, HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(output)
                .radius(radius).conductivity(conductivity).build(),
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
                .radius(radius).conductivity(conductivity)
                .phaseTemp(0.0f).drainRate(drainRate).restoreRate(restoreRate)
                .melt(meltChanceCold, meltChanceHot, meltCooldownCold, meltCooldownHot, meltTempSpan, meltLoss)
                .depletedBlock("minecraft:water").build(), state -> true);
    }

    private static HeatSourceDef machine(String id, float output, float radius, float conductivity,
                                         float temp, float scaleMin, float scaleMax) {
        return HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(output)
                .radius(radius).conductivity(conductivity)
                .deviceScale(scaleMin, scaleMax)
                .build();
    }

    /** Проверка, что блок является источником тепла (для диагностики). */
    public static boolean isVanillaSource(BlockState state) {
        return HeatSourceRegistry.resolveState(state) != null;
    }
}
