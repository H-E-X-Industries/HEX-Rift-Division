package com.trd.worldgen.hive;

import com.mojang.logging.LogUtils;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пост-генерационный проход: ульи по обязательным якорям (рельсы, доски, каменные
 * кирпичи, сундуки, бочки, спавнеры).
 * <p>
 * Почему это не может быть фичей: {@code ChunkGenerator.applyBiomeDecoration()} — то есть
 * все шаги декорации, включая наш — выполняется ДО {@code createStructures()}. На момент
 * работы фичи блоков шахт, крепостей, древних городов и данжей в мире физически ещё нет,
 * поэтому якорь-структура не находится никогда. Мягкая порода видна, потому что она часть
 * шумового рельефа и генерируется рано, поэтому мягкие ульи остались на фиче.
 * <p>
 * Новые чанки складываются в очередь и разбираются на серверном тике с бюджетом:
 * так генерация не блокируется, и мы не лезем в мир прямо из {@code ChunkEvent.Load},
 * о чём NeoForge отдельно предупреждает.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class StructureHivePass {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Обязательные якоря — рельсы, доски, кирпичи, сундуки, бочки, спавнеры. */
    public static final TagKey<Block> REQUIRED_ANCHOR_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "depth_worm_hive_required_anchors"));

    /** Сколько чанков разбираем за один серверный тик. */
    private static final int CHUNKS_PER_TICK = 4;
    /** Сколько колонок в чанке прочесать на поиск обязательного якоря. */
    private static final int ANCHOR_COLUMNS = 6;
    /** Сколько новых чанков держим в очереди; дальше подряд идёт мимо. */
    private static final int MAX_QUEUE = 2048;

    private static final Map<ServerLevel, Pending> PENDING = new ConcurrentHashMap<>();

    private StructureHivePass() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Фича стоит только в IS_OVERWORLD, здесь держим ту же область.
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        PENDING.computeIfAbsent(serverLevel, level -> new Pending()).offer(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        for (Map.Entry<ServerLevel, Pending> entry : PENDING.entrySet()) {
            Pending pending = entry.getValue();
            for (int i = 0; i < CHUNKS_PER_TICK; i++) {
                ChunkPos pos = pending.poll();
                if (pos == null) {
                    break;
                }
                try {
                    process(entry.getKey(), pos);
                } catch (Exception e) {
                    // Одна битая структура не должна ломать весь проход.
                    LOGGER.error("[HEX-Rift-Division] Failed to place depth worm hive in chunk {}", pos, e);
                }
            }
        }
    }

    /** Статика живёт дольше сервера: в одиночной игре при выходе в меню уровень умирает. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static void process(ServerLevel level, ChunkPos chunkPos) {
        // Не форсируем загрузку: чанк мог выгрузиться, пока стоял в очереди.
        ChunkAccess chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
        if (chunk == null) {
            return;
        }

        // Сид из мира и позиции чанка: тот же чанк всегда даёт тот же результат.
        RandomSource random = RandomSource.create(level.getSeed() ^ chunkPos.toLong());

        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        for (int attempt = 0; attempt < ANCHOR_COLUMNS; attempt++) {
            int cx = baseX + random.nextInt(16);
            int cz = baseZ + random.nextInt(16);
            BlockPos anchor = HivePlacer.findAnchor(level, cx, cz, REQUIRED_ANCHOR_TAG);
            if (anchor != null) {
                // Обязательные якоря идут на полной частоте — это и есть «улей у рельсов».
                // Второй улей в этом же чанке не нужен.
                HivePlacer.place(level, level, anchor, random);
                return;
            }
        }
    }

    /** Очередь чанков на уровень: дедуп по позиции и O(1)-счётчик вместо size(). */
    private static final class Pending {

        private final ConcurrentLinkedQueue<ChunkPos> queue = new ConcurrentLinkedQueue<>();
        private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();
        private final AtomicInteger size = new AtomicInteger();

        void offer(ChunkPos pos) {
            if (size.get() >= MAX_QUEUE || !queued.add(pos)) {
                return;
            }
            queue.add(pos);
            size.incrementAndGet();
        }

        ChunkPos poll() {
            ChunkPos pos = queue.poll();
            if (pos != null) {
                size.decrementAndGet();
                queued.remove(pos);
            }
            return pos;
        }
    }
}