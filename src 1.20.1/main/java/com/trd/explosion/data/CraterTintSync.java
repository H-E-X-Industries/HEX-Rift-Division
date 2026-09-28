package com.trd.explosion.data;

import com.trd.network.ModPacketHandler;
import com.trd.network.packet.explosion.SyncCraterTintsPacket;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;

/**
 * Отправка тинтовых данных клиентам. Большие списки режутся на чанки пакетов,
 * чтобы не превысить лимит размера сетевого пакета.
 */
public final class CraterTintSync {

    private static final int MAX_ADDS_PER_PACKET = 1200;
    private static final int MAX_REMOVES_PER_PACKET = 2000;

    private CraterTintSync() {
    }

    /** Разослать добавления затемнения всем игрокам измерения (из карты). */
    public static void sendAdds(ServerLevel level, Long2IntOpenHashMap entries) {
        if (entries == null || entries.isEmpty()) return;
        long[] pos = entries.keySet().toLongArray();
        int[] dark = new int[pos.length];
        for (int i = 0; i < pos.length; i++) dark[i] = entries.get(pos[i]);
        sendAdds(level, pos, dark);
    }

    /** Разослать добавления затемнения всем игрокам измерения (позиции + ступени). */
    public static void sendAdds(ServerLevel level, long[] pos, int[] dark) {
        if (pos == null || pos.length == 0 || dark == null || dark.length != pos.length) return;
        for (int from = 0; from < pos.length; from += MAX_ADDS_PER_PACKET) {
            int to = Math.min(pos.length, from + MAX_ADDS_PER_PACKET);
            ModPacketHandler.INSTANCE.send(
                    PacketDistributor.DIMENSION.with(() -> level.dimension()),
                    new SyncCraterTintsPacket(level.dimension().location(),
                            Arrays.copyOfRange(pos, from, to),
                            Arrays.copyOfRange(dark, from, to),
                            new long[0]));
        }
    }

    /** Разослать удаления затемнения всем игрокам измерения. */
    public static void sendRemoves(ServerLevel level, long[] removes) {
        if (removes == null || removes.length == 0) return;
        for (int from = 0; from < removes.length; from += MAX_REMOVES_PER_PACKET) {
            int to = Math.min(removes.length, from + MAX_REMOVES_PER_PACKET);
            ModPacketHandler.INSTANCE.send(
                    PacketDistributor.DIMENSION.with(() -> level.dimension()),
                    new SyncCraterTintsPacket(level.dimension().location(),
                            new long[0], new int[0],
                            Arrays.copyOfRange(removes, from, to)));
        }
    }

    /** Синхронизировать все сохранённые затемнения конкретному игроку (вход в мир/измерение/рес. спавн). */
    public static void syncToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        CraterTintData data = CraterTintData.get(level);
        sendAdds(level, data.entries());
    }

    /** Удалить позицию из базы и уведомить клиентов. Возвращает true, если запись существовала. */
    public static void removeAt(ServerLevel level, long pos) {
        CraterTintData data = CraterTintData.get(level);
        if (data.remove(pos)) {
            sendRemoves(level, new long[]{pos});
        }
    }
}