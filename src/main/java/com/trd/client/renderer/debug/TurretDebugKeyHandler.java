package com.trd.client.renderer.debug;


import com.trd.main.MainRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Переключатель отладочной визуализации турели по F3 (чисто клиентское событие). */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TurretDebugKeyHandler {

    public static boolean debugVisualizationEnabled = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // F3 = GLFW_KEY_F3
        if (event.getKey() == GLFW.GLFW_KEY_F3 && event.getAction() == GLFW.GLFW_PRESS) {
            debugVisualizationEnabled = !debugVisualizationEnabled;
        }
    }
}
