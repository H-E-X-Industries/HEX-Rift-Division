package com.trd.api.climate;

/**
 * Интерфейс источника тепла/холода для блоков мода.
 * <p>
 * Реализуется блок-сущностями приборов, которые нагревают окружающее пространство:
 * нагреватель, котёл, плавильня, коксовая печь, малая плавильня, нагреватель
 * химического завода, электрическая печь и т.п.
 * <p>
 * Блок-сущность сообщает <b>свою</b> температуру в собственной шкале прибора
 * (у нагревателя это 0..1600 °C, у котла 20..600 °C). Как это значение переводится
 * в лучистое тепло среды и в нагрев объёма помещения, решает реестр
 * {@link HeatSourceRegistry} — сам блок о физике среды ничего не знает.
 * <p>
 * Пример:
 * <pre>{@code
 * public class HeaterBlockEntity extends BlockEntity implements IHeatSource {
 *     @Override public String getHeatSourceId() { return "trd:heater"; }
 *     @Override public float getSourceTemperature() { return temperature; }
 *     @Override public boolean isHeatActive() { return burnTime > 0; }
 * }
 * }</pre>
 */
public interface IHeatSource {

    /**
     * Идентификатор источника в {@link HeatSourceRegistry}, например {@code "trd:heater"}.
     * <p>
     * Строка должна совпадать с {@link HeatSourceDef#id()} зарегистрированного описания.
     */
    String getHeatSourceId();

    /**
     * Текущая температура прибора в градусах Цельсия в его собственной шкале.
     * <p>
     * Для приборов без температуры (например, электрическая печь) достаточно вернуть
     * условное значение: реестр сам интерпретирует его как «горит / не горит».
     */
    float getSourceTemperature();

    /**
     * Активен ли источник (горит, работает, выдаёт тепло).
     * <p>
     * По умолчанию считается активным, если температура выше нуля. Приборы,
     * у которых «горение» не совпадает с наличием температуры, переопределяют метод.
     */
    default boolean isHeatActive() {
        return getSourceTemperature() > 0.0f;
    }
}
