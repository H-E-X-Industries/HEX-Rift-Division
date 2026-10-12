package com.trd.climate.client;

/**
 * Клиентский обработчик температуры.
 * Хранит данные о температуре локально на клиенте для отображения в HUD.
 */
public class ClientTemperatureHandler {
    
    private static float currentTemp = 20.0f;
    private static float targetTemp = 20.0f;

    /**
     * Обновляет клиентский кэш температуры (вызывается из сетевого пакета).
     */
    public static void setClientTemp(float current, float target) {
        currentTemp = current;
        targetTemp = target;
    }

    /**
     * Получить текущую температуру тела игрока.
     */
    public static float getCurrentTemp() {
        return currentTemp;
    }

    /**
     * Получить целевую температуру окружающей среды.
     */
    public static float getTargetTemp() {
        return targetTemp;
    }

    /**
     * Получить цвет для отображения температуры в HUD.
     * Синий - холодно, белый - нормально, красный - жарко.
     */
    public static int getTemperatureColor() {
        if (currentTemp < 15.0f) {
            return 0x55AAFF; // Холод - синий
        } else if (currentTemp > 25.0f) {
            return 0xFF5555; // Жара - красный
        } else {
            return 0xFFFFFF; // Комфорт - белый
        }
    }
}
