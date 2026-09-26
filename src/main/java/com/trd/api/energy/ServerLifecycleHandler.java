package com.trd.api.energy; // <-- Убедись, что package правильный

import com.trd.api.fluids.system.FluidNetworkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

/**
 * Этот обработчик запускает перестройку энергосетей и жидкостных сетей
 * ОДИН РАЗ, когда сервер полностью загрузился.
 * Это предотвращает дедлок при загрузке мира и гарантирует активность сетей.
 */
@EventBusSubscriber(modid = "trd") // <-- Укажи свой MOD_ID
public class ServerLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Сервер полностью запущен, мир стабилен
        LOGGER.info("[HBM-NETWORK] Server has started, rebuilding energy & fluid networks for all dimensions...");

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Мы "будим" менеджеры и запускаем перестройку
            EnergyNetworkManager.get(level).rebuildAllNetworks();
            FluidNetworkManager.get(level).rebuildAllNetworks();
        }

        LOGGER.info("[HBM-NETWORK] Energy & fluid network rebuild complete.");
    }
}
