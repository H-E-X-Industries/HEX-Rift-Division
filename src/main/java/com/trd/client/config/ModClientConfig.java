package com.trd.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Клиентские настройки мода (trd-client.toml).
 * <p>
 * Доступны для редактирования из меню модов (Mod Menu → Config) и напрямую в файле.
 */
public final class ModClientConfig {

    public static final ModConfigSpec SPEC;

    /**
     * Радиус (в блоках), в пределах которого отладочный оверлей тепла
     * показывает тепловые пятна вокруг игрока. Ограничивает нагрузку на ПК
     * в местах с огромным количеством источников (например, лавовые озёра в аду).
     */
    public static final ModConfigSpec.IntValue HEAT_DEBUG_RADIUS;

    /**
     * Максимальное количество тепловых пятен в отладочном оверлее.
     * Больше — красивее, но дороже по FPS (каждое пятно считается и рисуется).
     */
    public static final ModConfigSpec.IntValue HEAT_DEBUG_MAX_POINTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("climate");
        HEAT_DEBUG_RADIUS = builder
                .comment("Радиус отображения тепловых пятен в режиме отладки тепла (блоков).",
                        "Больше радиус — больше нагрузка на ПК. Допустимо: 1..100.")
                .defineInRange("heatDebugRadius", 24, 1, 100);
        HEAT_DEBUG_MAX_POINTS = builder
                .comment("Максимальное количество тепловых пятен в отладочном оверлее.",
                        "Больше значение — подробнее картина, но выше нагрузка на FPS.",
                        "Допустимо: 200..20000.")
                .defineInRange("heatDebugMaxPoints", 2000, 200, 20000);
        builder.pop();
        SPEC = builder.build();
    }

    private ModClientConfig() {
    }

    /** Текущий радиус отображения тепловых пятен, блоков. */
    public static int heatDebugRadius() {
        return HEAT_DEBUG_RADIUS.get();
    }

    /** Текущий лимит количества тепловых пятен. */
    public static int heatDebugMaxPoints() {
        return HEAT_DEBUG_MAX_POINTS.get();
    }
}
