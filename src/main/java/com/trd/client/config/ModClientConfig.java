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
     * Меняется командой {@code /trdheat radius <1..100>}.
     */
    public static final ModConfigSpec.IntValue HEAT_DEBUG_RADIUS;

    /**
     * Разрешить отладочный режим в режимах выживания и приключения.
     * По умолчанию отладка доступна только в creative/spectator.
     * Включается командой {@code /trdheat allow}.
     */
    public static final ModConfigSpec.BooleanValue HEAT_DEBUG_ALLOW_SURVIVAL;

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
                .defineInRange("heatDebugRadius", 35, 1, 100);
        HEAT_DEBUG_ALLOW_SURVIVAL = builder
                .comment("Разрешить режим отладки в выживании и приключении.",
                        "По умолчанию отладка доступна только в creative/spectator.")
                .define("heatDebugAllowSurvival", false);
        HEAT_DEBUG_MAX_POINTS = builder
                .comment("Максимальное количество тепловых пятен в отладочном оверлее.",
                        "Больше значение — подробнее картина, но выше нагрузка на FPS.",
                        "Допустимо: 200..20000.")
                .defineInRange("heatDebugMaxPoints", 4000, 200, 20000);
        builder.pop();
        SPEC = builder.build();
    }

    private ModClientConfig() {
    }

    /** Текущий радиус отображения тепловых пятен, блоков. */
    public static int heatDebugRadius() {
        return HEAT_DEBUG_RADIUS.get();
    }

    /** Разрешена ли отладка в выживании/приключении. */
    public static boolean heatDebugAllowSurvival() {
        return HEAT_DEBUG_ALLOW_SURVIVAL.get();
    }

    /** Текущий лимит количества тепловых пятен. */
    public static int heatDebugMaxPoints() {
        return HEAT_DEBUG_MAX_POINTS.get();
    }

    /**
     * Меняет радиус и сразу сохраняет конфиг на диск.
     */
    public static void setHeatDebugRadius(int radius) {
        HEAT_DEBUG_RADIUS.set(Math.max(1, Math.min(100, radius)));
        save();
    }

    /**
     * Разрешает/запрещает отладку в выживании и сохраняет конфиг.
     */
    public static void setHeatDebugAllowSurvival(boolean allow) {
        HEAT_DEBUG_ALLOW_SURVIVAL.set(allow);
        save();
    }

    /**
     * Сохраняет конфиг на диск (в исключительных случаях молча пропускаем —
     * настройка всё равно применяется в текущей сессии).
     */
    private static void save() {
        try {
            SPEC.save();
        } catch (Exception e) {
            com.trd.main.MainRegistry.LOGGER.warn("[TRD] Не удалось сохранить клиентский конфиг: {}", e.toString());
        }
    }
}
