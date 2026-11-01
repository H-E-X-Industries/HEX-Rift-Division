package com.trd.api.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OilVeinManager extends SavedData {
    private static final String DATA_NAME = "trd_oil_vein_manager";
    public static final int MB_PER_BLOCK = 1000;

    private final Map<UUID, OilVeinMetadata> veinIndex = new ConcurrentHashMap<>();
    private final Map<UUID, OilVeinData> activeVeins = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Set<UUID>> chunkToVeins = new ConcurrentHashMap<>();

    public static OilVeinManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        OilVeinManager::new,
                        OilVeinManager::load,
                        null
                ),
                DATA_NAME
        );
    }

    public void registerVeinPortion(UUID id, Set<BlockPos> portionBlocks, OilComposition composition, int yLevel) {
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

        int[] addedBox = new int[1];
        activeVeins.compute(id, (k, data) -> {
            OilVeinData d = data != null ? data : new OilVeinData(id, composition, 0, 0, yLevel);
            int added = 0;
            for (BlockPos p : portionBlocks) {
                if (d.blocks.add(p)) added++;
            }
            d.grow(added);
            addedBox[0] = added;
            return d;
        });

        int added = addedBox[0];

        veinIndex.compute(id, (k, meta) -> {
            if (meta == null) {
                OilVeinMetadata m = new OilVeinMetadata(id, composition, (long) added * MB_PER_BLOCK, pMin, pMax, yLevel, added);
                m.remainingUnits = (long) added * MB_PER_BLOCK;
                indexChunkRange(m);
                return m;
            }
            if (pMin.x < meta.minChunk.x || pMin.z < meta.minChunk.z || pMax.x > meta.maxChunk.x || pMax.z > meta.maxChunk.z) {
                meta.minChunk = new ChunkPos(Math.min(pMin.x, meta.minChunk.x), Math.min(pMin.z, meta.minChunk.z));
                meta.maxChunk = new ChunkPos(Math.max(pMax.x, meta.maxChunk.x), Math.max(pMax.z, meta.maxChunk.z));
                indexChunkRange(meta);
            }
            meta.blockCount += added;
            meta.maxUnits += (long) added * MB_PER_BLOCK;
            meta.remainingUnits += (long) added * MB_PER_BLOCK;
            return meta;
        });

        setDirty();
    }

    private void indexChunkRange(OilVeinMetadata meta) {
        for (int cx = meta.minChunk.x; cx <= meta.maxChunk.x; cx++) {
            for (int cz = meta.minChunk.z; cz <= meta.maxChunk.z; cz++) {
                chunkToVeins.computeIfAbsent(new ChunkPos(cx, cz), k -> ConcurrentHashMap.newKeySet()).add(meta.id);
            }
        }
    }

    public OilVeinData getVein(UUID id) {
        if (id == null) return null;
        OilVeinData live = activeVeins.get(id);
        if (live != null) return live;

        OilVeinMetadata meta = veinIndex.get(id);
        if (meta == null) return null;

        return activeVeins.computeIfAbsent(id, k -> new OilVeinData(
                meta.id, meta.composition, meta.remainingUnits, meta.maxUnits, meta.yLevel
        ));
    }

    public boolean consumeVeinUnits(UUID id, int amount) {
        if (id == null || amount <= 0) return false;

        OilVeinData live = activeVeins.get(id);
        if (live != null) {
            live.remainingUnits = Math.max(0, live.remainingUnits - amount);
        }

        OilVeinMetadata meta = veinIndex.get(id);
        if (meta != null) {
            meta.remainingUnits = Math.max(0, meta.remainingUnits - amount);
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (OilVeinMetadata meta : veinIndex.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", meta.id);
            entry.putLong("Remaining", meta.remainingUnits);
            entry.putLong("Max", meta.maxUnits);
            entry.putInt("BlockCount", meta.blockCount);
            entry.putInt("Y", meta.yLevel);
            entry.putInt("MinChunkX", meta.minChunk.x);
            entry.putInt("MinChunkZ", meta.minChunk.z);
            entry.putInt("MaxChunkX", meta.maxChunk.x);
            entry.putInt("MaxChunkZ", meta.maxChunk.z);
            entry.put("Composition", meta.composition.save());
            list.add(entry);
        }
        tag.put("OilVeins", list);
        return tag;
    }

    public static OilVeinManager load(CompoundTag tag, HolderLookup.Provider provider) {
        OilVeinManager mgr = new OilVeinManager();
        if (tag.contains("OilVeins", CompoundTag.TAG_LIST)) {
            ListTag list = tag.getList("OilVeins", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                UUID id = entry.getUUID("Id");
                long remaining = entry.getLong("Remaining");
                long max = entry.getLong("Max");
                int count = entry.getInt("BlockCount");
                int y = entry.getInt("Y");
                ChunkPos pMin = new ChunkPos(entry.getInt("MinChunkX"), entry.getInt("MinChunkZ"));
                ChunkPos pMax = new ChunkPos(entry.getInt("MaxChunkX"), entry.getInt("MaxChunkZ"));
                OilComposition comp = OilComposition.load(entry.getCompound("Composition"));

                OilVeinMetadata meta = new OilVeinMetadata(id, comp, max, pMin, pMax, y, count);
                meta.remainingUnits = remaining;
                mgr.veinIndex.put(id, meta);
                mgr.indexChunkRange(meta);
            }
        }
        return mgr;
    }

    public static final class OilVeinMetadata {
        public final UUID id;
        public final OilComposition composition;
        public long maxUnits;
        public long remainingUnits;
        public ChunkPos minChunk;
        public ChunkPos maxChunk;
        public final int yLevel;
        public int blockCount;

        OilVeinMetadata(UUID id, OilComposition composition, long maxUnits,
                        ChunkPos minChunk, ChunkPos maxChunk, int yLevel, int blockCount) {
            this.id = id;
            this.composition = composition;
            this.maxUnits = maxUnits;
            this.remainingUnits = maxUnits;
            this.minChunk = minChunk;
            this.maxChunk = maxChunk;
            this.yLevel = yLevel;
            this.blockCount = blockCount;
        }
    }

    public static final class OilVeinData {
        public final UUID id;
        public final OilComposition composition;
        public long remainingUnits;
        public long maxUnits;
        public final int yLevel;
        public final Set<BlockPos> blocks = ConcurrentHashMap.newKeySet();

        OilVeinData(UUID id, OilComposition composition, long remainingUnits, long maxUnits, int yLevel) {
            this.id = id;
            this.composition = composition;
            this.remainingUnits = remainingUnits;
            this.maxUnits = maxUnits;
            this.yLevel = yLevel;
        }

        void grow(int addedBlocks) {
            long added = (long) addedBlocks * MB_PER_BLOCK;
            this.maxUnits += added;
            this.remainingUnits += added;
        }
    }
}
