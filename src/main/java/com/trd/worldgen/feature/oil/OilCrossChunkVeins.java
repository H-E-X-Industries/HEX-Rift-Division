package com.trd.worldgen.feature.oil;

import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.UUID;

public final class OilCrossChunkVeins {

    public static final int CELL_SIZE = 48;

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

        OilVein(int cx, int cy, int cz, float rx, float ry, float rz,
                float cosYaw, float sinYaw, float warpAmp, float warpFreq,
                long seed, VeinShape shape, ReservoirType reservoirType) {
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
        }

        public float horizontalReach() {
            return Math.max(rx, rz);
        }

        public boolean overlapsChunk(int minBX, int maxBX, int minBZ, int maxBZ) {
            float m = horizontalReach() + warpAmp + 1f;
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

            if (shape == VeinShape.DOME && ly < -0.2 * ry) {
                return Double.MAX_VALUE;
            }

            double effRy = ry + warpAmp;
            if (shape == VeinShape.PUDDLE) {
                // Лужи ограничены малой высотой
                if (Math.abs(ly) > ry + 1.0) return Double.MAX_VALUE;
            } else if (shape == VeinShape.STRATIFIED) {
                // Пласты строго 2-3 блока
                if (Math.abs(ly) > ry + 0.5) return Double.MAX_VALUE;
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

            if (shape == VeinShape.PUDDLE) {
                return dx * dx + dz * dz; // горизонтальный срез для лужи
            }
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record ShapeParams(
            String veinId,
            int minSize,
            int maxSize,
            int minY,
            int maxY,
            float maxStretch,
            float noiseScale,
            int rarity,
            VeinShape shape,
            ReservoirType reservoirType
    ) {}

    @FunctionalInterface
    public interface VeinVisitor {
        void visit(OilVein vein);
    }

    public static void forEachVein(net.minecraft.world.level.WorldGenLevel level,
                                   int chunkX, int chunkZ,
                                   ShapeParams p,
                                   VeinVisitor visitor) {
        long worldSeed = level.getLevel().getSeed();

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;

        float reach = p.maxSize() * Math.max(1f, p.maxStretch()) + 6f;
        int reachI = Mth.ceil(reach);

        int c0x = Math.floorDiv(minBX - reachI, CELL_SIZE);
        int c1x = Math.floorDiv(maxBX + reachI, CELL_SIZE);
        int c0z = Math.floorDiv(minBZ - reachI, CELL_SIZE);
        int c1z = Math.floorDiv(maxBZ + reachI, CELL_SIZE);

        for (int cellX = c0x; cellX <= c1x; cellX++) {
            for (int cellZ = c0z; cellZ <= c1z; cellZ++) {
                OilVein vein = tryCreateVein(worldSeed, cellX, cellZ, p);
                if (vein != null && vein.overlapsChunk(minBX, maxBX, minBZ, maxBZ)) {
                    visitor.visit(vein);
                }
            }
        }
    }

    private static OilVein tryCreateVein(long worldSeed, int cellX, int cellZ, ShapeParams p) {
        WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(worldSeed));
        rng.setLargeFeatureWithSalt(worldSeed, cellX, cellZ, p.veinId().hashCode());

        float pCell = Math.min(1f, (CELL_SIZE * (float) CELL_SIZE) / (256f * Math.max(1, p.rarity())));
        if (rng.nextFloat() >= pCell) return null;

        int range = p.maxY() - p.minY();
        if (range < 0) return null;
        int cy = range > 0 ? p.minY() + rng.nextInt(range + 1) : p.minY();
        cy = Mth.clamp(cy, -64, 320);

        long veinSeed = rng.nextLong();
        RandomSource vr = RandomSource.create(veinSeed);

        float yaw = vr.nextFloat() * (float) (Math.PI * 2);
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);

        float rx, ry, rz;
        float warpAmp, warpFreq;

        if (p.shape() == VeinShape.PUDDLE) {
            // Поверхностная лужа: сплюснутая по Y (толщина 1-3 блока), огромная по X/Z
            float baseR = Mth.lerp(vr.nextFloat(), (float) p.minSize(), (float) p.maxSize());
            rx = baseR;
            rz = baseR * Mth.lerp(vr.nextFloat(), 0.85f, 1.25f);
            ry = 1.2f + vr.nextFloat() * 0.6f; // радиус 1.2..1.8 -> толщина 2-3 блока
            warpAmp = 4.0f;
            warpFreq = 0.05f;
        } else if (p.shape() == VeinShape.STRATIFIED) {
            // Пласт в дипслейте: строго 2-3 блока толщиной, огромный размах
            float baseR = Mth.lerp(vr.nextFloat(), (float) p.minSize(), (float) p.maxSize());
            rx = baseR;
            rz = baseR * Mth.lerp(vr.nextFloat(), 0.85f, 1.25f);
            ry = 1.1f + vr.nextFloat() * 0.4f; // строго 2-3 блока
            warpAmp = 3.5f;
            warpFreq = 0.04f;
        } else {
            // Купол / линза в камне: объемные тела
            float baseR = Mth.lerp(vr.nextFloat(), (float) p.minSize(), (float) p.maxSize());
            float major = 1f + Math.max(0f, p.maxStretch() - 1f) * vr.nextFloat();
            rx = baseR * major;
            rz = baseR * Mth.lerp(vr.nextFloat(), 0.85f, 1.15f);
            ry = baseR * Mth.lerp(vr.nextFloat(), 0.55f, 0.85f);
            warpAmp = baseR * 0.22f;
            warpFreq = 1.1f / Math.max(baseR, 4f);
        }

        int ax = cellX * CELL_SIZE + rng.nextInt(CELL_SIZE);
        int az = cellZ * CELL_SIZE + rng.nextInt(CELL_SIZE);

        return new OilVein(ax, cy, az, rx, ry, rz, cosYaw, sinYaw, warpAmp, warpFreq, veinSeed, p.shape(), p.reservoirType());
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

    private static double valueNoise(long seed, double x, double y, double z) {
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
