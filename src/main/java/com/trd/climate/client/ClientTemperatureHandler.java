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
     * Получить цвет для отображения температуры в HUD в зависимости от состояния.
     */
    public static int getTemperatureColor() {
        if (currentTemp < 0.0f) {
            return 0x55FFFF; // Обледенение - ледяной аквамарин
        } else if (currentTemp < 10.0f) {
            return 0x55AAFF; // Сильное переохлаждение - синий
        } else if (currentTemp < 15.0f) {
            return 0x88CCFF; // Легкий холод - светло-синий
        } else if (currentTemp <= 25.0f) {
            return 0xFFFFFF; // Зона комфорта - белый
        } else if (currentTemp <= 32.0f) {
            return 0xFFEE66; // Тепло - янтарно-желтый
        } else if (currentTemp <= 40.0f) {
            return 0xFFAA33; // Жара / тепловой удар - оранжевый
        } else {
            return 0xFF4444; // Критический перегрев - алый
        }
    }
}
