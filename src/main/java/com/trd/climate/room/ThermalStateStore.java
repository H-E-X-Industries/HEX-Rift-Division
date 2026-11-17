package com.trd.climate.room;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Персистентное хранилище живого состояния пассивных источников тепла.
 * <p>
 * Запись создаётся только тогда, когда источник впервые оказался в герметичном
 * помещении. До этого блок живёт по ванильным правилам и в хранилище отсутствует —
 * это значит «нефизичный, запас 100%, не тратится» без лишних расчётов.
 * <p>
 * Состояние — всего два числа:
 * <ul>
 *   <li>{@code reserve} (0..1) — запас эффективности, он же коэффициент, которым
 *       умножаются выход тепла, радиус, мощность и потолок комнаты;</li>
 *   <li>{@code physical} — находится ли блок сейчас в герметичной комнате
 *       (или в её оболочке). Вынесенный блок перестаёт тратить запас, но сохраняет его.</li>
 * </ul>
 * Температура источника не хранится отдельно: у нагревателя она выводится из запаса
 * через {@link HeatSourceDef#temperatureAt(float)}, поэтому «запас 50%» и «остыл до 950 °C»
 * не могут разойтись.
 */
public final class ThermalStateStore extends SavedData {

    public static final String NAME = "trd_thermal_states";
    /** Предел записей, чтобы база не разрослась (одна запись = один блок в комнате). */
    private static final int MAX_ENTRIES = 100_000;

    /** Одна запись состояния блока. */
    public record ThermalState(float reserve, boolean physical) {
        public ThermalState withReserve(float value) {
            return new ThermalState(Mth.clamp(value, 0.0f, 1.0f), physical);
        }

        public ThermalState withPhysical(boolean value) {
            return new ThermalState(reserve, value);
        }
    }

    private final Long2ObjectOpenHashMap<ThermalState> states = new Long2ObjectOpenHashMap<>();

    public static final SavedData.Factory<ThermalStateStore> FACTORY =
            new SavedData.Factory<>(ThermalStateStore::new, ThermalStateStore::load);

    public static ThermalStateStore get(ServerLevel level) {
        try {
            return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        } catch (Exception ignored) {
            return new ThermalStateStore();
        }
    }

    private static ThermalStateStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ThermalStateStore store = new ThermalStateStore();
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry)) continue;
            long pos = entry.getLong("p");
            float reserve = Mth.clamp(entry.getFloat("r"), 0.0f, 1.0f);
            boolean physical = entry.getBoolean("f");
            if (store.states.size() < MAX_ENTRIES) {
                store.states.put(pos, new ThermalState(reserve, physical));
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : states.long2ObjectEntrySet()) {
            CompoundTag record = new CompoundTag();
            record.putLong("p", entry.getLongKey());
            record.putFloat("r", entry.getValue().reserve());
            record.putBoolean("f", entry.getValue().physical());
            list.add(record);
        }
        tag.put("states", list);
        return tag;
    }

    /**
     * Активировать блок, если он — пассивный источник. Вызывается сканером контроллера
     * для внутреннего объёма и оболочки комнаты. Блоки в обычном мире не активируются,
     * поэтому их запас остаётся незатраченным (чистая ваниль).
     */
    public static void activateIfPassive(ServerLevel level, BlockPos pos) {
        HeatSourceDef def = HeatSourceRegistry.resolveState(level.getBlockState(pos));
        if (def != null && def.isPassive()) {
            get(level).activate(pos);
        }
    }

    /** Состояние блока, если источник когда-либо побывал в комнате. */
    public ThermalState get(BlockPos pos) {
        return states.get(pos.asLong());
    }

    /** Записать состояние (используется при переносе блока с предметом). */
    public void set(BlockPos pos, ThermalState state) {
        if (states.size() >= MAX_ENTRIES && !states.containsKey(pos.asLong())) {
            return;
        }
        states.put(pos.asLong(), state);
        setDirty();
    }

    /** Убрать запись (блок сломан или превратился в другой). */
    public void remove(BlockPos pos) {
        if (states.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    /** Снимок всех записей для команд диагностики. */
    public java.util.List<Entry> entries() {
        java.util.List<Entry> result = new java.util.ArrayList<>(states.size());
        for (var entry : states.long2ObjectEntrySet()) {
            result.add(new Entry(BlockPos.of(entry.getLongKey()), entry.getValue()));
        }
        return result;
    }

    public record Entry(BlockPos pos, ThermalState state) {}

    public int size() {
        return states.size();
    }

    /**
     * Пометить блок физичным (используется командами и будущими обработчиками комнат).
     * Уже накопленный запас сохраняется.
     */
    public void activate(BlockPos pos) {
        long key = pos.asLong();
        ThermalState current = states.get(key);
        ThermalState next = new ThermalState(current == null ? 1.0f : current.reserve(), true);
        if (next.equals(current)) return;
        if (current == null && states.size() >= MAX_ENTRIES) return;
        states.put(key, next);
        setDirty();
    }

    /** Полный шаг движка: раз в секунду пересматривает все физичные/source записи. */
    public void tick(ServerLevel level) {
        if (states.isEmpty()) return;

        boolean dirty = false;
        long[] keys = states.keySet().toLongArray();
        for (long key : keys) {
            BlockPos pos = BlockPos.of(key);
            BlockState blockState = level.getBlockState(pos);
            HeatSourceDef def = HeatSourceRegistry.resolveState(blockState);

            // Блок сломан/заменён или больше не является пассивным источником.
            if (def == null || !def.isPassive()) {
                states.remove(key);
                dirty = true;
                continue;
            }

            ThermalState state = states.get(key);
            SealedRoom room = findRoom(level, pos);

            if (room == null) {
                // Вынесен из комнаты (или комната разгерметизирована): запас замораживается.
                if (state.physical()) {
                    states.put(key, state.withPhysical(false));
                    dirty = true;
                }
                continue;
            }

            if (!state.physical()) {
                state = state.withPhysical(true);
                states.put(key, state);
                dirty = true;
            }

            float newReserve = updateReserve(def, state.reserve(), room.getRoomTemperature());
            if (Math.abs(newReserve - state.reserve()) > 1.0e-5f) {
                state = state.withReserve(newReserve);
                states.put(key, state);
                dirty = true;
            }

            if (newReserve <= 0.0f && def.depletedBlock() != null) {
                Block depleted = BuiltInRegistries.BLOCK.get(def.depletedBlock());
                if (depleted != null) {
                    level.setBlock(pos, depleted.defaultBlockState(), 3);
                }
                states.remove(key);
                dirty = true;
            }
        }

        if (dirty) setDirty();
    }

    /**
     * Ищет комнату для блока: внутренний объём ИЛИ оболочка (пол/потолок/стены).
     * Оболочка важна — иначе лава, поставленная в стену, обходит герметизацию.
     */
    private static SealedRoom findRoom(ServerLevel level, BlockPos pos) {
        // Только герметичная комната: при разгерметизации источники перестают быть
        // физичными и замораживают оставшийся запас.
        SealedRoom inside = SealedRoomManager.findRoomAt(level, pos);
        if (inside != null && inside.isSealed()) {
            return inside;
        }
        for (SealedRoom candidate : SealedRoomManager.getRoomsNear(level, pos, 1.5)) {
            if (candidate.isSealed() && candidate.isBoundary(pos)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Расход / простой / восстановление запаса за один секундный шаг.
     * <ul>
     *   <li>работа &gt; 0 — комментарий на стороне источника: запас тратится;</li>
     *   <li>работа == 0 — равновесие, запас не тратится;</li>
     *   <li>среда «сильнее» источника в {@code restoreFactor} раз — запас восстанавливается.</li>
     * </ul>
     */
    private static float updateReserve(HeatSourceDef def, float reserve, float roomTemp) {
        float sourceTemp = def.temperatureAt(reserve);
        float work = def.isCooling()
                ? roomTemp - sourceTemp
                : sourceTemp - roomTemp;
        work = Mth.clamp(work / def.workSpan(), 0.0f, 1.0f);

        float next = reserve - def.drainRate() * work;

        boolean restore;
        if (def.isCooling()) {
            // Для обычного льда surfaceTemp == 0, поэтому «2 раза холоднее» не определено —
            // используется абсолютный порог restoreBelow (по умолчанию −10 °C).
            float limit = def.surfaceTemp() == 0.0f
                    ? -Math.abs(def.restoreBelow())
                    : def.surfaceTemp() / def.restoreFactor();
            restore = roomTemp <= limit;
        } else {
            restore = roomTemp >= def.restoreFactor() * sourceTemp;
        }
        if (restore) {
            next += def.restoreRate();
        }
        return Mth.clamp(next, 0.0f, 1.0f);
    }
}
