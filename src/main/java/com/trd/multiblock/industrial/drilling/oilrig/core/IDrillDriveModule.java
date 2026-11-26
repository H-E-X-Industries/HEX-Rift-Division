package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Привод буровой колонны (роторный стол RotaryTable и его аналоги).
 */
public interface IDrillDriveModule extends IRigModule {

    Direction getDriveFacing();

    BlockPos getWellCenterPos();

    int getPlatformY();

    long getCurrentRpm();

    default boolean isOptimalRpm() {
        long abs = Math.abs(getCurrentRpm());
        return abs >= 45L && abs <= 75L;
    }

    boolean isRedstoneEnabled();

    boolean isDerrickAssembled();

    boolean isPlatformValid(Level level);

    int getTowerAnimTick();

    void setTowerAnimTick(int tick);

    boolean isTowerAnimPaused();

    void setTowerAnimPaused(boolean paused);

    boolean isHoldingPipe();

    void setHoldingPipe(boolean holding);

    int getPipeCountInWell();

    boolean isDescendingPipe();

    void beginPipeDescent(ServerLevel level);

    /**
     * Выполняет один тик плавного спуска и бурения.
     *
     * @return true, когда полный спуск на 4 блока полностью завершён
     */
    boolean tickPipeDescent(ServerLevel level);

    @Nullable
    BlockPos getDetectedPocketPos();

    void clearDetectedPocketPos();

    /** Останавливает вращение роторного диска и анимацию вышки при переходе к добыче. */
    void stopRotaryForExtraction(ServerLevel level);

    /** Возобновляет режим бурения (если карман истощился и требуется дальнейшее бурение). */
    void resumeDrillingFromExtraction(ServerLevel level);

    /** Удаляет физические трубы в скважине и возвращает их количество для выброса при взрыве. */
    int purgeWellborePipesForBlowout(ServerLevel level);

    /** Ищет живой блок нефтяного кармана рядом с самой нижней трубой (после истощения текущего блока). */
    @Nullable
    BlockPos findAdjacentPocketBlock(ServerLevel level);

    /** Пересчитывает фактическую непрерывную колонну труб после разрушения/установки трубы в скважине. */
    void onWellborePipeChanged(ServerLevel level, @Nullable BlockPos removedPos);

    /** Проверяет, доходит ли непрерывная верхняя колонна труб до указанного блока кармана. */
    boolean isWellboreReachingPocket(BlockPos pocketPos);

    /** Объявленные сокеты для привязки боковых модулей (например, PipeDrum со стороны локальной -Z). */
    default List<LateralSocket> getLateralSockets() {
        return List.of(new LateralSocket(
                "pipe_drum_north",
                RigModuleRole.PIPE_FEEDER,
                new BlockPos(0, 0, -3),
                Direction.NORTH
        ));
    }
}
