package com.trd.api.conveyor.client;

import com.trd.api.conveyor.ConveyorItem;
import net.minecraft.core.BlockPos;

import java.util.*;

public class ClientConveyorManager {
    private static final Map<UUID, ClientNetworkData> networks = new HashMap<>();

    public static void updateNetwork(UUID networkId, List<ConveyorItem> items, List<BlockPos> path) {
        // После пересоздания/слияния сетей на сервере старые клиентские копии остаются
        // с застывшими предметами и перехватывают рендер у живой сети.
        // Удаляем любые записи, чей путь пересекается с обновляемой сетью.
        networks.values().removeIf(data -> data != null && pathIntersects(data.path, path));
        networks.put(networkId, new ClientNetworkData(items, path));
    }

    private static boolean pathIntersects(List<BlockPos> a, List<BlockPos> b) {
        for (BlockPos pos : a) {
            if (b.contains(pos)) return true;
        }
        return false;
    }

    public static void removeNetwork(UUID networkId) {
        networks.remove(networkId);
    }

    public static ClientNetworkData getNetworkFor(BlockPos pos) {
        for (ClientNetworkData data : networks.values()) {
            if (data.path.contains(pos)) {
                return data;
            }
        }
        return null;
    }

    public static void tickClient() {
        // Экстраполяция на клиенте для плавности
        double speed = com.trd.api.conveyor.ConveyorNetwork.SPEED;
        for (ClientNetworkData data : networks.values()) {
            double maxProgress = data.path.size() - 0.01;
            for (ConveyorItem item : data.items) {
                // Экстраполяция ограничена концом пути: если сервер уже удалил предмет,
                // а пакет синхронизации ещё не дошёл — предмет не должен "убегать" вперёд
                item.setProgress(Math.min(item.getProgress() + speed, maxProgress));
            }
        }
    }

    public static class ClientNetworkData {
        public final List<ConveyorItem> items;
        public final List<BlockPos> path;

        public ClientNetworkData(List<ConveyorItem> items, List<BlockPos> path) {
            this.items = items;
            this.path = path;
        }
        
        public double getIndexFor(BlockPos pos) {
            return path.indexOf(pos);
        }
    }
}
