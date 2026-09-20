package com.trd.api.conveyor.client;

import com.trd.api.conveyor.ConveyorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ClientConveyorManager {
    private static final Map<UUID, ClientNetworkData> networks = new HashMap<>();

    /**
     * Максимальное количество тиков на которое клиент может «убежать» вперёд сервера.
     * Если расхождение меньше — оставляем клиентское значение (нет скачка назад).
     * Если больше — корректируем к серверному.
     */
    private static final double MAX_AHEAD_TICKS = 3.0;

    public static void updateNetwork(UUID networkId, List<ConveyorItem> serverItems, List<BlockPos> path) {
        // Удаляем устаревшие записи с пересекающимися путями (после перестройки сети)
        networks.values().removeIf(data -> data != null && !networks.containsKey(networkId)
                && pathIntersects(data.path, path));

        ClientNetworkData existing = networks.get(networkId);

        if (existing == null || !existing.path.equals(path)) {
            // Сеть новая или путь изменился — принимаем данные сервера как есть
            networks.put(networkId, new ClientNetworkData(serverItems, path));
        } else {
            // Сеть существует — reconcile: сопоставляем серверные предметы с клиентскими
            // и корректируем progress без резких скачков
            reconcileItems(existing, serverItems);
        }
    }

    /**
     * Сопоставляет серверный список предметов с клиентским.
     * Для каждого серверного предмета пытаемся найти соответствующий клиентский
     * (по ItemStack + близкому progress) и скорректировать его progress плавно.
     */
    private static void reconcileItems(ClientNetworkData existing, List<ConveyorItem> serverItems) {
        double speed = com.trd.api.conveyor.ConveyorNetwork.SPEED;

        // Помечаем уже «подобранные» клиентские предметы
        boolean[] matched = new boolean[existing.items.size()];

        List<ConveyorItem> reconciledItems = new ArrayList<>(serverItems.size());

        for (ConveyorItem serverItem : serverItems) {
            double serverProgress = serverItem.getProgress();
            ItemStack serverStack = serverItem.getStack();

            // Ищем ближайший клиентский предмет с тем же ItemStack
            int bestIdx = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < existing.items.size(); i++) {
                if (matched[i]) continue;
                ConveyorItem clientItem = existing.items.get(i);
                if (!ItemStack.isSameItemSameTags(clientItem.getStack(), serverStack)) continue;
                double dist = Math.abs(clientItem.getProgress() - serverProgress);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }

            ConveyorItem result;
            if (bestIdx >= 0 && bestDist <= MAX_AHEAD_TICKS * speed) {
                // Нашли соответствующий клиентский предмет
                matched[bestIdx] = true;
                ConveyorItem clientItem = existing.items.get(bestIdx);
                double clientProgress = clientItem.getProgress();

                // Если клиент убежал вперёд — оставляем клиентское значение
                // (не скачем назад, клиентская экстраполяция правильная)
                // Если клиент отстал или прыгнул — корректируем к серверному
                double usedProgress;
                if (clientProgress >= serverProgress && clientProgress - serverProgress <= MAX_AHEAD_TICKS * speed) {
                    usedProgress = clientProgress; // клиент чуть впереди — всё нормально
                } else {
                    usedProgress = serverProgress; // расхождение слишком большое — снэп
                }

                result = new ConveyorItem(serverStack, usedProgress);
                result.setPrevOverridePos(serverItem.getPrevOverridePos());
            } else {
                // Не нашли соответствие — новый предмет (появился на ленте)
                result = new ConveyorItem(serverStack, serverProgress);
                result.setPrevOverridePos(serverItem.getPrevOverridePos());
            }
            reconciledItems.add(result);
        }

        // Заменяем список предметов на согласованный
        existing.items.clear();
        existing.items.addAll(reconciledItems);
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
        // Клиентская экстраполяция — продвигаем предметы на каждом клиентском тике
        // чтобы движение было плавным между серверными пакетами
        double speed = com.trd.api.conveyor.ConveyorNetwork.SPEED;
        for (ClientNetworkData data : networks.values()) {
            double maxProgress = data.path.size() - 0.01;
            for (ConveyorItem item : data.items) {
                item.setProgress(Math.min(item.getProgress() + speed, maxProgress));
            }
        }
    }

    public static class ClientNetworkData {
        public final List<ConveyorItem> items;
        public final List<BlockPos> path;

        public ClientNetworkData(List<ConveyorItem> items, List<BlockPos> path) {
            // Создаём изменяемые списки (нужно для reconcile)
            this.items = new ArrayList<>(items);
            this.path = path;
        }

        public double getIndexFor(BlockPos pos) {
            return path.indexOf(pos);
        }
    }
}
