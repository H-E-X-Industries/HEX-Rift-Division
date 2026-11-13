package com.trd.client.config;

import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Общий режим отладки мода.
 * <p>
 * Включается переназначаемой клавишей {@link ModKeyBindings#DEBUG_RENDER_KEY}
 * и управляет всеми отладочными визуализациями сразу: турели, оружие и климат
 * (тепловые поля, источники тепла).
 * <p>
 * Отдельного переключателя у климата намеренно нет: как только игрок переназначает
 * клавишу отладки в настройках, отдельные переключатели разъезжаются с общим режимом,
 * и в итоге непонятно, что сейчас включено. Поэтому у климата тот же флаг и та же клавиша.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class ModDebugMode {

    /** Включён ли общий режим отладки мода. */
    public static boolean enabled = false;

    private ModDebugMode() {}

    /**
     * Клиентский тик: переключает общий режим отладки.
     * <p>
     * Клик обрабатывается один раз за тик ({@code consumeClick()}), а не сырым GLFW-событием —
     * так не срабатывает удержание клавиши и не ловится сочетание вроде F3+H:
     * переключение происходит только когда назначенная клавиша нажата в одиночку.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModKeyBindings.DEBUG_RENDER_KEY.consumeClick()) {
            return;
        }
        if (isAnyOtherKeyPressed()) {
            return;
        }
        enabled = !enabled;
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
