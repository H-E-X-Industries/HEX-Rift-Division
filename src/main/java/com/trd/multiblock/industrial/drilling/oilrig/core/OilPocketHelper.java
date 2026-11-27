package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilVeinManager;
import com.trd.api.nature.ore.oil.ReservoirType;
import com.trd.api.nature.ore.oil.VeinShape;
import com.trd.block.basic.nature.oil.OilReservoirBlock;
import com.trd.worldgen.feature.oil.OilCrossChunkVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Утилита разрешения параметров нефтяного кармана по мировым координатам.
 */
public final class OilPocketHelper {

    public record ResolvedPocket(
            BlockPos pos,
            @Nullable UUID veinId,
            OilComposition composition
    ) {}

    private OilPocketHelper() {}

    public static boolean isReservoirBlock(BlockState state) {
        return state.getBlock() instanceof OilReservoirBlock;
    }

    /**
     * Проверяет, содержит ли блок кармана доступные к выкачке запасы.
     */
    public static boolean hasExtractableOil(ServerLevel level, BlockPos pos) {
        if (!isReservoirBlock(level.getBlockState(pos))) {
            return false;
        }
        OilCrossChunkVeins.OilVein vein = OilCrossChunkVeins.getVeinAt(level, pos);
        if (vein != null) {
            UUID veinId = OilCrossChunkVeins.veinUuid(level.getSeed(), vein);
            OilVeinManager.OilVeinData data = OilVeinManager.get(level).getVein(veinId);
            if (data != null && data.remainingUnits <= 0L) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static ResolvedPocket resolve(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof OilReservoirBlock reservoirBlock)) {
            return null;
        }

        OilCrossChunkVeins.OilVein vein = OilCrossChunkVeins.getVeinAt(level, pos);

        if (vein != null) {
            UUID veinId = OilCrossChunkVeins.veinUuid(level.getSeed(), vein);
            OilComposition comp = OilCrossChunkVeins.resolveAuthoritativeComposition(level, vein, pos);
            if (reservoirBlock.getReservoirType() != ReservoirType.TAR_SANDS
                    && comp.getReservoirType() != reservoirBlock.getReservoirType()) {
                comp = comp.withReservoirType(reservoirBlock.getReservoirType());
            }
            OilVeinManager.OilVeinData veinData = OilVeinManager.get(level).ensureVeinRegistered(
                    veinId, comp, vein.estimateBlockCount(), vein.cy,
                    new ChunkPos(vein.cx >> 4, vein.cz >> 4)
            );
            if (veinData.remainingUnits <= 0L) {
                return null;
            }
            return new ResolvedPocket(pos.immutable(), veinId, comp);
        }

        // Фолбэк для ручного размещения блоков залежи в креативе / плоском мире:
        // генерируем детерминированный состав с параметрами типа блока залежи.
        Holder<Biome> biome = level.getBiome(pos);
        long fallbackSeed = Mth.getSeed(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4) ^ level.getSeed();
        OilComposition fallback = OilComposition.generate(
                pos.getY(),
                fallbackSeed,
                biome,
                VeinShape.STRATIFIED,
                reservoirBlock.getReservoirType(),
                false,
                "",
                1,
                0
        );
        return new ResolvedPocket(pos.immutable(), null, fallback);
    }

    /**
     * Заменяет 1 случайный живой блок указанной жилы на гравий в уже прогруженных чанках.
     */
    public static boolean replaceRandomVeinBlockWithGravel(ServerLevel level, @Nullable UUID veinId, BlockPos anchorPos) {
        OilCrossChunkVeins.OilVein vein = OilCrossChunkVeins.getVeinAt(level, anchorPos);
        if (vein == null) {
            return false;
        }
        if (veinId != null && !veinId.equals(OilCrossChunkVeins.veinUuid(level.getSeed(), vein))) {
            return false;
        }

        int minBuildY = level.getMinBuildHeight();
        int maxBuildY = level.getMaxBuildHeight() - 1;
        float warp = vein.getWarpAmp();
        float hx = vein.horizontalReach() + warp + 1.0f;
        int minX = (int) Math.floor(vein.cx - hx);
        int maxX = (int) Math.ceil(vein.cx + hx);
        int minZ = (int) Math.floor(vein.cz - hx);
        int maxZ = (int) Math.ceil(vein.cz + hx);

        int minY;
        int maxY;
        switch (vein.shape) {
            case PUDDLE -> {
                minY = Math.max(minBuildY, 50);
                maxY = maxBuildY;
            }
            case STRATIFIED -> {
                minY = Math.max(minBuildY, (int) Math.floor(vein.cy - vein.ry - 0.8));
                maxY = Math.min(maxBuildY, (int) Math.ceil(vein.cy + vein.ry + 0.8));
            }
            case DOME -> {
                minY = Math.max(minBuildY, (int) Math.floor(vein.cy - 0.15 * vein.ry));
                maxY = Math.min(maxBuildY, (int) Math.ceil(vein.cy + vein.ry + warp));
            }
            case DIAPIR -> {
                minY = Math.max(minBuildY, (int) Math.floor(vein.cy - vein.ry));
                maxY = Math.min(maxBuildY, (int) Math.ceil(vein.cy + vein.ry));
            }
            default -> {
                minY = Math.max(minBuildY, (int) Math.floor(vein.cy - vein.ry - warp));
                maxY = Math.min(maxBuildY, (int) Math.ceil(vein.cy + vein.ry + warp));
            }
        }
        if (maxX < minX || maxZ < minZ || maxY < minY) {
            return false;
        }

        RandomSource random = level.random;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        if (tryRandomGravelReplace(level, vein, random, minX, maxX, minY, maxY, minZ, maxZ, 48, cursor)) {
            return true;
        }

        int localMinX = Math.max(minX, anchorPos.getX() - 20);
        int localMaxX = Math.min(maxX, anchorPos.getX() + 20);
        int localMinZ = Math.max(minZ, anchorPos.getZ() - 20);
        int localMaxZ = Math.min(maxZ, anchorPos.getZ() + 20);
        if (localMinX <= localMaxX && localMinZ <= localMaxZ
                && tryRandomGravelReplace(level, vein, random, localMinX, localMaxX, minY, maxY, localMinZ, localMaxZ, 24, cursor)) {
            return true;
        }

        List<BlockPos> candidates = new ArrayList<>();
        int scanMinY = Math.max(minBuildY, anchorPos.getY() - 3);
        int scanMaxY = Math.min(maxBuildY, anchorPos.getY() + 3);
        for (int x = anchorPos.getX() - 6; x <= anchorPos.getX() + 6; x++) {
            for (int z = anchorPos.getZ() - 6; z <= anchorPos.getZ() + 6; z++) {
                if (!isChunkAndNeighborsLoaded(level, x, z)) continue;
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    int fieldY = vein.shape == VeinShape.PUDDLE ? vein.cy : y;
                    if (vein.fieldValue(x, fieldY, z) > 1.0) continue;
                    cursor.set(x, y, z);
                    if (isReservoirBlock(level.getBlockState(cursor))) {
                        candidates.add(cursor.immutable());
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            BlockPos chosen = candidates.get(random.nextInt(candidates.size()));
            level.setBlock(chosen, Blocks.GRAVEL.defaultBlockState(), 3);
            return true;
        }
        return false;
    }

    private static boolean tryRandomGravelReplace(ServerLevel level,
                                                  OilCrossChunkVeins.OilVein vein,
                                                  RandomSource random,
                                                  int minX, int maxX,
                                                  int minY, int maxY,
                                                  int minZ, int maxZ,
                                                  int attempts,
                                                  BlockPos.MutableBlockPos cursor) {
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int spanY = maxY - minY + 1;
        for (int i = 0; i < attempts; i++) {
            int rx = minX + random.nextInt(spanX);
            int rz = minZ + random.nextInt(spanZ);
            if (vein.shape == VeinShape.PUDDLE) {
                if (vein.fieldValue(rx, vein.cy, rz) > 1.0) continue;
                if (!isChunkAndNeighborsLoaded(level, rx, rz)) continue;
                int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, rx, rz);
                int ry = surfaceY - random.nextInt(4);
                if (ry < minY || ry > maxY) continue;
                cursor.set(rx, ry, rz);
            } else {
                int ry = minY + random.nextInt(spanY);
                if (vein.fieldValue(rx, ry, rz) > 1.0) continue;
                if (!isChunkAndNeighborsLoaded(level, rx, rz)) continue;
                cursor.set(rx, ry, rz);
            }
            if (isReservoirBlock(level.getBlockState(cursor))) {
                level.setBlock(cursor, Blocks.GRAVEL.defaultBlockState(), 3);
                return true;
            }
        }
        return false;
    }

    private static boolean isChunkAndNeighborsLoaded(ServerLevel level, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (!level.hasChunk(cx, cz)) return false;
        int lx = x & 15;
        int lz = z & 15;
        if (lx == 0 && !level.hasChunk(cx - 1, cz)) return false;
        if (lx == 15 && !level.hasChunk(cx + 1, cz)) return false;
        if (lz == 0 && !level.hasChunk(cx, cz - 1)) return false;
        if (lz == 15 && !level.hasChunk(cx, cz + 1)) return false;
        return true;
    }
}
