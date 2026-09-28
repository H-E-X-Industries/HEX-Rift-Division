package com.trd.worldgen.feature;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.UUID;

/**
 * Детерминированная генерация больших жил, не ограниченных размером чанка.
 *
 * Мир разбит на ячейки CELL_SIZE x CELL_SIZE. Для каждой ячейки и каждого типа жилы
 * чистая функция от (worldSeed, cellX, cellZ, salt) решает: есть ли тут якорь жилы,
 * и если да - задаёт все её параметры (центр, радиусы, растяжение, поворот, шум).
 *
 * Каждый чанк независимо вычисляет все жилы в соседних ячейках и достраивает только
 * свою часть (блоки внутри своих границ). Соседние чанки получают идентичные параметры,
 * поэтому жила сшивается бесшовно. Все "случайные" решения на уровне блока принимаются
 * хешем от (seed жилы, координаты блока), а не последовательным RNG — поэтому плотность
 * и прочее не зависят от того, в каком порядке/какими порциями генерируются чанки.
 */
public final class CrossChunkVeins {

    /** Размер ячейки региона в блоках. */
    public static final int CELL_SIZE = 48;

    private static final int UNITS_PER_BLOCK = 810; // синхронно с VeinManager

    private CrossChunkVeins() {}

    /** Параметры одной детерминированной жилы. */
    public static final class Vein {
        private static final long WARP_SALT = 0x5BF036A5B0F6199EL;

        public final long seed;
        public final int cx, cy, cz;
        public final float rx, ry, rz;
        final float cosYaw, sinYaw;
        final float warpAmp, warpFreq;
        final long warpSeed;

        Vein(int cx, int cy, int cz, float rx, float ry, float rz,
             float cosYaw, float sinYaw, float warpAmp, float warpFreq,
             long seed) {
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
        }

        /** Горизонтальный полуохват (поворот не увеличивает его за пределы max(rx, rz)). */
        public float horizontalReach() {
            return Math.max(rx, rz);
        }

        /** Быстрый AABB-тест перекрытия с границами чанка (без учёта варпа — он добавляется к reach). */
        public boolean overlapsChunk(int minBX, int maxBX, int minBZ, int maxBZ) {
            float m = horizontalReach() + warpAmp + 1f;
            return cx + m >= minBX && cx - m <= maxBX && cz + m >= minBZ && cz - m <= maxBZ;
        }

        /**
         * Поле формы: значение <= 1 значит "внутри жилы".
         * Эллипсоид -> случайный поворот вокруг Y -> доменный варперлин -> нормировка на радиусы.
         * Дешёвый предфильтр по раздутому эллипсоиду отсекает дальние блоки без шума.
         */
        public double fieldValue(int x, int y, int z) {
            double lx = x - cx;
            double ly = y - cy;
            double lz = z - cz;

            // предфильтр: консервативный эллипсоид, раздутый на амплитуду варпа (без поворота)
            double hr = horizontalReach() + warpAmp;
            if ((lx * lx + lz * lz) / (hr * hr) + (ly * ly) / ((ry + warpAmp) * (ry + warpAmp)) > 1.0) {
                return Double.MAX_VALUE;
            }

            double ux = cosYaw * lx + sinYaw * lz;
            double uz = -sinYaw * lx + cosYaw * lz;

            double n1 = valueNoise(warpSeed, ux * warpFreq + 31.7, ly * warpFreq - 12.9, uz * warpFreq) * warpAmp;
            double n2 = valueNoise(warpSeed, ux * warpFreq - 55.3, ly * warpFreq + 7.1, uz * warpFreq + 44.8) * warpAmp;
            double n3 = valueNoise(warpSeed, ux * warpFreq + 91.2, ly * warpFreq - 63.4, uz * warpFreq + 17.6) * warpAmp;

            double dx = (ux + n1) / rx;
            double dy = (ly + n2) / ry;
            double dz = (uz + n3) / rz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** Параметры формы, общие для всех типов жил. */
    public record ShapeParams(
            String veinId,     // стабильный id (для соли и UUID)
            int minSize,
            int maxSize,
            int minY,
            int maxY,
            float maxStretch,  // максимальное растяжение главной оси (>= 1)
            float noiseScale,  // сила варпа краёв
            int rarity         // в среднем одна жила раз в N чанков
    ) {}

    @FunctionalInterface
    public interface VeinVisitor {
        void visit(Vein vein);
    }

    /**
     * Обходит все детерминированные жилы, пересекающие данный чанк.
     * Вызывается из Feature.place(); каждый чанк получает одинаковый набор жил.
     */
    public static void forEachVein(net.minecraft.world.level.WorldGenLevel level,
                                   int chunkX, int chunkZ,
                                   ShapeParams p,
                                   VeinVisitor visitor) {
        long worldSeed = level.getLevel().getSeed();

        int minBX = chunkX << 4;
        int maxBX = minBX + 15;
        int minBZ = chunkZ << 4;
        int maxBZ = minBZ + 15;

        float warpK = 0.22f + p.noiseScale();
        float reach = p.maxSize() * Math.max(1f, p.maxStretch()) * warpK + p.maxSize() + 2f;
        int reachI = Mth.ceil(reach);

        int c0x = Math.floorDiv(minBX - reachI, CELL_SIZE);
        int c1x = Math.floorDiv(maxBX + reachI, CELL_SIZE);
        int c0z = Math.floorDiv(minBZ - reachI, CELL_SIZE);
        int c1z = Math.floorDiv(maxBZ + reachI, CELL_SIZE);

        for (int cellX = c0x; cellX <= c1x; cellX++) {
            for (int cellZ = c0z; cellZ <= c1z; cellZ++) {
                Vein vein = tryCreateVein(worldSeed, cellX, cellZ, p);
                if (vein != null && vein.overlapsChunk(minBX, maxBX, minBZ, maxBZ)) {
                    visitor.visit(vein);
                }
            }
        }
    }

    /** Чистая функция: ячейка -> жила (или null). Одинаковый результат во всех чанках и потоках. */
    private static Vein tryCreateVein(long worldSeed, int cellX, int cellZ, ShapeParams p) {
        WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(worldSeed));
        rng.setLargeFeatureWithSalt(worldSeed, cellX, cellZ, p.veinId().hashCode());

        // Плотность как у RarityFilter.onAverageOnceEvery(rarity), но на ячейку:
        // ячеек в чанке ~ (48*48)/(16*16) = 9, поэтому p = 9/rarity.
        float pCell = Math.min(1f, (CELL_SIZE * (float) CELL_SIZE) / (256f * Math.max(1, p.rarity())));
        if (rng.nextFloat() >= pCell) return null;

        int range = p.maxY() - p.minY();
        if (range < 0) return null;
        int cy = range > 0 ? p.minY() + rng.nextInt(range + 1) : p.minY();
        cy = Mth.clamp(cy, -64, 320);

        float t = range > 0 ? Mth.clamp((cy - p.minY()) / (float) range, 0f, 1f) : 0.5f;
        float baseR = Mth.lerp(t, p.minSize(), p.maxSize());
        if (baseR < 1f) return null;

        long veinSeed = rng.nextLong();
        RandomSource vr = RandomSource.create(veinSeed);

        // --- естественная форма: растяжение вдоль случайной оси + поворот + сплюснутость ---
        float yaw = vr.nextFloat() * (float) (Math.PI * 2);
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);

        float major = 1f + Math.max(0f, p.maxStretch() - 1f) * vr.nextFloat();
        float minor = (float) (1.0 / Math.sqrt(major)); // сохраняем примерный объём

        float rx, ry, rz;
        if (vr.nextFloat() < 0.3f) {
            // вытянута вертикально (шток/столб)
            ry = baseR * major;
            rx = baseR * minor * Mth.lerp(vr.nextFloat(), 0.85f, 1.15f);
            rz = baseR * minor * Mth.lerp(vr.nextFloat(), 0.85f, 1.15f);
        } else {
            // вытянута горизонтально вдоль случайного направления
            rx = baseR * major;
            rz = baseR * minor * Mth.lerp(vr.nextFloat(), 0.85f, 1.15f);
            ry = baseR * minor * Mth.lerp(vr.nextFloat(), 0.5f, 1.0f); // линза/пласт
        }
        ry = Math.max(1.5f, ry);

        // --- доменный варп: рвёт правильную эллиптическую поверхность ---
        float warpAmp = baseR * (0.22f + p.noiseScale());
        float warpFreq = 1.1f / Math.max(baseR, 3f);

        int ax = cellX * CELL_SIZE + rng.nextInt(CELL_SIZE);
        int az = cellZ * CELL_SIZE + rng.nextInt(CELL_SIZE);

        return new Vein(ax, cy, az, rx, ry, rz, cosYaw, sinYaw, warpAmp, warpFreq, veinSeed);
    }

    /**
     * Псевдослучайное число [0..1) для конкретного блока конкретной жилы.
     * Чистая функция координат — решения одинаковы независимо от порядка генерации чанков.
     */
    public static float blockRandom(long veinSeed, int x, int y, int z) {
        long h = veinSeed ^ Mth.getSeed(x, y, z);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 29;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (float) (1 << 24);
    }

    /** Дешёвый 3D value-noise на хешах решётки: чистая функция координат и сида. */
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
        return (double) (h >>> 41) / (double) (1L << 22) - 1.0; // [-1..1)
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Детерминированный UUID жилы: одинаковый во всех чанках, которые её достраивают. */
    public static UUID veinUuid(long worldSeed, Vein vein, String veinId) {
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
