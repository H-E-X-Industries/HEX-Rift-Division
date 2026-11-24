package com.trd.climate.calculation;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * <b>Стержень тепловой системы.</b> Все числа источников живут только здесь.
 *
 * <p>При регистрации нового прибора указываются основные значения:
 * {@code id}, {@code outputHeat} (К — потолок жара, главный баланс), паспортная
 * {@code surfaceTemp} (максимум шкалы) и {@code spread} (форма спада).
 * Радиус работы и теплопроводность вычисляются кодом (см. {@link #machine}).
 */
public final class ModHeatSources {

    private ModHeatSources() {
    }

    /** Заполняет реестр (клиент и сервер заполняют одинаково). */
    public static void registerAll() {
        registerVanilla();
        registerModMachines();
    }

    // ═══════════════════════════════════════════════════════════
    // ВАНИЛЬНЫЕ ИСТОЧНИКИ
    // ═══════════════════════════════════════════════════════════

    private static void registerVanilla() {
        // Костры (пассивные, догорают по ванильной механике).
        register(Blocks.CAMPFIRE, HeatSourceDef.builder("minecraft:campfire")
                .heater().passive().surfaceTemp(100.0f).drainRate(0.0015f).outputHeat(7.0f)
                .radius(4.0f).conductivity(0.07f).spread(0.45f).negligibleInLargeRooms(true)
                .phaseTemp(50.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        register(Blocks.SOUL_CAMPFIRE, HeatSourceDef.builder("minecraft:soul_campfire")
                .heater().passive().surfaceTemp(100.0f).outputHeat(6.0f)
                .radius(4.0f).drainRate(0.0015f).conductivity(0.06f).spread(0.40f).negligibleInLargeRooms(true)
                .phaseTemp(50.0f).build(), state -> state.getValue(CampfireBlock.LIT));

        // Факелы — согревают при касании сильнее (считается отдельно в TemperatureCalculator).
        torch(Blocks.TORCH, "minecraft:torch");
        torch(Blocks.WALL_TORCH, "minecraft:wall_torch");
        torch(Blocks.SOUL_TORCH, "minecraft:soul_torch");
        torch(Blocks.SOUL_WALL_TORCH, "minecraft:soul_wall_torch");

        // Печи (активные приборы, активность из состояния LIT).
        furnace(Blocks.FURNACE, "minecraft:furnace", 24.0f, 900.0f, 0.50f);
        furnace(Blocks.SMOKER, "minecraft:smoker", 24.0f, 900.0f, 0.50f);
        furnace(Blocks.BLAST_FURNACE, "minecraft:blast_furnace", 28.0f, 1400.0f, 0.55f);

        // Лава: карбонатная лава ~550-700 °C. При истощении поток вокруг замораживается
        // синхронно (ThermalStateStore#freezeSurroundingFluid).
        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:lava")
                .heater().passive().surfaceTemp(700.0f).outputHeat(20.0f)
                .radius(6.0f).conductivity(0.04f).spread(0.90f)
                .phaseTemp(300.0f).drainRate(0.0015f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").freezeRadius(4.0f).build(),
                state -> state.getValue(LiquidBlock.LEVEL) == 0);

        register(Blocks.LAVA, HeatSourceDef.builder("minecraft:flowing_lava")
                .heater().passive().surfaceTemp(550.0f).outputHeat(18.0f)
                .radius(4.5f).conductivity(0.0327f).spread(0.85f)
                .phaseTemp(300.0f).drainRate(0.0020f).restoreRate(0.0015f)
                .depletedBlock("minecraft:basalt").freezeRadius(3.0f).build(),
                state -> state.getValue(LiquidBlock.LEVEL) > 0);

        // Огонь.
        register(Blocks.FIRE, HeatSourceDef.builder("minecraft:fire")
                .heater().passive().surfaceTemp(300.0f).outputHeat(16.0f)
                .radius(4.0f).conductivity(0.053f).spread(0.60f)
                .phaseTemp(0.0f).build(), state -> true);

        // Магма-блок: остывает «на месте», пока не перестанет греть.
        register(Blocks.MAGMA_BLOCK, HeatSourceDef.builder("minecraft:magma_block")
                .heater().passive().surfaceTemp(450.0f).outputHeat(12.0f)
                .radius(3.0f).conductivity(0.027f).spread(0.65f)
                .phaseTemp(150.0f).drainRate(0.0015f).restoreRate(0.0010f).build(),
                state -> true);

        // Охладители (ледовый ряд): coolingPower — максимум °C, который источник забирает.
        cooler(Blocks.ICE, "minecraft:ice", 8.0f, 4.0f, 0.50f, 0.0f,
                0.02f, 0.005f, 0.04f, 0.25f, 15, 4, 25.0f, 0.015f);
        cooler(Blocks.PACKED_ICE, "minecraft:packed_ice", 11.0f, 4.0f, 0.45f, -18.0f,
                0.015f, 0.005f, 0.03f, 0.20f, 18, 5, 25.0f, 0.012f);
        cooler(Blocks.BLUE_ICE, "minecraft:blue_ice", 15.0f, 4.5f, 0.40f, -50.0f,
                0.01f, 0.004f, 0.02f, 0.15f, 20, 6, 25.0f, 0.010f);

        // Вода: нейтральный пассивный источник (озёра). На сильном перепаде
        // с комнатой ниже 0°C запускается процесс заморозки (ThermalStateStore#tickWaterPhysics),
        // при нагреве выше 100°C — кипение.
        register(Blocks.WATER, HeatSourceDef.builder("minecraft:water")
                .heater().passive().surfaceTemp(20.0f).outputHeat(0.2f)
                .radius(2.0f).conductivity(0.01f).spread(0.40f)
                .phaseTemp(0.0f).drainRate(0.0005f)
                .depletedBlock("minecraft:ice").build(), state -> true);
    }

    // ═══════════════════════════════════════════════════════════
    // ПРИБОРЫ МОДА (активные, со своей температурой)
    // ═══════════════════════════════════════════════════════════

    private static void registerModMachines() {
        // machine() — весь остальной расчёт (теплопроводность, радиус) делает код.

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.HEATER.get(),
                machine("trd:heater", 20.0f, 1600.0f, 0.40f, 200.0f, 1600.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.BOILER.get(),
                machine("trd:boiler", 14.0f, 600.0f, 0.70f, 100.0f, 600.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMELTER.get(),
                machine("trd:smelter", 22.0f, 1600.0f, 0.45f, 300.0f, 1600.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.COCCER_OVEN.get(),
                machine("trd:coking_oven", 25.0f, 2700.0f, 0.50f, 300.0f, 2700.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.SMALL_SMELTER.get(),
                machine("trd:small_smelter", 16.0f, 1100.0f, 0.50f, 200.0f, 1100.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.CHEMICAL_PLANT_HEATER.get(),
                machine("trd:chemical_plant_heater", 8.0f, 100.0f, 0.65f, 50.0f, 100.0f));

        HeatSourceRegistry.registerIdOnly(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get(),
                machine("trd:electro_furnace", 14.0f, 1200.0f, 0.55f, 1.0f, 1.0f));
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

    private static void furnace(Block block, String id, float output, float temp, float spread) {
        register(block, HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(output)
                .radius(radiusFromHeat(output)).conductivity(output / temp).spread(spread).build(),
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

    /**
     * Регистрация прибора мода: {@code id}, {@code heat} (потолок жара, °C),
     * паспортная {@code temp}, {@code spread} и диапазон шкалы {@code scaleMin..scaleMax}.
     * Радиус и теплопроводность — честно вычислены кодом.
     */
    private static HeatSourceDef machine(String id, float heat, float temp, float spread,
                                         float scaleMin, float scaleMax) {
        return HeatSourceDef.builder(id)
                .heater().active().surfaceTemp(temp).outputHeat(heat)
                .radius(radiusFromHeat(heat)).conductivity(heat / temp).spread(spread)
                .deviceScale(scaleMin, scaleMax)
                .build();
    }

    /** Радиус излучения прибора из его выхода тепла: выше К — шире, ниже — уже. */
    private static float radiusFromHeat(float heat) {
        return Mth.clamp(heat * 0.3f + 2.0f, 3.5f, 9.0f);
    }

    /** Проверка, что блок является источником тепла (для диагностики). */
    public static boolean isVanillaSource(BlockState state) {
        return HeatSourceRegistry.resolveState(state) != null;
    }
}
