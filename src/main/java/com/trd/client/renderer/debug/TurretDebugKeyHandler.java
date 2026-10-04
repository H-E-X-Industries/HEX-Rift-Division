package com.trd.client.renderer.debug;


import com.trd.client.config.ModKeyBindings;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Переключатель отладочной визуализации турели (чисто клиентское событие).
 * <p>
 * Клавиша берётся из {@link ModKeyBindings#DEBUG_RENDER_KEY}, поэтому её можно
 * переназначить в настройках управления. Клик обрабатывается один раз за тик
 * ({@code consumeClick()}), а не сырым GLFW-событием — так не срабатывает
 * удержание клавиши и не ловится сочетание вроде F3+H: переключение
 * происходит только когда назначенная клавиша нажата в одиночку.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TurretDebugKeyHandler {

    public static boolean debugVisualizationEnabled = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModKeyBindings.DEBUG_RENDER_KEY.consumeClick()) {
            return;
        }
        if (isAnyOtherKeyPressed()) {
            return;
        }
        debugVisualizationEnabled = !debugVisualizationEnabled;
    }

    /** Проверяет, что не зажата ни одна другая клавиша — защита от комбинаций вида F3+H. */
    private static boolean isAnyOtherKeyPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (window == 0L) {
            return false;
        }
        int boundKey = ModKeyBindings.DEBUG_RENDER_KEY.getKey().getValue();
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_MENU; key++) {
            if (key == boundKey) {
                continue;
            }
            if (GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS) {
                return true;
            }
        }
        return false;
    }
}
