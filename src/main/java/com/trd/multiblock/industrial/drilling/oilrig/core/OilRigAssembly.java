package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilVeinManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Координатор одной модульной нефтевышки.
 * Объединяет осевые (MudCross, будущий BOP, RotaryTable) и боковые (PipeDrum, будущий SteamInjector) модули,
 * управляет единой State Machine, 340-тиковой анимацией, добычей и давлением.
 */
public final class OilRigAssembly {

    public static final int TOWER_CYCLE_TICKS = 340;
    public static final int TICK_GRAB_PIPE = 100;
    public static final int TICK_INDEX_DRUM = 140;
    public static final int TICK_FEED_ROTOR = 260;
    public static final int EXTRACTION_INTERVAL_TICKS = 60;
    public static final float EXTRACTION_DEPLETION_CHANCE = 0.25f;
    public static final int EXTRACTION_BASE_AMOUNT_MB = 1000;
    public static final float PRESSURE_VENT_THRESHOLD = 200.0f;
    public static final float PRESSURE_DETONATE_THRESHOLD = 250.0f;

    private final WellboreColumn column;
    @Nullable
    private final IWellheadModule wellhead;
    @Nullable
    private final IDrillDriveModule drive;
    @Nullable
    private final IPipeFeederModule feeder;
    private final List<IBlowoutPreventerModule> bops;
    private final List<IWellEnhancerModule> enhancers;

    private RigAssemblyState state = RigAssemblyState.IDLE;
    private long lastTickedGameTime = Long.MIN_VALUE;
    private int extractionTimer = 0;

    private final RigPressureContext pressureContext = new RigPressureContext();
    private final RigExtractionContext extractionContext = new RigExtractionContext();

    public OilRigAssembly(WellboreColumn column,
                          @Nullable IWellheadModule wellhead,
                          @Nullable IDrillDriveModule drive,
                          @Nullable IPipeFeederModule feeder,
                          List<IBlowoutPreventerModule> bops,
                          List<IWellEnhancerModule> enhancers) {
        this.column = column;
        this.wellhead = wellhead;
        this.drive = drive;
        this.feeder = feeder;
        this.bops = Collections.unmodifiableList(bops);
        this.enhancers = Collections.unmodifiableList(enhancers);

        if (this.wellhead != null && this.wellhead.getActivePocketPos() != null && this.drive != null) {
            this.state = RigAssemblyState.EXTRACTING;
        }
    }

    public WellboreColumn getColumn() {
        return column;
    }

    @Nullable
    public IWellheadModule getWellhead() {
        return wellhead;
    }

    @Nullable
    public IDrillDriveModule getDrive() {
        return drive;
    }

    @Nullable
    public IPipeFeederModule getFeeder() {
        return feeder;
    }

    public List<IBlowoutPreventerModule> getBops() {
        return bops;
    }

    public List<IWellEnhancerModule> getEnhancers() {
        return enhancers;
    }

    public RigAssemblyState getState() {
        return state;
    }

    private void setState(RigAssemblyState newState) {
        if (this.state == newState) return;
        this.state = newState;
        if (wellhead != null) wellhead.onAssemblyStateChanged(newState);
        if (drive != null) drive.onAssemblyStateChanged(newState);
        if (feeder != null) feeder.onAssemblyStateChanged(newState);
        for (IBlowoutPreventerModule bop : bops) bop.onAssemblyStateChanged(newState);
        for (IWellEnhancerModule enhancer : enhancers) enhancer.onAssemblyStateChanged(newState);
    }

    /**
     * Единый тик всей вышки, защищённый от повторного вызова разными BE в одном игровом тике.
     */
    public void serverTick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime == lastTickedGameTime) {
            return;
        }
        lastTickedGameTime = gameTime;

        if (!validateModules()) {
            OilRigManager.get(level).rebuildColumn(column);
            return;
        }

        // Давление обрабатывается каждый тик независимо от активности бурения,
        // пока устье существует (в т.ч. при переполнении и стравливании).
        if (wellhead != null) {
            tickPressure(level, 0.0f, true);
            if (!wellhead.isModuleValid()) {
                return;
            }
        }

        if (wellhead == null || drive == null || !drive.isDerrickAssembled() || !drive.isPlatformValid(level)) {
            setState(RigAssemblyState.IDLE);
            if (drive != null) {
                drive.setTowerAnimPaused(true);
            }
            return;
        }

        if (!drive.isRedstoneEnabled()) {
            if (state != RigAssemblyState.EXTRACTING) {
                setState(RigAssemblyState.IDLE);
            }
            drive.setTowerAnimPaused(true);
            return;
        }

        // Режим добычи после достижения кармана
        if (state == RigAssemblyState.EXTRACTING || wellhead.getActivePocketPos() != null) {
            setState(RigAssemblyState.EXTRACTING);
            drive.setTowerAnimPaused(true);
            drive.stopRotaryForExtraction(level);
            tickExtraction(level);
            return;
        }

        // Для подачи трубы и бурения требуются оптимальные обороты (60 RPM ± 25% = 45..75 RPM)
        if (!drive.isOptimalRpm()) {
            setState(RigAssemblyState.PAUSED_NO_POWER);
            drive.setTowerAnimPaused(true);
            return;
        }

        // Пайплайн бурения (если труба в процессе 4-блочного спуска)
        if (drive.isDescendingPipe()) {
            boolean finishedDescent = drive.tickPipeDescent(level);
            if (finishedDescent) {
                BlockPos detected = drive.getDetectedPocketPos();
                if (detected != null) {
                    OilPocketHelper.ResolvedPocket resolved = OilPocketHelper.resolve(level, detected);
                    if (resolved != null) {
                        wellhead.bindReservoir(resolved.composition(), resolved.pos(), resolved.veinId());
                        drive.clearDetectedPocketPos();
                        drive.stopRotaryForExtraction(level);
                        extractionTimer = 0;
                        setState(RigAssemblyState.EXTRACTING);
                        return;
                    }
                    drive.clearDetectedPocketPos();
                }
            }
        }

        // Пайплайн 17-секундной (340-тиковой) анимации вышки и барабана
        int tick = drive.getTowerAnimTick();

        if (tick == TICK_GRAB_PIPE) {
            if (feeder != null && !feeder.isIndexingOrPending() && feeder.hasReadyPipe()) {
                if (feeder.consumeReadyPipe()) {
                    drive.setHoldingPipe(true);
                    drive.setTowerAnimPaused(false);
                    drive.setTowerAnimTick(tick + 1);
                    setState(RigAssemblyState.FEEDING_PIPE);
                    return;
                }
            }
            drive.setHoldingPipe(false);
            drive.setTowerAnimPaused(true);
            setState(RigAssemblyState.PAUSED_NO_PIPES);
            return;
        }

        if (tick == TICK_INDEX_DRUM) {
            if (feeder != null) {
                feeder.requestIndexNextSlot();
            }
            drive.setTowerAnimPaused(false);
            drive.setTowerAnimTick(tick + 1);
            setState(RigAssemblyState.FEEDING_PIPE);
            return;
        }

        if (tick == TICK_FEED_ROTOR) {
            drive.setHoldingPipe(false);
            drive.beginPipeDescent(level);
            drive.setTowerAnimPaused(false);
            drive.setTowerAnimTick(tick + 1);
            setState(RigAssemblyState.DRILLING);
            return;
        }

        if (tick >= TOWER_CYCLE_TICKS) {
            drive.setTowerAnimTick(0);
            if (drive.isDescendingPipe()) {
                drive.setTowerAnimPaused(true);
                setState(RigAssemblyState.DRILLING);
            } else {
                drive.setTowerAnimPaused(false);
                setState(RigAssemblyState.FEEDING_PIPE);
            }
            return;
        }

        if (tick == 0 && drive.isDescendingPipe()) {
            // Ждём полного спуска текущей трубы (например, если порода затормозила спуск дольше 340 тиков)
            drive.setTowerAnimPaused(true);
            setState(RigAssemblyState.DRILLING);
            return;
        }

        drive.setTowerAnimPaused(false);
        drive.setTowerAnimTick(tick + 1);
        setState((tick >= TICK_FEED_ROTOR || drive.isDescendingPipe())
                ? RigAssemblyState.DRILLING
                : RigAssemblyState.FEEDING_PIPE);
    }

    private void tickExtraction(ServerLevel level) {
        if (wellhead == null || drive == null) return;

        BlockPos pocketPos = wellhead.getActivePocketPos();
        if (pocketPos == null || !OilPocketHelper.isReservoirBlock(level.getBlockState(pocketPos))) {
            // Ищем соседний живой блок кармана вокруг нижней трубы
            BlockPos adjacent = drive.findAdjacentPocketBlock(level);
            if (adjacent != null) {
                OilPocketHelper.ResolvedPocket resolved = OilPocketHelper.resolve(level, adjacent);
                if (resolved != null) {
                    wellhead.bindReservoir(resolved.composition(), resolved.pos(), resolved.veinId());
                    pocketPos = resolved.pos();
                } else {
                    pocketPos = null;
                }
            } else {
                pocketPos = null;
            }
        }

        if (pocketPos == null) {
            wellhead.bindReservoir(null, null, null);
            extractionTimer = 0;
            drive.resumeDrillingFromExtraction(level);
            setState(RigAssemblyState.IDLE);
            return;
        }

        extractionTimer++;
        if (extractionTimer < EXTRACTION_INTERVAL_TICKS) {
            return;
        }
        extractionTimer = 0;

        OilComposition composition = wellhead.getReservoirComposition();
        if (composition == null) {
            OilPocketHelper.ResolvedPocket resolved = OilPocketHelper.resolve(level, pocketPos);
            if (resolved != null) {
                composition = resolved.composition();
                wellhead.bindReservoir(composition, resolved.pos(), resolved.veinId());
            }
        }

        extractionContext.reset(EXTRACTION_BASE_AMOUNT_MB, EXTRACTION_DEPLETION_CHANCE, composition, pocketPos);
        for (IWellEnhancerModule enhancer : enhancers) {
            if (enhancer.isModuleValid()) {
                enhancer.onExtractionTick(level, extractionContext);
            }
        }

        int targetMb = extractionContext.finalAmountMb();
        if (targetMb <= 0) {
            return;
        }

        int accepted = wellhead.extractWellheadFluid(targetMb, composition, false);
        int overflow = targetMb - accepted;
        if (overflow > 0) {
            // Дополнительный скачок давления при попытке закачать в переполненный буфер
            float current = wellhead.getPressureBar();
            float extraDelta = (overflow / 1000.0f) * (2.5f + current * 0.05f);
            tickPressure(level, extraDelta, false);
        }

        UUID veinId = wellhead.getActiveVeinId();
        if (veinId != null && accepted > 0) {
            OilVeinManager.get(level).consumeVeinUnits(veinId, accepted);
        }

        if (level.random.nextFloat() < extractionContext.depletionChance) {
            level.setBlock(pocketPos, Blocks.GRAVEL.defaultBlockState(), 3);
            BlockPos nextPocket = drive.findAdjacentPocketBlock(level);
            if (nextPocket != null) {
                OilPocketHelper.ResolvedPocket resolved = OilPocketHelper.resolve(level, nextPocket);
                if (resolved != null) {
                    wellhead.bindReservoir(resolved.composition(), resolved.pos(), resolved.veinId());
                    return;
                }
            }
            wellhead.bindReservoir(null, null, null);
        }
    }

    private void tickPressure(ServerLevel level, float immediateSpike, boolean applyPerTickRamp) {
        if (wellhead == null) return;

        float current = wellhead.getPressureBar();
        int amount = wellhead.getFluidAmount();
        int capacity = Math.max(1, wellhead.getFluidCapacity());
        boolean isBufferFull = amount >= capacity;

        float delta = immediateSpike;
        if (applyPerTickRamp) {
            if (state == RigAssemblyState.EXTRACTING && isBufferFull) {
                // Чем выше текущее давление, тем быстрее оно растёт при переполненном буфере
                delta += 0.08f + current * 0.006f;
            } else if (!isBufferFull && current > 0.0f) {
                // Плавный сброс давления при наличии места в буфере
                delta -= Math.min(current, 0.25f);
            }
        }

        OilComposition comp = wellhead.getReservoirComposition();
        pressureContext.reset(current, delta, amount, capacity, isBufferFull, comp);
        for (IBlowoutPreventerModule bop : bops) {
            if (bop.isModuleValid()) {
                bop.onPressureTick(level, pressureContext);
            }
        }

        float nextPressure = Math.max(0.0f, current + pressureContext.deltaPressureBar);
        if (Math.abs(nextPressure - current) > 1.0E-4f) {
            wellhead.setPressureBar(nextPressure);
        }

        if (nextPressure >= PRESSURE_DETONATE_THRESHOLD && !pressureContext.blowoutPrevented) {
            wellhead.onBlowoutDetonate(level, drive);
            return;
        }

        if (nextPressure >= PRESSURE_VENT_THRESHOLD && !pressureContext.ventSuppressed) {
            wellhead.onGasVentTick(level);
        }
    }

    private boolean validateModules() {
        if (wellhead != null && !wellhead.isModuleValid()) return false;
        if (drive != null && !drive.isModuleValid()) return false;
        if (feeder != null && !feeder.isModuleValid()) return false;
        for (IBlowoutPreventerModule bop : bops) {
            if (!bop.isModuleValid()) return false;
        }
        for (IWellEnhancerModule enhancer : enhancers) {
            if (!enhancer.isModuleValid()) return false;
        }
        return true;
    }
}
