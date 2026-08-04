package com.trd.api.conveyor.client;

import com.trd.api.conveyor.ConveyorItem;
import net.minecraft.core.BlockPos;

import java.util.*;

public class ClientConveyorManager {
    private static final Map<UUID, ClientNetworkData> networks = new HashMap<>();

    public static void updateNetwork(UUID networkId, List<ConveyorItem> items, List<BlockPos> path) {
        networks.put(networkId, new ClientNetworkData(items, path));
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
            for (ConveyorItem item : data.items) {
                // ПРИМЕЧАНИЕ: Это простая экстраполяция. 
                // В идеале мы должны отслеживать коллизии и на клиенте, 
                // чтобы предметы не въезжали друг в друга при задержках пакетов.
                // Но пакеты с сервера будут корректировать их реальное положение.
                item.setProgress(item.getProgress() + speed);
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
