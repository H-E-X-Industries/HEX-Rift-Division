package com.trd.api.vein;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VeinManager extends SavedData {
    private static final String DATA_NAME = "trd_vein_manager";
    private static final int UNITS_PER_BLOCK = 810;

    // Потокобезопасность обязательна: фичи генерации вызываются из воркер-потоков
    // (Worker-Main-*), а save()/consume идут на потоке сервера. Все изменения записей
    // идут через ConcurrentHashMap.compute — атомарно по ключу.
    private final Map<UUID, VeinMetadata> veinIndex = new ConcurrentHashMap<>();
    private final Map<UUID, VeinData> activeVeins = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Set<UUID>> chunkToVeins = new ConcurrentHashMap<>();

    public static VeinManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                VeinManager::load,
                VeinManager::new,
                DATA_NAME
        );
    }

    /**
     * Регистрирует порцию большой жилы, достроенную текущим чанком.
     * Жила определяется детерминированным UUID, поэтому соседний чанк получит тот же id
     * и его порция атомарно сольётся с уже существующей записью.
     */
    public void registerVeinPortion(UUID id, Set<BlockPos> portionBlocks, VeinComposition composition, int yLevel) {
        if (portionBlocks.isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : portionBlocks) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        ChunkPos pMin = new ChunkPos(minX >> 4, minZ >> 4);
        ChunkPos pMax = new ChunkPos(maxX >> 4, maxZ >> 4);

        // 1. Сливаем блоки в активную жилу и считаем реально добавленные блоки
        int[] addedBox = new int[1];
        activeVeins.compute(id, (k, data) -> {
            VeinData d = data != null ? data : new VeinData(id, composition, 0, 0, yLevel);
            int added = 0;
            for (BlockPos p : portionBlocks) {
                if (d.blocks.add(p)) added++;
            }
            d.grow(added);
            addedBox[0] = added;
            return d;
        });

        int added = addedBox[0];

        // 2. Обновляем индекс (метаданные для сейва)
        veinIndex.compute(id, (k, meta) -> {
            if (meta == null) {
                ChunkPos mn = pMin, mx = pMax;
                VeinMetadata m = new VeinMetadata(id, composition, added * UNITS_PER_BLOCK, mn, mx, yLevel, added);
                m.remainingUnits = added * UNITS_PER_BLOCK;
                indexChunkRange(m);
                return m;
            }
            if (pMin.x < meta.minChunk.x || pMin.z < meta.minChunk.z || pMax.x > meta.maxChunk.x || pMax.z > meta.maxChunk.z) {
                meta.minChunk = new ChunkPos(Math.min(pMin.x, meta.minChunk.x), Math.min(pMin.z, meta.minChunk.z));
                meta.maxChunk = new ChunkPos(Math.max(pMax.x, meta.maxChunk.x), Math.max(pMax.z, meta.maxChunk.z));
                indexChunkRange(meta);
            }
            meta.blockCount += added;
            meta.maxUnits += added * UNITS_PER_BLOCK;
            meta.remainingUnits += added * UNITS_PER_BLOCK;
            return meta;
        });

        setDirty();
    }

    /** Заполняет обратный индекс чанк -> жилы для метаданных. */
    private void indexChunkRange(VeinMetadata meta) {
        for (int cx = meta.minChunk.x; cx <= meta.maxChunk.x; cx++) {
            for (int cz = meta.minChunk.z; cz <= meta.maxChunk.z; cz++) {
                chunkToVeins.computeIfAbsent(new ChunkPos(cx, cz), k -> ConcurrentHashMap.newKeySet()).add(meta.id);
            }
        }
    }

    public VeinData getVein(UUID id) {
        VeinData cached = activeVeins.get(id);
        if (cached != null) return cached;
        return loadVeinFromStorage(id);
    }

    public void onChunkLoad(ChunkPos pos) {
        Set<UUID> veinsInChunk = chunkToVeins.get(pos);
        if (veinsInChunk == null) return;

        for (UUID veinId : veinsInChunk) {
            if (!activeVeins.containsKey(veinId)) {
                VeinData data = loadVeinFromStorage(veinId);
                if (data != null) activeVeins.put(veinId, data);
            }
        }
    }

    public void onChunkUnload(ChunkPos pos) {
        Set<UUID> veinsInChunk = chunkToVeins.get(pos);
        if (veinsInChunk == null) return;

        for (UUID veinId : veinsInChunk) {
            VeinMetadata meta = veinIndex.get(veinId);
            if (meta != null && areAllChunksUnloaded(meta)) {
                activeVeins.remove(veinId);
            }
        }
    }

    public void consumeVeinUnits(UUID veinId, int amount) {
        activeVeins.computeIfPresent(veinId, (k, data) -> {
            data.consumeUnits(amount);
            return data;
        });
        veinIndex.computeIfPresent(veinId, (k, meta) -> {
            meta.remainingUnits = Math.max(0, meta.remainingUnits - amount);
            return meta;
        });
        setDirty();
    }

    private boolean areAllChunksUnloaded(VeinMetadata meta) {
        // Заглушка — можно доработать позже, если нужна агрессивная выгрузка из памяти.
        return true;
    }

    private VeinData loadVeinFromStorage(UUID id) {
        VeinMetadata meta = veinIndex.get(id);
        if (meta == null) return null;
        return new VeinData(id, meta.composition, meta.remainingUnits, meta.blockCount, meta.yLevel);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag indexList = new ListTag();
        veinIndex.values().forEach(meta -> indexList.add(meta.serialize()));
        tag.put("VeinIndex", indexList);
        return tag;
    }

    public static VeinManager load(CompoundTag tag) {
        VeinManager manager = new VeinManager();
        ListTag indexList = tag.getList("VeinIndex", 10);
        indexList.forEach(nbt -> {
            VeinMetadata meta = VeinMetadata.deserialize((CompoundTag) nbt);
            manager.veinIndex.put(meta.id, meta);
            manager.indexChunkRange(meta);
        });
        return manager;
    }

    public static class VeinMetadata {
        public final UUID id;
        public final VeinComposition composition;
        public int maxUnits;
        public ChunkPos minChunk;
        public ChunkPos maxChunk;
        public final int yLevel;
        public int blockCount;
        public volatile int remainingUnits;

        public VeinMetadata(UUID id, VeinComposition composition, int maxUnits, ChunkPos minChunk, ChunkPos maxChunk, int yLevel, int blockCount) {
            this.id = id;
            this.composition = composition;
            this.maxUnits = maxUnits;
            this.remainingUnits = maxUnits;
            this.minChunk = minChunk;
            this.maxChunk = maxChunk;
            this.yLevel = yLevel;
            this.blockCount = blockCount;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.put("Composition", composition.serialize());
            tag.putInt("MaxUnits", maxUnits);
            tag.putInt("Remaining", remainingUnits);
            tag.putInt("MinCX", minChunk.x);
            tag.putInt("MinCZ", minChunk.z);
            tag.putInt("MaxCX", maxChunk.x);
            tag.putInt("MaxCZ", maxChunk.z);
            tag.putInt("YLevel", yLevel);
            tag.putInt("BlockCount", blockCount);
            return tag;
        }

        public static VeinMetadata deserialize(CompoundTag tag) {
            UUID id = tag.getUUID("Id");
            VeinComposition composition = VeinComposition.deserialize(tag.getCompound("Composition"));
            int maxUnits = tag.getInt("MaxUnits");
            ChunkPos min = new ChunkPos(tag.getInt("MinCX"), tag.getInt("MinCZ"));
            ChunkPos maxChunk = new ChunkPos(tag.getInt("MaxCX"), tag.getInt("MaxCZ"));
            int yLevel = tag.getInt("YLevel");
            int blockCount = tag.contains("BlockCount") ? tag.getInt("BlockCount") : 1;
            VeinMetadata meta = new VeinMetadata(id, composition, maxUnits, min, maxChunk, yLevel, blockCount);
            meta.remainingUnits = tag.getInt("Remaining");
            return meta;
        }
    }

    public static class VeinData {
        public final UUID id;
        public final VeinComposition composition;
        // Потокобезопасное множество: порции добавляются из воркер-потоков генерации
        public final Set<BlockPos> blocks;
        private volatile int remainingUnits;
        private int blockCount;
        private final int yLevel;

        public VeinData(UUID id, VeinComposition composition, Set<BlockPos> blocks, int yLevel) {
            this.id = id;
            this.composition = composition;
            this.blocks = ConcurrentHashMap.newKeySet();
            this.blocks.addAll(blocks);
            this.blockCount = this.blocks.size();
            this.remainingUnits = this.blockCount * UNITS_PER_BLOCK;
            this.yLevel = yLevel;
        }

        // Конструктор для загрузки / наращивания (без списка блоков)
        public VeinData(UUID id, VeinComposition composition, int remainingUnits, int blockCount, int yLevel) {
            this.id = id;
            this.composition = composition;
            this.blocks = ConcurrentHashMap.newKeySet();
            this.blockCount = blockCount;
            this.remainingUnits = remainingUnits;
            this.yLevel = yLevel;
        }

        /** Атомарно наращивает жилу новой порцией блоков (вызывается внутри compute). */
        void grow(int addedBlocks) {
            if (addedBlocks <= 0) return;
            this.blockCount += addedBlocks;
            this.remainingUnits += addedBlocks * UNITS_PER_BLOCK;
        }

        public void consumeUnits(int amount) {
            this.remainingUnits = Math.max(0, remainingUnits - amount);
        }

        public int getRemainingUnits() { return remainingUnits; }
        public boolean isDepleted() { return remainingUnits <= 0; }
        public float getDepletionRatio() {
            if (blockCount == 0) return 0.0f;
            return 1.0f - ((float)remainingUnits / (blockCount * 810f));
        }
        public VeinComposition getComposition() { return composition; }

        public String getTypeName() {
            return getDepthTypeName(yLevel);
        }

        private static String getDepthTypeName(int y) {
            if (y >= 40) return "surface";
            if (y >= -20) return "medium";
            return "deep";
        }
    }
}
