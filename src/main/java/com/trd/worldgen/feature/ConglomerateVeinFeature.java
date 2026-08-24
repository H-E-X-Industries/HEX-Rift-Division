package com.trd.worldgen.feature;

import com.trd.api.vein.VeinCompositionGenerator;
import com.trd.api.vein.VeinManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.conglomerate.ConglomerateBlockEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ConglomerateVeinFeature extends Feature<ConglomerateVeinConfiguration> {

    private static final long DEPLETION_SALT = 0x51ED270B19C0FFEEL;

    public ConglomerateVeinFeature(Codec<ConglomerateVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ConglomerateVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        ConglomerateVeinConfiguration cfg = context.config();

        ServerLevel serverLevel = level.getLevel();
        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight() - 1;

        CrossChunkVeins.ShapeParams params = new CrossChunkVeins.ShapeParams(
                cfg.veinId(), cfg.minSize(), cfg.maxSize(), cfg.minY(), cfg.maxY(),
                cfg.maxStretch(), 0.15f, cfg.rarity());

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] placedAny = {false};

        Set<BlockPos> normalBlocks = new HashSet<>();
        Set<BlockPos> depletedBlocks = new HashSet<>();

        CrossChunkVeins.forEachVein(level, chunkX, chunkZ, params, vein -> {
            float hx = vein.horizontalReach() + vein.warpAmp + 1f;
            int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
            int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
            int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
            int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));
            int y0 = Math.max(worldMinY, (int) Math.floor(vein.cy - vein.ry - vein.warpAmp));
            int y1 = Math.min(worldMaxY, (int) Math.ceil(vein.cy + vein.ry + vein.warpAmp));

            normalBlocks.clear();
            depletedBlocks.clear();

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        pos.set(x, y, z);

                        // плотность — чистая функция координат (бесшовно между чанками)
                        if (CrossChunkVeins.blockRandom(vein.seed, x, y, z) > cfg.density()) continue;

                        BlockState existing = level.getBlockState(pos);
                        if (!isReplaceable(existing)) continue;

                        if (vein.fieldValue(x, y, z) > 1.0) continue;

                        if (CrossChunkVeins.blockRandom(vein.seed ^ DEPLETION_SALT, x, y, z) < cfg.depletionChance()) {
                            depletedBlocks.add(pos.immutable());
                        } else {
                            normalBlocks.add(pos.immutable());
                        }
                    }
                }
            }

            if (normalBlocks.isEmpty()) return;

            // Состав жилы детерминирован сидом жилы — все чанки получают одинаковый
            var composition = VeinCompositionGenerator.generate(
                    vein.cy, RandomSource.create(vein.seed ^ 0xC0FFEE1234L));

            UUID veinId = CrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, cfg.veinId());
            VeinManager.get(serverLevel).registerVeinPortion(veinId, normalBlocks, composition, vein.cy);

            for (BlockPos p : depletedBlocks) {
                level.setBlock(p, ModBlocks.DEPLETED_CONGLOMERATE.get().defaultBlockState(), 2);
            }

            for (BlockPos p : normalBlocks) {
                level.setBlock(p, ModBlocks.CONGLOMERATE.get().defaultBlockState(), 2);
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof ConglomerateBlockEntity cbe) {
                    cbe.setVeinId(veinId);
                }
            }
            placedAny[0] = true;
        });

        return placedAny[0];
    }

    private boolean isReplaceable(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }
}
