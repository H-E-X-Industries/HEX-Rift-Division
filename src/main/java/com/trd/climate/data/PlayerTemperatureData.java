package com.trd.climate.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Данные температуры игрока, хранящиеся через Data Attachment API.
 * Используется для отслеживания текущей и целевой температуры тела игрока.
 */
public class PlayerTemperatureData {
    private float currentTemp = 20.0f; // Текущая температура тела (°C)
    private float targetTemp = 20.0f;  // Целевая температура окружающей среды (°C)
    private float shelterLevel = 0.0f; // Уровень укрытия от ветра (0.0 - 1.0)

    public static final Codec<PlayerTemperatureData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("currentTemp").forGetter(PlayerTemperatureData::getCurrentTemp),
            Codec.FLOAT.fieldOf("targetTemp").forGetter(PlayerTemperatureData::getTargetTemp),
            Codec.FLOAT.fieldOf("shelterLevel").forGetter(PlayerTemperatureData::getShelterLevel)
        ).apply(instance, PlayerTemperatureData::new)
    );

    public PlayerTemperatureData() {}

    public PlayerTemperatureData(float current, float target, float shelter) {
        this.currentTemp = current;
        this.targetTemp = target;
        this.shelterLevel = shelter;
    }

    // Геттеры и сеттеры
    public float getCurrentTemp() {
        return currentTemp;
    }

    public void setCurrentTemp(float val) {
        this.currentTemp = val;
    }

    public float getTargetTemp() {
        return targetTemp;
    }

    public void setTargetTemp(float val) {
        this.targetTemp = val;
    }

    public float getShelterLevel() {
        return shelterLevel;
    }

    public void setShelterLevel(float val) {
        this.shelterLevel = Math.max(0.0f, Math.min(1.0f, val));
    }

    /**
     * Получить состояние игрока на основе текущей температуры.
     */
    public TemperatureState getState() {
        if (currentTemp < 0.0f) return TemperatureState.FREEZING;
        if (currentTemp < 10.0f) return TemperatureState.SEVERE_COLD;
        if (currentTemp < 15.0f) return TemperatureState.COLD;
        if (currentTemp <= 25.0f) return TemperatureState.COMFORTABLE;
        if (currentTemp <= 32.0f) return TemperatureState.HOT;
        if (currentTemp <= 40.0f) return TemperatureState.HEAT_STROKE;
        return TemperatureState.OVERHEATING;
    }

    public enum TemperatureState {
        FREEZING,       // < 0°C - Обледенение
        SEVERE_COLD,    // 0-10°C - Сильное переохлаждение
        COLD,           // 10-15°C - Легкий холод
        COMFORTABLE,    // 15-25°C - Зона комфорта
        HOT,            // 25-32°C - Жара
        HEAT_STROKE,    // 32-40°C - Тепловой удар
        OVERHEATING     // > 40°C - Перегрев/Ожог
    }
}
