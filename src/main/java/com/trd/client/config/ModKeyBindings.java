package com.trd.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши управления оружием. Регистрируются в {@code ModClientSetup#registerKeyMappings}.
 */
public class ModKeyBindings {

    /** Перезарядка оружия. */
    public static final KeyMapping RELOAD_KEY = new KeyMapping(
            "key.trd.reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.trd"
    );

    /** Разрядка оружия. */
    public static final KeyMapping UNLOAD_KEY = new KeyMapping(
            "key.trd.unload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.trd"
    );

    /** Отладочная визуализация оружия (дебаг-рендеры). Переназначается в настройках. */
    public static final KeyMapping DEBUG_RENDER_KEY = new KeyMapping(
            "key.trd.debug_render",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F3,
            "key.categories.trd"
    );
}
