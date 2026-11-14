package com.trd.api.climate;

import net.minecraft.util.Mth;

/**
 * Описание источника тепла (или холода) для климатической системы.
 * <p>
 * Это <b>единственный источник правды</b> о том, как источник влияет на окружение.
 * Одна и та же запись используется:
 * <ul>
 *   <li>для расчёта лучистого тепла в точке (игрок, термометры, дебаг-оверлей);</li>
 *   <li>для расчёта объёмного нагрева герметичного помещения;</li>
 *   <li>для клиентской визуализации теплового поля.</li>
 * </ul>
 * Раньше эти три задачи решались тремя разными таблицами с разными числами,
 * из-за чего дебаг-режим и термометр показывали разные значения.
 *
 * @param id                    идентификатор источника ({@code "trd:heater"})
 * @param maxHeat               максимальное лучистое тепло в упор, °C (прибавка к температуре среды)
 * @param radius                радиус излучения, блоков
 * @param comfortCap            предел ощущаемой температуры рядом с источником, °C:
 *                              для нагревателей — потолок, для охладителей (лёд) — нижняя граница
 * @param roomTarget            потолок объёмного нагрева герметичного помещения, °C
 * @param roomPower             мощность объёмного нагрева (скорость выхода на потолок)
 * @param cooling               true — источник холода, false — источник тепла
 * @param negligibleInLargeRooms true — не влияет на помещения объёмом более 500 блоков
 *                              (костёр и факел физически не греют огромную пещеру)
 * @param tempMin               температура прибора, дающая 0% мощности
 * @param tempMax               температура прибора, дающая 100% мощности
 */
public record HeatSourceDef(
        String id,
        float maxHeat,
        float radius,
        float comfortCap,
        float roomTarget,
        float roomPower,
        boolean cooling,
        boolean negligibleInLargeRooms,
        float tempMin,
        float tempMax
) {

    public HeatSourceDef {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("HeatSourceDef: id не может быть пустым");
        }
        maxHeat = Mth.clamp(maxHeat, 0.0f, 500.0f);
        radius = Mth.clamp(radius, 0.5f, 32.0f);
        roomPower = Math.max(0.0f, roomPower);
    }

    /**
     * Мощность источника (0..1) при заданной температуре прибора.
     * <p>
     * Для источников без температурной шкалы ({@code tempMin == tempMax}) мощность
     * определяется сравнением: прибор «горит» при температуре не ниже {@code tempMin}.
     * Именно так работают ванильные источники: предикат состояния уже проверил,
     * что костёр зажжён, и передаёт сюда 1.0, а незажжённый — 0.0.
     */
    public float powerAt(float deviceTemp) {
        if (tempMax <= tempMin) {
            return deviceTemp >= tempMin ? 1.0f : 0.0f;
        }
        return Mth.clamp((deviceTemp - tempMin) / (tempMax - tempMin), 0.0f, 1.0f);
    }

    /**
     * Потолок объёмного нагрева при текущей мощности источника.
     * <p>
     * При мощности меньше 100% потолок смещается от {@link #roomTarget} к нейтральной
     * температуре: холодный нагреватель греет комнату скромнее, чем раскалённый.
     *
     * @param deviceTemp температура прибора
     * @param neutral    нейтральная температура, к которой стремится потолок при нулевой мощности
     */
    public float roomTargetAt(float deviceTemp, float neutral) {
        float power = powerAt(deviceTemp);
        if (cooling) {
            // Для охладителя потолок — это нижняя граница; при слабом охлаждении она выше.
            return neutral + (roomTarget - neutral) * power;
        }
        return neutral + (roomTarget - neutral) * power;
    }

    /**
     * Мощность объёмного нагрева при текущей температуре прибора.
     */
    public float roomPowerAt(float deviceTemp) {
        return roomPower * powerAt(deviceTemp);
    }
}
