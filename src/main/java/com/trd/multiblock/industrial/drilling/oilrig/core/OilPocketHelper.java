package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilVeinManager;
import com.trd.api.nature.ore.oil.VeinShape;
import com.trd.block.basic.nature.oil.OilReservoirBlock;
import com.trd.worldgen.feature.oil.OilCrossChunkVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    public static ResolvedPocket resolve(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof OilReservoirBlock reservoirBlock)) {
            return null;
        }

        OilCrossChunkVeins.OilVein vein = OilCrossChunkVeins.getVeinAt(level, pos);
        Holder<Biome> biome = level.getBiome(pos);

        if (vein != null) {
            UUID veinId = OilCrossChunkVeins.veinUuid(level.getSeed(), vein, "oil_pocket_" + vein.seed);
            OilVeinManager manager = OilVeinManager.get(level);
            OilVeinManager.OilVeinData data = manager.getVein(veinId);
            OilComposition comp;
            if (data != null && data.composition != null) {
                comp = data.composition;
            } else {
                comp = OilComposition.generate(
                        vein.cy, vein.seed, biome, vein.shape, vein.reservoirType,
                        vein.isField, vein.fieldName, vein.totalPockets, vein.pocketIndex
                );
            }
            if (reservoirBlock.getReservoirType() != com.trd.api.nature.ore.oil.ReservoirType.TAR_SANDS
                    && comp.getReservoirType() != reservoirBlock.getReservoirType()) {
                comp = comp.withReservoirType(reservoirBlock.getReservoirType());
            }
            return new ResolvedPocket(pos.immutable(), veinId, comp);
        }

        // Фолбэк для ручного размещения блоков залежи в креативе / плоском мире:
        // генерируем детерминированный состав с параметрами типа блока залежи.
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
}
