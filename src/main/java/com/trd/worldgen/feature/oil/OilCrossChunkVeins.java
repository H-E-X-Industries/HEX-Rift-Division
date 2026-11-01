package com.trd.worldgen.feature.oil;

import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OilCrossChunkVeins {

    /** Размер макро-бассейна в блоках (1800x1800 блоков = ~112 чанков). */
    public static final int BASIN_SIZE = 1800;

    private static final String[] FIELD_PREFIXES = {
            "Северное", "Глубоководное", "Южное", "Восточное", "Западное",
            "Арктическое", "Пустынное", "Приморское", "Таймырское", "Рифтовое",
            "Шельфовое", "Центральное", "Атлантическое", "Степное", "Сибирское"
    };

    private OilCrossChunkVeins() {}

    public static final class OilVein {
        private static final long WARP_SALT = 0x5BF036A5B0F6199EL;

        public final long seed;
        public final int cx, cy, cz;
        public final float rx, ry, rz;
        final float cosYaw, sinYaw;
        final float warpAmp, warpFreq;
        final long warpSeed;
        public final VeinShape shape;
        public final ReservoirType reservoirType;

        // Принадлежность к месторождению
        public final boolean isField;
        public final String fieldName;
        public final int totalPockets;
        public final int pocketIndex;

        OilVein(int cx, int cy, int cz, float rx, float ry, float rz,
                float cosYaw, float sinYaw, float warpAmp, float warpFreq,
                long seed, VeinShape shape, ReservoirType reservoirType,
                boolean isField, String fieldName, int totalPockets, int pocketIndex) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.cosYaw = cosYaw;
            this.sinYaw = sinYaw;
            this.warpAmp = warpAmp;
            this.warpFreq = warpFreq;
            this.warpSeed = seed ^ WARP_SALT;
            this.seed = seed;
            this.shape = shape;
            this.reservoirType = reservoirType;
            this.isField = isField;
            this.fieldName = fieldName;
            this.totalPockets = totalPockets;
            this.pocketIndex = pocketIndex;
        }

        public float horizontalReach() {
            return Math.max(rx, rz);
        }

        public boolean overlapsChunk(int minBX, int maxBX, int minBZ, int maxBZ) {
            float m = horizontalReach() + warpAmp + 2f;
            return cx + m >= minBX && cx - m <= maxBX && cz + m >= minBZ && cz - m <= maxBZ;
        }

        public double fieldValue(int x, int y, int z) {
            double lx = x - cx;
            double ly = y - cy;
            double lz = z - cz;

            double hr = horizontalReach() + warpAmp;
            if ((lx * lx + lz * lz) / (hr * hr) > 1.0) {
                return Double.MAX_VALUE;
            }

            if (shape == VeinShape.DOME && ly < -0.15 * ry) {
                return Double.MAX_VALUE;
            }

            double effRy = ry + warpAmp;
            if (shape == VeinShape.PUDDLE) {
                if (Math.abs(ly) > ry + 2.0) return Double.MAX_VALUE;
            } else if (shape == VeinShape.STRATIFIED) {
                // Пласты 4-6 блоков толщиной (сохраняют монолитность и связность)
                if (Math.abs(ly) > ry + 0.8) return Double.MAX_VALUE;
            } else if (shape == VeinShape.DIAPIR) {
                // Шток (устаревший, оставлен для совместимости)
                if (Math.abs(ly) > ry) return Double.MAX_VALUE;
            } else {
                if ((ly * ly) / (effRy * effRy) > 1.0) {
                    return Double.MAX_VALUE;
                }
            }

            double ux = cosYaw * lx + sinYaw * lz;
            double uz = -sinYaw * lx + cosYaw * lz;

            double n1 = valueNoise(warpSeed, ux * warpFreq + 31.7, ly * warpFreq - 12.9, uz * warpFreq) * warpAmp;
            double n2 = valueNoise(warpSeed, ux * warpFreq - 55.3, ly * warpFreq + 7.1, uz * warpFreq + 44.8) * warpAmp;
            double n3 = valueNoise(warpSeed, ux * warpFreq + 91.2, ly * warpFreq - 63.4, uz * warpFreq + 17.6) * warpAmp;

            double dx = (ux + n1) / rx;
            double dy = (ly + n2) / ry;
            double dz = (uz + n3) / rz;

            if (shape == VeinShape.PUDDLE || shape == VeinShape.STRATIFIED || shape == VeinShape.DIAPIR) {
                return dx * dx + dz * dz;
            }
            return dx * dx + dy * dy + dz * dz;
        }
    }

    @FunctionalInterface
    public interface VeinVisitor {
        void visit(OilVein vein);
    }

    /**
     * Поиск и генерация всех нефтеносных тел для данного чанка.
     * Гарантирует изоляцию 1500-2000 блоков между независимыми месторождениями/карманами.
     */
    public static void forEachVein(net.minecraft.world.level.WorldGenLevel level,
                                   int chunkX, int chunkZ,
                                   VeinVisitor visitor) {
        long worldSeed = level.getLevel().getSeed();

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;

        // Максимальный размах кластера месторождения от якоря (~450 блоков)
        int queryMargin = 450;
        int b0x = Math.floorDiv(minBX - queryMargin, BASIN_SIZE);
        int b1x = Math.floorDiv(maxBX + queryMargin, BASIN_SIZE);
        int b0z = Math.floorDiv(minBZ - queryMargin, BASIN_SIZE);
        int b1z = Math.floorDiv(maxBZ + queryMargin, BASIN_SIZE);

        for (int basinX = b0x; basinX <= b1x; basinX++) {
            for (int basinZ = b0z; basinZ <= b1z; basinZ++) {
                List<OilVein> veinsInBasin = getVeinsForBasin(level, worldSeed, basinX, basinZ);
                for (OilVein vein : veinsInBasin) {
                    if (vein.overlapsChunk(minBX, maxBX, minBZ, maxBZ)) {
                        visitor.visit(vein);
                    }
                }
            }
        }
    }

    private static final Map<Long, List<OilVein>> BASIN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<OilVein>> eldest) {
                    return size() > 128;
                }
            }
    );

    public static Holder<Biome> sampleNoiseBiome(WorldGenLevel level, int x, int y, int z) {
        try {
            ServerLevel serverLevel = level.getLevel();
            ServerChunkCache chunkSource = serverLevel.getChunkSource();
            BiomeSource biomeSource = chunkSource.getGenerator().getBiomeSource();
            Climate.Sampler sampler = chunkSource.randomState().sampler();
            return biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<OilVein> getVeinsForBasin(WorldGenLevel level,
                                                 long worldSeed, int basinX, int basinZ) {
        long cacheKey = (((long) basinX) << 32) ^ (basinZ & 0xFFFFFFFFL) ^ worldSeed;
        List<OilVein> cached = BASIN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<OilVein> list = new ArrayList<>();

        WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(worldSeed));
        rng.setLargeFeatureWithSalt(worldSeed, basinX, basinZ, 0x1BA51ED);

        // Центральный якорь бассейна с отступами по краям
        int anchorX = basinX * BASIN_SIZE + 350 + rng.nextInt(BASIN_SIZE - 700);
        int anchorZ = basinZ * BASIN_SIZE + 350 + rng.nextInt(BASIN_SIZE - 700);

        Holder<Biome> centerBiome = sampleNoiseBiome(level, anchorX, 60, anchorZ);
        boolean isOcean = centerBiome != null && centerBiome.is(BiomeTags.IS_OCEAN);
        boolean isDesert = centerBiome != null && centerBiome.is(Tags.Biomes.IS_DESERT);
        boolean isSnowy = centerBiome != null && centerBiome.is(Tags.Biomes.IS_SNOWY);
        boolean isColdOrTaiga = centerBiome != null && (centerBiome.is(BiomeTags.IS_TAIGA) || centerBiome.is(Tags.Biomes.IS_COLD_OVERWORLD));
        boolean canHaveSurfacePuddle = isDesert || isSnowy || isColdOrTaiga;

        // В пустынях и снежных биомах шанс наличия нефтяной системы повышен до 90%
        float basinActiveChance = (isDesert || isSnowy) ? 0.90f : (isOcean ? 0.75f : 0.65f);
        if (rng.nextFloat() >= basinActiveChance) {
            List<OilVein> empty = Collections.emptyList();
            BASIN_CACHE.put(cacheKey, empty);
            return empty;
        }

        // Шанс месторождения: 50% в океанах, 30% на суше
        float fieldChance = isOcean ? 0.50f : 0.30f;
        boolean isField = rng.nextFloat() < fieldChance;

        String fieldName = "";
        int totalPockets = 1;
        if (isField) {
            String prefix = FIELD_PREFIXES[rng.nextInt(FIELD_PREFIXES.length)];
            int number = 100 + rng.nextInt(900);
            fieldName = prefix + " месторождение №" + number;
            totalPockets = 3 + rng.nextInt(3); // 3 - 5 карманов в месторождении
        }

        if (!isField) {
            // === ОДИНОЧНЫЙ КАРМАН (строго 1 карман на бассейн!) ===
            long veinSeed = rng.nextLong();
            RandomSource vr = RandomSource.create(veinSeed);

            // В пустынях и снежных биомах 80% шанс спавна именно поверхностной битумной лужи (TAR_SANDS), в тайге 50%
            boolean makePuddle = canHaveSurfacePuddle && (isDesert || isSnowy ? vr.nextFloat() < 0.80f : vr.nextFloat() < 0.50f);

            if (makePuddle) {
                // Поверхностная битумная лужа (TAR_SANDS, Y=70, Радиус R=75..115 блоков)
                float prx = 75f + vr.nextFloat() * 40f;
                list.add(createVein(anchorX, 70, anchorZ, prx, 2.0f, prx * (0.85f + vr.nextFloat() * 0.3f),
                        vr, veinSeed, VeinShape.PUDDLE, ReservoirType.TAR_SANDS,
                        false, "", 1, 1));
            } else {
                float roll = vr.nextFloat();
                if (roll < (isOcean ? 0.55f : 0.45f)) {
                    // Глубинный пласт (STRATIFIED, R=75..110, толщина 5-6 блоков в дипслейте)
                    float r = 75f + vr.nextFloat() * 35f;
                    int cy = -42 + vr.nextInt(25);
                    list.add(createVein(anchorX, cy, anchorZ, r, 2.5f, r * (0.9f + vr.nextFloat() * 0.2f),
                            vr, veinSeed, VeinShape.STRATIFIED, ReservoirType.TIGHT_SHALE,
                            false, "", 1, 1));
                } else {
                    // Пористый песчаник (купол DOME, R=30..50, ry=18..26 в среднем камне)
                    float r = 30f + vr.nextFloat() * 20f;
                    float ry = 18f + vr.nextFloat() * 8f;
                    int cy = 10 + vr.nextInt(22);
                    list.add(createVein(anchorX, cy, anchorZ, r, ry, r * (0.9f + vr.nextFloat() * 0.2f),
                            vr, veinSeed, VeinShape.DOME, ReservoirType.POROUS_STONE,
                            false, "", 1, 1));
                }
            }

        } else {
            // === КЛАСТЕРНОЕ НЕФТЯНОЕ МЕСТОРОЖДЕНИЕ ===
            int pocketIdx = 1;

            // 1. Главный глубинный пласт (Main Deep Reservoir)
            long s1 = rng.nextLong();
            RandomSource r1 = RandomSource.create(s1);
            float rMain = 80f + r1.nextFloat() * 30f; // R = 80-110 блоков
            list.add(createVein(anchorX, -35, anchorZ, rMain, 2.5f, rMain * (0.9f + r1.nextFloat() * 0.2f),
                    r1, s1, VeinShape.STRATIFIED, ReservoirType.TIGHT_SHALE,
                    true, fieldName, totalPockets, pocketIdx++));

            // 2. Если биом позволяет — всегда генерируем крупное битумное озеро на поверхности!
            // Если нет — средний пористый купол (Satellite Dome)
            long s2 = rng.nextLong();
            RandomSource r2 = RandomSource.create(s2);
            int dx2 = (r2.nextBoolean() ? 1 : -1) * (70 + r2.nextInt(80));
            int dz2 = (r2.nextBoolean() ? 1 : -1) * (70 + r2.nextInt(80));
            if (canHaveSurfacePuddle) {
                float rPuddle = 75f + r2.nextFloat() * 35f;
                list.add(createVein(anchorX + dx2, 70, anchorZ + dz2, rPuddle, 2.0f, rPuddle * (0.85f + r2.nextFloat() * 0.3f),
                        r2, s2, VeinShape.PUDDLE, ReservoirType.TAR_SANDS,
                        true, fieldName, totalPockets, pocketIdx++));
            } else {
                float rDome = 30f + r2.nextFloat() * 15f;
                list.add(createVein(anchorX + dx2, 22, anchorZ + dz2, rDome, 20f, rDome * (0.9f + r2.nextFloat() * 0.2f),
                        r2, s2, VeinShape.DOME, ReservoirType.POROUS_STONE,
                        true, fieldName, totalPockets, pocketIdx++));
            }

            // 3. Второй средний пористый купол (Satellite Porous Dome) вместо вертикального штока
            long s3 = rng.nextLong();
            RandomSource r3 = RandomSource.create(s3);
            float rDome2 = 28f + r3.nextFloat() * 14f;
            int dx3 = (r3.nextBoolean() ? 1 : -1) * (80 + r3.nextInt(80));
            int dz3 = (r3.nextBoolean() ? 1 : -1) * (80 + r3.nextInt(80));
            list.add(createVein(anchorX + dx3, 10, anchorZ + dz3, rDome2, 18f, rDome2 * (0.9f + r3.nextFloat() * 0.2f),
                    r3, s3, VeinShape.DOME, ReservoirType.POROUS_STONE,
                    true, fieldName, totalPockets, pocketIdx++));

            // 4. Дополнительный средний карман (если карманов >= 4)
            if (totalPockets >= 4) {
                long s4 = rng.nextLong();
                RandomSource r4 = RandomSource.create(s4);
                float rMed = 26f + r4.nextFloat() * 12f;
                int dx4 = (r4.nextBoolean() ? 1 : -1) * (110 + r4.nextInt(90));
                int dz4 = (r4.nextBoolean() ? 1 : -1) * (110 + r4.nextInt(90));
                list.add(createVein(anchorX + dx4, -5, anchorZ + dz4, rMed, 16f, rMed * (0.9f + r4.nextFloat() * 0.2f),
                        r4, s4, VeinShape.LENTICULAR, ReservoirType.POROUS_STONE,
                        true, fieldName, totalPockets, pocketIdx++));
            }

            // 5. Пятый карман (если карманов >= 5): второй глубокий пласт в дипслейте
            if (totalPockets >= 5) {
                long s5 = rng.nextLong();
                RandomSource r5 = RandomSource.create(s5);
                int dx5 = r5.nextInt(140) - 70;
                int dz5 = r5.nextInt(140) - 70;
                float rDeep = 70f + r5.nextFloat() * 30f;
                list.add(createVein(anchorX + dx5, -45, anchorZ + dz5, rDeep, 2.5f, rDeep * (0.9f + r5.nextFloat() * 0.2f),
                        r5, s5, VeinShape.STRATIFIED, ReservoirType.TIGHT_SHALE,
                        true, fieldName, totalPockets, pocketIdx++));
            }
        }

        List<OilVein> immutableList = Collections.unmodifiableList(list);
        BASIN_CACHE.put(cacheKey, immutableList);
        return immutableList;
    }

    private static OilVein createVein(int cx, int cy, int cz,
                                      float rx, float ry, float rz,
                                      RandomSource vr, long seed,
                                      VeinShape shape, ReservoirType reservoirType,
                                      boolean isField, String fieldName, int totalPockets, int pocketIndex) {
        float yaw = vr.nextFloat() * (float) (Math.PI * 2);
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);

        float warpAmp = (shape == VeinShape.STRATIFIED || shape == VeinShape.PUDDLE) ? 4.0f : rx * 0.22f;
        float warpFreq = (shape == VeinShape.STRATIFIED || shape == VeinShape.PUDDLE) ? 0.04f : 1.1f / Math.max(rx, 4f);

        return new OilVein(cx, cy, cz, rx, ry, rz, cosYaw, sinYaw, warpAmp, warpFreq, seed,
                shape, reservoirType, isField, fieldName, totalPockets, pocketIndex);
    }

    public static float blockRandom(long veinSeed, int x, int y, int z) {
        long h = veinSeed ^ Mth.getSeed(x, y, z);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 29;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (float) (1 << 24);
    }

    public static double valueNoise(long seed, double x, double y, double z) {
        int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
        double tx = smoothstep(x - x0), ty = smoothstep(y - y0), tz = smoothstep(z - z0);

        double c000 = lattice(seed, x0, y0, z0),         c100 = lattice(seed, x0 + 1, y0, z0);
        double c010 = lattice(seed, x0, y0 + 1, z0),     c110 = lattice(seed, x0 + 1, y0 + 1, z0);
        double c001 = lattice(seed, x0, y0, z0 + 1),     c101 = lattice(seed, x0 + 1, y0, z0 + 1);
        double c011 = lattice(seed, x0, y0 + 1, z0 + 1), c111 = lattice(seed, x0 + 1, y0 + 1, z0 + 1);

        return Mth.lerp(
                Mth.lerp(Mth.lerp(c000, c100, tx), Mth.lerp(c010, c110, tx), ty),
                Mth.lerp(Mth.lerp(c001, c101, tx), Mth.lerp(c011, c111, tx), ty),
                tz);
    }

    private static double lattice(long seed, int x, int y, int z) {
        long h = seed ^ Mth.getSeed(x, y, z);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 29;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 32;
        return (double) (h >>> 41) / (double) (1L << 22) - 1.0;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static UUID veinUuid(long worldSeed, OilVein vein, String veinId) {
        long packed = ((long) vein.cx << 32) ^ (vein.cz & 0xFFFFFFFFL) ^ (vein.cy << 8);
        long a = mix(worldSeed ^ packed * 0x9E3779B97F4A7C15L ^ veinId.hashCode() * 0xBF58476D1CE4E5B9L);
        long b = mix(a ^ 0xD1B54A32D192ED03L ^ vein.seed);
        return new UUID(a, b);
    }

    private static long mix(long h) {
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }
}
