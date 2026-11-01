package com.trd.worldgen.feature.oil;

import com.mojang.serialization.Codec;
import com.trd.api.oil.OilComposition;
import com.trd.api.oil.OilVeinManager;
import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.oil.OilReservoirBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.neoforged.neoforge.common.Tags;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OilVeinFeature extends Feature<OilVeinConfiguration> {

    public OilVeinFeature(Codec<OilVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OilVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        OilVeinConfiguration cfg = context.config();
        ServerLevel serverLevel = level.getLevel();

        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight() - 1;

        OilCrossChunkVeins.ShapeParams params = new OilCrossChunkVeins.ShapeParams(
                cfg.veinId(), cfg.minSize(), cfg.maxSize(), cfg.minY(), cfg.maxY(),
                cfg.maxStretch(), 0.15f, cfg.rarity(), cfg.shape(), cfg.reservoirType()
        );

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] placedAny = {false};
        Set<BlockPos> placedBlocks = new HashSet<>();

        OilCrossChunkVeins.forEachVein(level, chunkX, chunkZ, params, vein -> {
            placedBlocks.clear();

            int safeBiomeX = Mth.clamp(vein.cx, minBX, maxBX);
            int safeBiomeZ = Mth.clamp(vein.cz, minBZ, maxBZ);
            Holder<Biome> biome = level.getBiome(new BlockPos(safeBiomeX, vein.cy, safeBiomeZ));

            if (vein.shape == VeinShape.PUDDLE) {
                // Поверхностная лужа: разрешена ТОЛЬКО в пустынях и снежных биомах/тундре
                boolean isDesert = biome.is(Tags.Biomes.IS_DESERT);
                boolean isSnowy = biome.is(Tags.Biomes.IS_SNOWY) || biome.is(BiomeTags.IS_TAIGA);

                if (!isDesert && !isSnowy) {
                    return; // В других биомах поверхностные лужи не появляются
                }

                float hx = vein.horizontalReach() + vein.warpAmp + 1f;
                int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
                int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
                int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
                int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));

                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (x < minBX || x > maxBX || z < minBZ || z > maxBZ) continue;

                        if (OilCrossChunkVeins.blockRandom(vein.seed, x, vein.cy, z) > cfg.density()) continue;
                        if (vein.fieldValue(x, vein.cy, z) > 1.0) continue;

                        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                        if (surfaceY < worldMinY || surfaceY > worldMaxY) continue;

                        // Заменяем тонкий слой 1-3 блока от поверхности
                        for (int y = surfaceY; y >= surfaceY - 2; y--) {
                            pos.set(x, y, z);
                            BlockState existing = level.getBlockState(pos);

                            BlockState targetState = null;
                            if (isDesert && existing.is(BlockTags.SAND)) {
                                targetState = ModBlocks.OIL_SAND.get().defaultBlockState();
                            } else if (isSnowy && existing.is(BlockTags.DIRT)) {
                                targetState = ModBlocks.OIL_DIRT.get().defaultBlockState();
                            }

                            if (targetState != null) {
                                level.setBlock(pos, targetState, 2);
                                BlockEntity be = level.getBlockEntity(pos);
                                UUID veinId = OilCrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, cfg.veinId());
                                if (be instanceof OilReservoirBlockEntity oilBe) {
                                    oilBe.setVeinId(veinId);
                                }
                                placedBlocks.add(pos.immutable());
                            }
                        }
                    }
                }
            } else {
                // Подземные залежи (пористый песчаник в камне или тонкий пласт в сланце)
                float hx = vein.horizontalReach() + vein.warpAmp + 1f;
                int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
                int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
                int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
                int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));
                int y0 = Math.max(worldMinY, (int) Math.floor(vein.cy - vein.ry - vein.warpAmp));
                int y1 = Math.min(worldMaxY, (int) Math.ceil(vein.cy + vein.ry + vein.warpAmp));

                BlockState targetState = vein.reservoirType == ReservoirType.TIGHT_SHALE
                        ? ModBlocks.DEEPSLATE_OIL_SHALE.get().defaultBlockState()
                        : ModBlocks.POROUS_OIL_STONE.get().defaultBlockState();

                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = y0; y <= y1; y++) {
                            if (x < minBX || x > maxBX || z < minBZ || z > maxBZ) continue;

                            if (OilCrossChunkVeins.blockRandom(vein.seed, x, y, z) > cfg.density()) continue;
                            if (vein.fieldValue(x, y, z) > 1.0) continue;

                            pos.set(x, y, z);
                            BlockState existing = level.getBlockState(pos);

                            boolean replaceable = false;
                            if (vein.reservoirType == ReservoirType.TIGHT_SHALE) {
                                replaceable = existing.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
                            } else {
                                replaceable = existing.is(BlockTags.STONE_ORE_REPLACEABLES);
                            }

                            if (replaceable) {
                                level.setBlock(pos, targetState, 2);
                                BlockEntity be = level.getBlockEntity(pos);
                                UUID veinId = OilCrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, cfg.veinId());
                                if (be instanceof OilReservoirBlockEntity oilBe) {
                                    oilBe.setVeinId(veinId);
                                }
                                placedBlocks.add(pos.immutable());
                            }
                        }
                    }
                }
            }

            if (placedBlocks.isEmpty()) return;

            UUID veinId = OilCrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, cfg.veinId());
            OilComposition composition = OilComposition.generate(vein.cy, vein.seed, biome, vein.shape, vein.reservoirType);
            OilVeinManager.get(serverLevel).registerVeinPortion(veinId, placedBlocks, composition, vein.cy);

            placedAny[0] = true;
        });

        return placedAny[0];
    }
}
