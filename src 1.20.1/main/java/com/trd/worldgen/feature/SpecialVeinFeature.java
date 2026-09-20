package com.trd.worldgen.feature;

import com.trd.api.vein.VeinBiomeResolver;
import com.trd.api.vein.VeinCompositionGenerator;
import com.trd.api.vein.VeinManager;
import com.trd.api.vein.VeinModifier;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpecialVeinFeature extends Feature<SpecialVeinConfiguration> {

    public SpecialVeinFeature(Codec<SpecialVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpecialVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        SpecialVeinConfiguration cfg = context.config();
        ServerLevel serverLevel = level.getLevel();

        // Якорь фичи игнорируется: жилы привязаны к ячейкам региона и детерминированы
        // (см. CrossChunkVeins), поэтому каждый чанк сам достраивает свою часть всех жил.
        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight() - 1;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] placed = {false};

        CrossChunkVeins.ShapeParams params = new CrossChunkVeins.ShapeParams(
                cfg.veinId(), cfg.minSize(), cfg.maxSize(), cfg.minY(), cfg.maxY(),
                cfg.maxStretch(), cfg.noiseScale(), cfg.rarity());

        Set<BlockPos> portion = new HashSet<>();

        CrossChunkVeins.forEachVein(level, chunkX, chunkZ, params, vein -> {
            float hx = vein.horizontalReach() + vein.warpAmp + 1f;
            int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
            int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
            int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
            int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));
            int y0 = Math.max(worldMinY, (int) Math.floor(vein.cy - vein.ry - vein.warpAmp));
            int y1 = Math.min(worldMaxY, (int) Math.ceil(vein.cy + vein.ry + vein.warpAmp));

            portion.clear();

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        pos.set(x, y, z);

                        // плотность — чистая функция координат: бесшовно между чанками,
                        // и дешёвый отсев до дорогого шума
                        if (CrossChunkVeins.blockRandom(vein.seed, x, y, z) > cfg.density()) continue;

                        BlockState existing = level.getBlockState(pos);
                        if (cfg.respectAir() && existing.isAir()) continue;

                        if (vein.fieldValue(x, y, z) > 1.0) continue;

                        boolean replaced = false;
                        for (OreConfiguration.TargetBlockState target : cfg.targets()) {
                            if (target.target.test(existing, context.random())) {
                                level.setBlock(pos, cfg.state(), 2);
                                replaced = true;
                                break;
                            }
                        }
                        if (replaced) {
                            portion.add(pos.immutable());
                            placed[0] = true;
                        }
                    }
                }
            }

            if (portion.isEmpty() || serverLevel == null) return;

            // Регистрируем свою порцию жилы в менеджере (детерминированный UUID:
            // соседние чанки сольют свои порции с этой же записью).
            // Модификатор биома — чистая функция координат (биом центра жилы детерминирован),
            // поэтому состав жилы остаётся одинаковым во всех чанках.
            VeinModifier modifier = VeinBiomeResolver.of(level.getBiome(BlockPos.containing(vein.cx, vein.cy, vein.cz)));
            var composition = VeinCompositionGenerator.generate(
                    vein.cy, RandomSource.create(vein.seed ^ 0xC0FFEE1234L), modifier);
            UUID veinId = CrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, cfg.veinId());
            VeinManager.get(serverLevel).registerVeinPortion(veinId, portion, composition, vein.cy);
        });

        return placed[0];
    }
}

