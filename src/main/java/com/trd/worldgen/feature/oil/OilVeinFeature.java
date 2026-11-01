package com.trd.worldgen.feature.oil;

import com.mojang.serialization.Codec;
import com.trd.api.oil.OilComposition;
import com.trd.api.oil.OilVeinManager;
import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import com.trd.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
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
        ServerLevel serverLevel = level.getLevel();

        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight() - 1;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] placedAny = {false};
        Set<BlockPos> placedBlocks = new HashSet<>();

        OilCrossChunkVeins.forEachVein(level, chunkX, chunkZ, vein -> {
            placedBlocks.clear();

            int safeBiomeX = Mth.clamp(vein.cx, minBX, maxBX);
            int safeBiomeZ = Mth.clamp(vein.cz, minBZ, maxBZ);
            int safeBiomeY = Mth.clamp(vein.cy, worldMinY, worldMaxY);
            Holder<Biome> biome = OilCrossChunkVeins.sampleNoiseBiome(level, safeBiomeX, safeBiomeY, safeBiomeZ);
            if (biome == null) {
                biome = level.getBiome(new BlockPos(safeBiomeX, safeBiomeY, safeBiomeZ));
            }

            if (vein.shape == VeinShape.PUDDLE) {
                // Поверхностные битумные озера/лужи генерируются ТОЛЬКО на суше (в пустынях/снегу/тайге)
                // Если биом жилы — океан или река, полностью пропускаем
                if (isOceanOrRiver(biome)) return;

                boolean isDesert = biome.is(Tags.Biomes.IS_DESERT);

                float hx = vein.horizontalReach() + vein.warpAmp + 1f;
                int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
                int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
                int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
                int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));

                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (x < minBX || x > maxBX || z < minBZ || z > maxBZ) continue;

                        if (vein.fieldValue(x, vein.cy, z) > 1.0) continue;

                        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                        // Строго выше уровня моря (62). Исключаем дно водоемов, глубоководные каньоны и пещеры
                        if (surfaceY < 62 || surfaceY > worldMaxY - 10) continue;

                        // Проверяем локальный биом блока
                        pos.set(x, surfaceY, z);
                        Holder<Biome> localBiome = level.getBiome(pos);
                        if (isOceanOrRiver(localBiome)) continue;

                        // Проверяем блок над поверхностью — не должно быть воды (не залито океаном/рекой)
                        BlockState aboveState = level.getBlockState(pos.set(x, surfaceY + 1, z));
                        if (aboveState.is(Blocks.WATER) || aboveState.liquid()) continue;

                        // === ПРОВЕРКА РЕЛЬЕФА: Защита от сползания в каньоны, овраги и расщелины ===
                        // Перепад высот с соседними блоками не должен превышать 2 блоков по ортогонали
                        int yE = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + 1, z);
                        int yW = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - 1, z);
                        int yS = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z + 1);
                        int yN = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z - 1);

                        int maxStep = Math.max(Math.max(Math.abs(surfaceY - yE), Math.abs(surfaceY - yW)),
                                               Math.max(Math.abs(surfaceY - yS), Math.abs(surfaceY - yN)));
                        if (maxStep > 2) continue; // Обрыв / край каньона

                        // Проверка на 2 блока вдаль — безопасный отступ от резкого сброса высоты в каньон
                        int yE2 = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + 2, z);
                        int yW2 = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - 2, z);
                        int yS2 = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z + 2);
                        int yN2 = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z - 2);

                        int maxStep2 = Math.max(Math.max(Math.abs(surfaceY - yE2), Math.abs(surfaceY - yW2)),
                                                Math.max(Math.abs(surfaceY - yS2), Math.abs(surfaceY - yN2)));
                        if (maxStep2 > 3) continue;

                        // Сплошное заполнение слоя толщиной 2-4 блока (без случайных пропусков)
                        for (int y = surfaceY; y >= surfaceY - 3; y--) {
                            pos.set(x, y, z);
                            BlockState existing = level.getBlockState(pos);
                            if (existing.isAir() || existing.liquid()) continue;

                            BlockState targetState = null;
                            if (isDesert) {
                                if (existing.is(BlockTags.SAND) || existing.is(Blocks.SANDSTONE) || existing.is(Blocks.RED_SANDSTONE)) {
                                    targetState = ModBlocks.OIL_SAND.get().defaultBlockState();
                                }
                            } else {
                                if (existing.is(BlockTags.DIRT) || existing.is(BlockTags.STONE_ORE_REPLACEABLES) || existing.is(Blocks.SNOW_BLOCK)) {
                                    targetState = ModBlocks.OIL_DIRT.get().defaultBlockState();
                                }
                            }

                            if (targetState != null) {
                                level.setBlock(pos, targetState, 2);
                                placedBlocks.add(pos.immutable());
                            }
                        }
                    }
                }
            } else {
                // Подземные залежи (STRATIFIED, DOME, LENTICULAR)
                // Монолитная структура: все блоки внутри эллипсоида связны между собой
                float hx = vein.horizontalReach() + vein.warpAmp + 1f;
                int x0 = Math.max(minBX, (int) Math.floor(vein.cx - hx));
                int x1 = Math.min(maxBX, (int) Math.ceil(vein.cx + hx));
                int z0 = Math.max(minBZ, (int) Math.floor(vein.cz - hx));
                int z1 = Math.min(maxBZ, (int) Math.ceil(vein.cz + hx));
                int y0 = Math.max(worldMinY, (int) Math.floor(vein.cy - vein.ry - vein.warpAmp));
                int y1 = Math.min(worldMaxY, (int) Math.ceil(vein.cy + vein.ry + vein.warpAmp));

                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        // Для каждой вертикальной колонки проверяем уровень дневной поверхности / дна океана
                        int oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                        // Защита кровли пласта (caprock): подземный карман НЕ должен прорываться на дно моря или в открытый каньон
                        int maxReservoirY = oceanFloor - 4;

                        for (int y = y0; y <= y1; y++) {
                            if (y > maxReservoirY) continue; // Сохраняем надежную непроницаемую покрышку из камня/дипслейта
                            if (x < minBX || x > maxBX || z < minBZ || z > maxBZ) continue;

                            if (vein.fieldValue(x, y, z) > 1.0) continue;

                            pos.set(x, y, z);
                            BlockState existing = level.getBlockState(pos);

                            BlockState targetState = null;
                            if (vein.reservoirType == ReservoirType.TIGHT_SHALE) {
                                if (canReplaceDeepslate(existing)) {
                                    targetState = ModBlocks.DEEPSLATE_OIL_SHALE.get().defaultBlockState();
                                }
                            } else {
                                if (canReplaceStone(existing)) {
                                    targetState = ModBlocks.POROUS_OIL_STONE.get().defaultBlockState();
                                }
                            }

                            if (targetState != null) {
                                level.setBlock(pos, targetState, 2);
                                placedBlocks.add(pos.immutable());
                            }
                        }
                    }
                }

                // === НЕФТЕПРОЯВЛЕНИЕ НА ПОВЕРХНОСТИ (SURFACE OIL SEEP) ===
                // Органическая форма "клякса" с рваными шумовыми краями над подземным карманом
                // В океанах, морях и реках поверхностные следы НЕ создаются (офшорные залежи скрыты под водой)
                if (!isOceanOrRiver(biome)) {
                    float baseRadius = 8.0f + (OilCrossChunkVeins.blockRandom(vein.seed ^ 0x5EEFL, vein.cx, 0, vein.cz) * 5.0f); // R = 8.0..13.0
                    int maxMargin = (int) Math.ceil(baseRadius * 1.6f);

                    int sx0 = Math.max(minBX, vein.cx - maxMargin);
                    int sx1 = Math.min(maxBX, vein.cx + maxMargin);
                    int sz0 = Math.max(minBZ, vein.cz - maxMargin);
                    int sz1 = Math.min(maxBZ, vein.cz + maxMargin);

                    if (sx0 <= sx1 && sz0 <= sz1) {
                        for (int sx = sx0; sx <= sx1; sx++) {
                            for (int sz = sz0; sz <= sz1; sz++) {
                                double ddx = sx - vein.cx;
                                double ddz = sz - vein.cz;
                                double dist = Math.sqrt(ddx * ddx + ddz * ddz);

                                // Шумовая деформация контура ("клякса" / неравномерные выступы)
                                double angle = Math.atan2(ddz, ddx);
                                double nAngle = Math.sin(angle * 3.0 + (vein.seed & 0xFF)) * 0.22
                                        + Math.cos(angle * 5.0 - ((vein.seed >> 8) & 0xFF)) * 0.15;
                                double nSpatial = OilCrossChunkVeins.valueNoise(vein.seed ^ 0x7E57L, sx * 0.16, 0.5, sz * 0.16) * 0.35;
                                double effectiveRadius = baseRadius * (1.0 + nAngle + nSpatial);

                                if (dist > effectiveRadius) continue;

                                int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx, sz);
                                if (surfaceY < 62 || surfaceY > worldMaxY - 10) continue;

                                pos.set(sx, surfaceY, sz);
                                Holder<Biome> localBiome = level.getBiome(pos);
                                if (isOceanOrRiver(localBiome)) continue;

                                BlockState above = level.getBlockState(pos.set(sx, surfaceY + 1, sz));
                                if (above.is(Blocks.WATER) || above.liquid()) continue;

                                // Проверка склона / края обрыва
                                int yE = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx + 1, sz);
                                int yW = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx - 1, sz);
                                int yS = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx, sz + 1);
                                int yN = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx, sz - 1);

                                int maxStep = Math.max(Math.max(Math.abs(surfaceY - yE), Math.abs(surfaceY - yW)),
                                                       Math.max(Math.abs(surfaceY - yS), Math.abs(surfaceY - yN)));
                                if (maxStep > 1) continue; // На обрывах пятно аккуратно обрывается

                                boolean isLocalDesert = localBiome.is(Tags.Biomes.IS_DESERT);

                                // Заменяем верхние 1-2 блока поверхности
                                for (int sy = surfaceY; sy >= surfaceY - 1; sy--) {
                                    pos.set(sx, sy, sz);
                                    BlockState existing = level.getBlockState(pos);
                                    if (existing.isAir() || existing.liquid()) continue;

                                    BlockState seepBlock = null;
                                    if (isLocalDesert) {
                                        // В пустыне — битуминозный песок
                                        if (existing.is(BlockTags.SAND) || existing.is(Blocks.SANDSTONE)) {
                                            seepBlock = ModBlocks.OIL_SAND.get().defaultBlockState();
                                        }
                                    } else {
                                        // Во всех остальных случаях: 70% каменистая земля, 30% нефтяная земля
                                        if (existing.is(BlockTags.DIRT) || existing.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                                            float roll = OilCrossChunkVeins.blockRandom(vein.seed ^ 0xC0A5EL, sx, sy, sz);
                                            if (roll < 0.70f) {
                                                seepBlock = Blocks.COARSE_DIRT.defaultBlockState();
                                            } else {
                                                seepBlock = ModBlocks.OIL_DIRT.get().defaultBlockState();
                                            }
                                        }
                                    }

                                    if (seepBlock != null) {
                                        level.setBlock(pos, seepBlock, 2);
                                        // ВАЖНО: Мы НЕ присваиваем veinId и НЕ добавляем блок в placedBlocks!
                                        // Поверхностное пятно — это только визуальный маркер, керн берется только из подземного кармана.

                                        // Счищаем растительность сверху, чтобы клякса контрастно выделялась на местности
                                        BlockPos abovePos = pos.above();
                                        BlockState aboveState = level.getBlockState(abovePos);
                                        if (aboveState.is(BlockTags.FLOWERS) || aboveState.is(Blocks.SHORT_GRASS) || aboveState.is(Blocks.FERN)
                                                || aboveState.is(Blocks.TALL_GRASS) || aboveState.is(Blocks.DEAD_BUSH)) {
                                            level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), 2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (placedBlocks.isEmpty()) return;

            UUID veinId = OilCrossChunkVeins.veinUuid(serverLevel.getSeed(), vein, "oil_pocket_" + vein.seed);
            OilComposition composition = OilComposition.generate(vein.cy, vein.seed, biome, vein.shape, vein.reservoirType,
                    vein.isField, vein.fieldName, vein.totalPockets, vein.pocketIndex);
            OilVeinManager.get(serverLevel).registerVeinPortion(veinId, placedBlocks, composition, vein.cy);

            placedAny[0] = true;
        });

        return placedAny[0];
    }

    private static boolean isOceanOrRiver(Holder<Biome> biome) {
        if (biome == null) return false;
        return biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_RIVER);
    }

    private static boolean canReplaceDeepslate(BlockState state) {
        if (state.isAir() || state.liquid()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        return state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(Blocks.TUFF)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT);
    }

    private static boolean canReplaceStone(BlockState state) {
        if (state.isAir() || state.liquid()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        return state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.TUFF)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT);
    }
}
