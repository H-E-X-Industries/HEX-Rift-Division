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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    /** Скорость спада запаса воды при заморозке ниже 0°C в секунду. */
    private static final float WATER_FREEZE_DRAIN = 0.012f;
    /** Скорость спада запаса воды при кипении выше 100°C в секунду. */
    private static final float WATER_STEAM_DRAIN = 0.03f;
    /** Шанс выпустить пузырьки пара за ролл на кипящей воде. */
    private static final float STEAM_BUBBLE_CHANCE = 0.25f;

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

    /**
     * Единая точка чтения живого состояния с обеих сторон: сервер читает SavedData,
     * клиент — кэш, присланный пакетом. Это убирает рассинхрон дебаг-оверлея
     * и показаний термометра.
     */
    public static ThermalState stateAt(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return ClientThermalStateCache.get(pos);
        }
        return get((ServerLevel) level).get(pos);
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

    /** Снимок записей рядом с точкой — для сетевой синхронизации. */
    public java.util.List<Entry> entriesNear(BlockPos center, double radius) {
        java.util.List<Entry> result = new java.util.ArrayList<>();
        double rSq = radius * radius;
        for (var entry : states.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (pos.distSqr(center) <= rSq) {
                result.add(new Entry(pos, entry.getValue()));
            }
        }
        return result;
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
                    // Синхронное застывание: текучая лава вокруг источника превращается
                    // в тот же depletedBlock разом, а не отдельно по истечении своего запаса.
                    if (def.freezeRadius() > 0.0f) {
                        freezeSurroundingFluid(level, pos, def.freezeRadius(), depleted);
                    }
                }
                states.remove(key);
                dirty = true;
            }
        }

        if (dirty) setDirty();
    }

    /**
     * Синхронно замораживает текучую часть источника (лава → базальт).
     * Заменяет только текучую лаву в радиусе, не трогая остальные блоки.
     */
    private static void freezeSurroundingFluid(ServerLevel level, BlockPos center,
                                               float radius, Block depleted) {
        net.minecraft.world.level.block.state.BlockState depletedState = depleted.defaultBlockState();
        int r = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (cursor.equals(center)) {
                        continue;
                    }
                    var fluid = level.getFluidState(cursor);
                    // Только текучая лава (не источник — он уже заменён)
                    if (fluid.is(net.minecraft.tags.FluidTags.LAVA) && !fluid.isSource()) {
                        level.setBlock(cursor, depletedState, 3);
                    }
                }
            }
        }
    }

    /**
     * Тиковый шаг вероятностного таяния льда (вызывается каждый тик).
     * <p>
     * Чем выше температура комнаты, тем выше шанс растаять за ролл и тем короче
     * кулдаун между роллами. При успешном ролле запас уменьшается на {@code melt.loss()}.
     * Вне герметичной комнаты лёд не тает (нефизичный источник).
     */
    public void tickMelting(ServerLevel level) {
        if (states.isEmpty()) return;

        boolean dirty = false;
        long gameTime = level.getGameTime();
        long[] keys = states.keySet().toLongArray();
        for (long key : keys) {
            BlockPos pos = BlockPos.of(key);
            BlockState blockState = level.getBlockState(pos);
            HeatSourceDef def = HeatSourceRegistry.resolveState(blockState);

            // Только охладители с профилем таяния (лёд, пакед, блю)
            if (def == null || !def.isPassive() || !def.isCooling() || def.melt() == null) {
                continue;
            }

            var stored = states.get(key);
            if (stored == null || !stored.physical()) {
                continue;
            }

            SealedRoom room = findRoom(level, pos);
            if (room == null) {
                continue;
            }

            HeatSourceDef.MeltParams melt = def.melt();
            float roomTemp = room.getRoomTemperature();
            int cooldown = melt.cooldownAt(roomTemp, def.surfaceTemp());

            // Детерминированный «ролл-таймер»: каждый блок проверяется раз в `cooldown` тиков,
            // но фаза сдвинута по позиции, чтобы все льдины не таяли одновременно.
            long phase = pos.asLong() & 0xFFFFL;
            if ((gameTime + phase) % cooldown != 0L) {
                continue;
            }

            float chance = melt.chanceAt(roomTemp, def.surfaceTemp());
            if (level.random.nextFloat() >= chance) {
                continue;
            }

            float nextReserve = Math.max(0.0f, stored.reserve() - melt.loss());
            if (Math.abs(nextReserve - stored.reserve()) > 1.0e-5f) {
                states.put(key, stored.withReserve(nextReserve));
                dirty = true;
            }
        }

        if (dirty) setDirty();
    }

    /**
     * Процессы воды: при охлаждении среды до 0°C и ниже вода теряет запас
     * (как у льда, но вместо таяния — заморозка в {@link Blocks#ICE});
     * при нагреве до 100°C и выше — «кипение»: пузырьки пара и ускоренный спад.
     */
    public void tickWaterPhysics(ServerLevel level) {
        if (states.isEmpty()) return;

        boolean dirty = false;
        long[] keys = states.keySet().toLongArray();
        for (long key : keys) {
            BlockPos pos = BlockPos.of(key);
            if (level.getBlockState(pos).getBlock() != Blocks.WATER) {
                continue;
            }
            SealedRoom room = findRoom(level, pos);
            if (room == null) {
                continue;
            }
            float roomTemp = room.getRoomTemperature();
            ThermalState stored = states.get(key);
            if (stored == null) {
                continue;
            }

            float drain = 0.0f;
            if (roomTemp <= 0.0f) {
                // Заморозка: вода остывает ниже 0°C — теряет запас, превращается в лёд.
                drain = WATER_FREEZE_DRAIN;
            } else if (roomTemp >= 100.0f) {
                // Кипение: периодические пузырьки пара.
                if (level.random.nextFloat() < STEAM_BUBBLE_CHANCE) {
                    level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                            pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                            3, 0.5, 0.05, 0.5, 0.002);
                }
                drain = WATER_STEAM_DRAIN;
            }
            if (drain <= 0.0f) {
                continue;
            }

            float next = Mth.clamp(stored.reserve() - drain, 0.0f, 1.0f);
            if (next < stored.reserve()) {
                states.put(key, stored.withReserve(next));
                dirty = true;
            }
            if (next <= 0.0f) {
                // Заморозка до конца — вода превращается в лёд.
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
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
