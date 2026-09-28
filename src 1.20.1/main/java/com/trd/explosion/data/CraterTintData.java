package com.trd.explosion.data;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Серверное персистентное хранилище позиций, затемнённых взрывами.
 * Каждая запись — блок → ступень затемнения (0..{@link com.trd.block.basic.CraterBasaltBlock#MAX_DARK}).
 *
 * <p>Запись перезаписывается целиком: новый кратер в зоне старого пересчитывает
 * затемнение этой позиции по своему расстоянию (суммирования намеренно нет).
 *
 * <p>Данные переживают перезаход в мир (SavedData сохраняется в файл измерения).
 * Если блок на такой позиции сломали/заменили — запись удаляется через
 * {@link com.trd.event.CraterTintEvents} и тинт на клиенте пропадает.
 */
public class CraterTintData extends SavedData {

    public static final String NAME = "trd_crater_tints";

    /** Абсолютный предел записей, чтобы база не разрослась до бесконечности. */
    public static final int MAX_ENTRIES = 400_000;

    /** Верхняя ступень затемнения, зеркалит {@code CraterBasaltBlock.MAX_DARK}. */
    private static final int DARK_MAX = 7;

    private final Long2IntOpenHashMap dark = new Long2IntOpenHashMap();

    public static CraterTintData get(ServerLevel level) {
        try {
            return level.getDataStorage().computeIfAbsent(CraterTintData::load, CraterTintData::new, NAME);
        } catch (Exception ignored) {
            return new CraterTintData();
        }
    }

    public static CraterTintData load(CompoundTag tag) {
        CraterTintData data = new CraterTintData();
        long[] pos = tag.getLongArray("pos");
        // Миры, где успели поработать промилле-варианту (суммирование): переводим обратно
        // в ступени, иначе значение 750 прочиталось бы как ступень 750 и блок почернел.
        int[] permille = tag.getIntArray("permille");
        if (permille.length == pos.length && permille.length > 0) {
            for (int i = 0; i < pos.length && data.dark.size() < MAX_ENTRIES; i++) {
                int step = permilleToStep(permille[i]);
                if (step > 0) data.dark.put(pos[i], step);
            }
            return data;
        }
        int[] dark = tag.getIntArray("dark");
        int n = Math.min(pos.length, dark.length);
        for (int i = 0; i < n && data.dark.size() < MAX_ENTRIES; i++) {
            if (dark[i] > 0) data.dark.put(pos[i], Math.min(DARK_MAX, dark[i]));
        }
        return data;
    }

    /** Промилле → ступень 0..{@code DARK_MAX} (обратно к формуле 75% на ступени 7). */
    private static int permilleToStep(int permille) {
        if (permille <= 0) return 0;
        long step = Math.round((double) permille * DARK_MAX / 750.0);
        return (int) Math.max(0, Math.min(DARK_MAX, step));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] pos = dark.keySet().toLongArray();
        int[] darkArr = new int[pos.length];
        for (int i = 0; i < pos.length; i++) darkArr[i] = dark.get(pos[i]);
        tag.putLongArray("pos", pos);
        tag.putIntArray("dark", darkArr);
        return tag;
    }

    public boolean contains(long pos) {
        return dark.containsKey(pos);
    }

    public int get(long pos) {
        return dark.containsKey(pos) ? dark.get(pos) : 0;
    }

    public void put(long pos, int value) {
        if (value <= 0) return;
        if (dark.size() >= MAX_ENTRIES && !dark.containsKey(pos)) return;
        dark.put(pos, Math.min(DARK_MAX, value));
        setDirty();
    }

    /** Батч записей: значения перезаписывают старые (новый кратер заменяет старый). */
    public void putAll(Long2IntOpenHashMap entries) {
        boolean dirty = false;
        for (Long2IntMap.Entry e : entries.long2IntEntrySet()) {
            int v = e.getIntValue();
            if (v <= 0) continue;
            if (dark.size() >= MAX_ENTRIES && !dark.containsKey(e.getLongKey())) continue;
            dark.put(e.getLongKey(), Math.min(DARK_MAX, v));
            dirty = true;
        }
        if (dirty) setDirty();
    }

    public boolean remove(long pos) {
        if (!dark.containsKey(pos)) return false;
        dark.remove(pos);
        setDirty();
        return true;
    }

    public Long2IntOpenHashMap entries() {
        return dark;
    }

    public int size() {
        return dark.size();
    }
}