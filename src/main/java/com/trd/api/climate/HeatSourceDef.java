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
 *
 * <h2>Как считается тепло</h2>
 * <pre>
 *   ΔT(у поверхности) = min(maxHeat, sourceTemp × (0.1 + 0.8 × conductivity))
 *   ΔT(в точке)       = ΔT(у поверхности) × спад(расстояние)
 *   спад(d)           = (1 − d/R)^p × (1 + p·d/R),  p — от теплопроводности
 *   R                 = radius × (мощность) × (тепловыход) × (теплопроводность)
 * </pre>
 * То есть <b>теплопроводность — это доля температуры самого источника</b>,
 * доходящая до соседних блоков: при conductivity = 1 блок отдаёт до 90% своего
 * тепла (0.1 + 0.8 × 1), при conductivity = 0 — лишь 10%. Например, нагреватель
 * из доломитовых кирпичей (conductivity 0.35) при 1000 °C отдаёт соседям
 * около 380 °C, а медный котёл (conductivity 0.6) при тех же 1000 °C — больше.
 * <p>
 * Параметр {@code maxHeat} при этом остаётся <b>потолком</b>: сколько градусов
 * источник способен добавить соседям у самой поверхности, независимо от его
 * температуры (нужно, чтобы обычный костёр не разогревал комнату до 400 °C).
 * <p>
 * Жёстких потолков температуры у самих блоков нет, но есть два ограничителя,
 * не дающих нескольким источникам сложить тепло в космос:
 * убывающая отдача для перекрывающихся источников и потолок «температура
 * источника» (см. {@link com.trd.climate.calculation.HeatSourceHelper}).
 *
 * @param id                    идентификатор источника ({@code "trd:heater"})
 * @param maxHeat               потолок выхода источника: сколько градусов он может
 *                              добавить соседям у самой поверхности, °C
 * @param radius                базовый радиус излучения, блоков (при 100% мощности).
 *                              Уточняется по мощности, тепловыходу и теплопроводности
 *                              источника (см. {@link #heatRadiusScale(float)},
 *                              {@link #spreadByConductivity()})
 * @param conductivity          теплопроводность 0..1: доля температуры источника,
 *                              доходящая до соседей (0.1 при 0, 0.9 при 1)
 * @param sourceTemp            температура самого источника, °C (лава 1200, костёр 700, лёд 0).
 *                              Блок не может нагреться выше температуры источника, даже если
 *                              рядом действуют сразу несколько источников
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
        float conductivity,
        float sourceTemp,
        float roomTarget,
        float roomPower,
        boolean cooling,
        boolean negligibleInLargeRooms,
        float tempMin,
        float tempMax
) {

    /** Минимальная доля передаваемого тепла при теплопроводности 0. */
    public static final float MIN_TRANSFER = 0.1f;
    /** Максимальная доля передаваемого тепла при теплопроводности 1. */
    public static final float MAX_TRANSFER = 0.9f;

    /** Минимальная доля радиуса излучения при нулевой мощности. */
    public static final float MIN_RADIUS_FACTOR = 0.35f;

    /**
     * Минимальный множитель радиуса от тепловыхода источника.
     * Слабый источник (костёр на 40 °C) греет всего в паре блоков.
     */
    public static final float HEAT_RADIUS_MIN_SCALE = 0.5f;
    /**
     * Максимальный множитель радиуса от тепловыхода источника.
     * Мощный источник (лава, раскалённый нагреватель) чувствуется издалека.
     */
    public static final float HEAT_RADIUS_MAX_SCALE = 2.0f;

    /**
     * Минимальный показатель формы спада (идеальный проводник тепла).
     * При значении 1 тепло расходится почти линейно и далеко.
     */
    public static final float FALLOFF_EXPONENT_MIN = 1.0f;
    /**
     * Максимальный показатель формы спада (плохой проводник).
     * При значении 3 тепло почти не выходит за пределы поверхности источника.
     */
    public static final float FALLOFF_EXPONENT_MAX = 3.0f;

    public HeatSourceDef {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("HeatSourceDef: id не может быть пустым");
        }
        maxHeat = Mth.clamp(maxHeat, 0.0f, 500.0f);
        radius = Mth.clamp(radius, 0.5f, 32.0f);
        conductivity = Mth.clamp(conductivity, 0.0f, 1.0f);
        roomPower = Math.max(0.0f, roomPower);
    }

    /**
     * Доля собственного тепла, передаваемая окружению (по теплопроводности).
     * conductivity = 1 → {@value #MAX_TRANSFER}, conductivity = 0 → {@value #MIN_TRANSFER}.
     */
    public float transferFactor() {
        return MIN_TRANSFER + (MAX_TRANSFER - MIN_TRANSFER) * conductivity;
    }

    /**
     * Выход источника у самой поверхности, °C.
     * <p>
     * Нагреватель отдаёт соседям долю своей температуры (по теплопроводности),
     * но не больше потолка {@code maxHeat}. Охладитель, наоборот, забирает
     * фиксированное количество градусов (у льда это и есть его «температура»
     * относительно окружающей среды).
     *
     * @param sourceTemp текущая температура источника, °C
     */
    public float radiantOutput(float sourceTemp) {
        if (cooling) {
            return maxHeat * transferFactor();
        }
        return Math.min(maxHeat, sourceTemp * transferFactor());
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
     * Эффективный радиус излучения при текущей мощности источника:
     * чем слабее источник, тем ближе к нему ощущается тепло.
     */
    public float radiusAt(float power) {
        return radius * (MIN_RADIUS_FACTOR + (1.0f - MIN_RADIUS_FACTOR) * power);
    }

    /**
     * Множитель радиуса от тепловыхода источника.
     * <p>
     * Радиус влияния растёт вместе с тем, сколько градусов источник отдаёт
     * у своей поверхности: костёр (+40 °C) греет всего в паре блоков
     * (×{@value #HEAT_RADIUS_MIN_SCALE}), а лава (+500 °C) ощущается
     * издалека (×{@value #HEAT_RADIUS_MAX_SCALE}). Это делает градиент
     * температуры естественным: у мощного источника тепло спадает плавно
     * на десяток блоков, а не обрывается за 2–3 блока от сотен градусов
     * до комнатной температуры.
     *
     * @param outputHeat тепловыход у поверхности, °C ({@link #radiantOutput(float)})
     */
    public float heatRadiusScale(float outputHeat) {
        return Mth.clamp(outputHeat / 100.0f, HEAT_RADIUS_MIN_SCALE, HEAT_RADIUS_MAX_SCALE);
    }

    /**
     * Показатель формы спада температуры от расстояния.
     * <p>
     * Определяется <b>теплопроводностью</b> источника: чем лучше материал
     * проводит тепло, тем дальше и равномернее тепло расходится от него.
     * <ul>
     *   <li>лава (conductivity 0.95) → ~1.1 — тепло далеко разносится
     *       и плавно гаснет у края радиуса;</li>
     *   <li>нагреватель из доломитовых кирпичей (0.35) → ~2.3 —
     *       тепло держится у самой поверхности, радиус греет не весь.</li>
     * </ul>
     *
     * @see com.trd.climate.calculation.HeatSourceHelper#falloffFactor(float, float)
     */
    public float falloffExponent() {
        return FALLOFF_EXPONENT_MIN
                + (FALLOFF_EXPONENT_MAX - FALLOFF_EXPONENT_MIN) * (1.0f - conductivity);
    }

    /**
     * Доля радиуса, «доступная» источнику из-за его теплопроводности.
     * <p>
     * Плохой проводник (нагреватель из доломитовых кирпичей) тепло дальше
     * себя не пускает: его радиус меньше, и греет он вплотную. Хороший
     * проводник (лава, медь) рассеивает тепло по всему радиусу.
     *
     * @return множитель 0.6…1.0
     */
    public float spreadByConductivity() {
        return 0.6f + 0.4f * conductivity;
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
        // Для охладителя потолок — это нижняя граница; при слабом охлаждении она выше.
        return neutral + (roomTarget - neutral) * power;
    }

    /**
     * Мощность объёмного нагрева при текущей температуре прибора.
     */
    public float roomPowerAt(float deviceTemp) {
        return roomPower * powerAt(deviceTemp);
    }
}
