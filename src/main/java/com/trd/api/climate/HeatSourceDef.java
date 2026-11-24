package com.trd.api.climate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Описание источника тепла (или холода) для климатической системы.
 * <p>
 * <b>Единственный источник правды</b> о числах источника. Все значения
 * задаются в {@link com.trd.climate.calculation.ModHeatSources} через
 * {@link #builder(String)}; в коде расчётов дублирования чисел нет.
 *
 * <h2>Три независимые оси</h2>
 * <ul>
 *   <li>{@link Role} — <b>роль</b>: {@code HEATER} греет, {@code COOLER} охлаждает;</li>
 *   <li>{@link Kind} — <b>тип</b>: {@code ACTIVE} — прибор (печь, радиатор, бойлер) со своей
 *       температурой/топливом, {@code PASSIVE} — природный блок (лава, лёд, магма);</li>
 *   <li>{@code physical} — <b>состояние</b> (только у {@code PASSIVE}, живёт в
 *       {@link com.trd.climate.room.ThermalStateStore}): нефизичный источник имеет запас 1
 *       и его запас не тратится; физичный — оказался в герметичном помещении и тратит запас.</li>
 * </ul>
 * Внимание: «физичный» ≠ «активный». Активный — это прибор; физичный — пассивный блок в комнате.
 *
 * <h2>Запас и эффективность</h2>
 * У пассивного источника есть <b>запас</b> {@code reserve} (0..1), хранящийся в состоянии мира.
 * {@code reserve == 1} — источник «свежий»; по мере работы он падает, и запас <b>и есть</b>
 * коэффициент эффективности: им умножаются выход тепла, радиус, мощность и потолок комнаты.
 * <p>
 * Температура нагревателя выводится из запаса:
 * {@code T = phaseTemp + reserve × (surfaceTemp − phaseTemp)}.
 * При {@code reserve → 0} нагреватель доходит до {@code phaseTemp} (например, лава 1200 → 700 °C)
 * и превращается в {@code depletedBlock} (базальт). У охладителя температура остаётся
 * {@code surfaceTemp}, а падает именно эффективность охлаждения (лёд тает, но остаётся льдом
 * до полного истощения).
 *
 * <h2>Как считается тепло</h2>
 * <pre>
 *   ΔT(у поверхности) = sourceTemp × conductivity × запас    // HEATER (outputHeat — потолок)
 *   ΔT(у поверхности) = coolingPower × запас                 // COOLER
 *   ΔT(в точке)       = ΔT(у поверхности) × спад(расстояние)
 *   спад(d)           = (1 − d/R)^p × (1 + p·d/R),  p — от spread
 *   R                 = radius × мощность × spread
 * </pre>
 * <b>Теплопроводность</b> ({@code conductivity}) — это честная доля температуры источника,
 * доходящая до воздуха: 700 °C × 1.7% = 12 °C у магмы. Именно её видит игрок в паспорте
 * блока, поэтому баланс делается ею, а не скрытым потолком {@code outputHeat}.
 * Форма спада и радиус задаются отдельным полем {@code spread}, чтобы смена доли отдачи
 * не ломала уже настроенный профиль нагрева.
 */
public record HeatSourceDef(
        String id,
        Role role,
        Kind kind,
        /** Базовая (паспортная) температура источника, °C. */
        float surfaceTemp,
        /** HEATER: потолок отдачи у поверхности, °C. Для COOLER всегда 0. */
        float outputHeat,
        /** COOLER: сколько °C источник способен забрать. Для HEATER всегда 0. */
        float coolingPower,
        float radius,
        /**
         * Теплопроводность 0..1 — доля паспортной температуры, доходящая до воздуха (°C =
         * surfaceTemp × conductivity). Показывается в паспорте источника, поэтому именно
         * этим полем балансируется ощущаемый жар.
         */
        float conductivity,
        /**
         * Форма спада и радиус 0..1 (ранее это делала conductivity). 1 — тепло уходит
         * далеко и равномерно, 0 — держится у поверхности. В паспорте не показывается.
         */
        float spread,
        /** Костёр и факел физически не греют большую пещеру. */
        boolean negligibleInLargeRooms,
        /** Нижняя граница шкалы прибора (для PASSIVE не используется). */
        float deviceScaleMin,
        /** Верхняя граница шкалы прибора; {@code <= min} → шкалы нет, активность из состояния. */
        float deviceScaleMax,
        /** Порог истощения: лава → 700 °C (базальт), лёд → 0 °C (вода). */
        float phaseTemp,
        /** Доля запаса, теряемая в секунду при полном дисбалансе с комнатой. */
        float drainRate,
        /** Доля запаса, восстанавливаемая в секунду при сильном перепаде. */
        float restoreRate,
        /** Во сколько раз среда должна «превысить» источник, чтобы началось восстановление. */
        float restoreFactor,
        /** Абсолютный порог восстановления для охладителя с surfaceTemp == 0 (обычный лёд). */
        float restoreBelow,
        /** Во что превращается источник при истощении (базальт, вода, ...). */
        ResourceLocation depletedBlock,
        /**
         * Профиль вероятностного таяния охладителей (лёд/пакед/блю).
         * {@code null} — источник тает обычным линейным drainRate.
         */
        MeltParams melt,
        /**
         * Радиус синхронного застывания текучей части при истощении источника (блоков).
         * 0 — застывает только сам блок-источник. Для лавы >0, чтобы текучая лава
         * вокруг базальтового источника не застывала отдельно спустя время.
         */
        float freezeRadius
) {

    /**
     * Параметры таяния льда: чем выше температура среды, тем выше шанс таяния
     * и тем короче кулдаун между роллами.
     *
     * @param chanceCold шанс за ролл при температуре около {@code surfaceTemp}
     * @param chanceHot  шанс за ролл при {@code surfaceTemp + tempSpan} и выше
     * @param cooldownCold тиков между роллами при холодной среде (например 20)
     * @param cooldownHot  тиков между роллами при горячей среде (например 5)
     * @param tempSpan диапазон температур, на котором интерполируются шанс и кулдаун
     * @param loss доля запаса, теряемая при одном успешном ролле
     */
    public record MeltParams(
            float chanceCold, float chanceHot,
            int cooldownCold, int cooldownHot,
            float tempSpan, float loss
    ) {
        public MeltParams {
            chanceCold = Mth.clamp(chanceCold, 0.0f, 1.0f);
            chanceHot = Mth.clamp(chanceHot, 0.0f, 1.0f);
            cooldownCold = Math.max(1, cooldownCold);
            cooldownHot = Math.max(1, cooldownHot);
            tempSpan = Math.max(1.0f, tempSpan);
            loss = Math.max(0.0f, loss);
        }

        /** Шанс таяния при заданной температуре среды. */
        public float chanceAt(float roomTemp, float surfaceTemp) {
            float t = Mth.clamp((roomTemp - surfaceTemp) / tempSpan, 0.0f, 1.0f);
            return Mth.lerp(t, chanceCold, chanceHot);
        }

        /** Кулдаун между роллами (тиков) при заданной температуре среды. */
        public int cooldownAt(float roomTemp, float surfaceTemp) {
            float t = Mth.clamp((roomTemp - surfaceTemp) / tempSpan, 0.0f, 1.0f);
            return Math.round(Mth.lerp(t, cooldownCold, cooldownHot));
        }
    }

    public enum Role { HEATER, COOLER }

    public enum Kind { ACTIVE, PASSIVE }

    /**
     * Доля отдачи теперь равна самой теплопроводности: 0% → 0 °C, 100% → вся температура.
     * Прежний пол 10% означал, что даже «холодный» источник с 0% проводимости выдавал
     * 70 °C от 700 °C — из-за этого приходилось прятать баланс в скрытый outputHeat.
     */
    public static final float MIN_TRANSFER = 0.0f;
    public static final float MAX_TRANSFER = 1.0f;
    /** Минимальная доля радиуса излучения при нулевой мощности. */
    public static final float MIN_RADIUS_FACTOR = 0.35f;
    /**
     * Множители радиуса от тепловыхода.
     * <p>
     * Минимум поднят с 0.5 до 1.0: после балансировки источников их {@code outputHeat}
     * лежит в шкале ощущаемой игроком температуры (единицы-десятки °C), и старый минимум
     * 0.5 необоснованно вдвое срезал радиус каждого источника. Теперь при
     * {@code outputHeat <= 100} радиус равен заявленному (умноженному только на
     * {@code spread} и мощность), а удвоение получают лишь реально мощные источники.
     */
    public static final float HEAT_RADIUS_MIN_SCALE = 1.0f;
    public static final float HEAT_RADIUS_MAX_SCALE = 2.0f;
    /** Показатель формы спада: 1 — плохой проводник, 3 — хороший. */
    public static final float FALLOFF_EXPONENT_MIN = 1.0f;
    public static final float FALLOFF_EXPONENT_MAX = 3.0f;

    public HeatSourceDef {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("HeatSourceDef: id не может быть пустым");
        }
        if (role == null || kind == null) {
            throw new IllegalArgumentException("HeatSourceDef: role и kind обязательны");
        }
        outputHeat = Mth.clamp(outputHeat, 0.0f, 500.0f);
        coolingPower = Mth.clamp(coolingPower, 0.0f, 500.0f);
        radius = Mth.clamp(radius, 0.5f, 32.0f);
        conductivity = Mth.clamp(conductivity, 0.0f, 1.0f);
        spread = Mth.clamp(spread, 0.0f, 1.0f);
        drainRate = Math.max(0.0f, drainRate);
        restoreRate = Math.max(0.0f, restoreRate);
        restoreFactor = Math.max(1.0f, restoreFactor);
        restoreBelow = Math.abs(restoreBelow);
    }

    public boolean isCooling() {
        return role == Role.COOLER;
    }

    public boolean isPassive() {
        return kind == Kind.PASSIVE;
    }

    /** Доля собственного тепла, передаваемая окружению (фиксированная у пассивных). */
    public float transferFactor() {
        return MIN_TRANSFER + (MAX_TRANSFER - MIN_TRANSFER) * conductivity;
    }

    /**
     * Доля отдачи при <b>текущей</b> температуре источника.
     * <p>
     * У активных нагревателей это функция {@code K / T}, где {@code K = outputHeat}
     * (ощущаемый жар у корпуса), {@code T} — температура прибора. Чем горячее прибор,
     * тем <b>меньше</b> доля его тепла доходит до игрока: печь на 2700 °C не обжигает
     * сильнее печи на 300 °C. Произведение {@code T × (K / T)} даёт стабильные {@code K} °C.
     * У пассивных блоков и охладителей доля фиксированная ({@link #transferFactor()}).
     */
    public float transferFactor(float sourceTemp) {
        if (isPassive() || isCooling()) {
            return transferFactor();
        }
        return Mth.clamp(outputHeat / Math.max(1.0f, sourceTemp), MIN_TRANSFER, MAX_TRANSFER);
    }

    /**
     * Температура, которую источник сообщает внешнему миру (потолок излучения, термометр).
     * <p>
     * У приборов без температурной шкалы ({@code deviceScaleMax <= deviceScaleMin})
     * блок-сущность сообщает «1.0» как флаг включения, а не градусы — подставляем паспорт.
     */
    public float effectiveTemperature(float reportedTemp) {
        if (kind == Kind.ACTIVE && deviceScaleMax <= deviceScaleMin) {
            return surfaceTemp;
        }
        return reportedTemp;
    }

    /** Выход источника у поверхности, °C (полная эффективность). */
    public float radiantOutput(float sourceTemp) {
        return radiantOutput(sourceTemp, 1.0f);
    }

    /**
     * Выход источника с учётом эффективности (запаса) — °C.
     * <p>
     * Без этого множителя остывшая лава с запасом 20% всё равно выдавала бы
     * потолок {@code outputHeat}, пока её температура не упала бы ниже самого потолка.
     */
    public float radiantOutput(float sourceTemp, float efficiency) {
        float e = Mth.clamp(efficiency, 0.0f, 1.0f);
        if (isCooling()) {
            // coolingPower уже в °C: это ровно те градусы, которые источник забирает.
            // Теплопроводность здесь не множитель (иначе лёд внезапно стал бы слабее).
            return coolingPower * e;
        }
        float temp = effectiveTemperature(sourceTemp);
        return Math.min(outputHeat, temp * transferFactor(temp)) * e;
    }

    /** Мощность прибора (0..1) при заданной температуре. */
    public float powerAt(float deviceTemp) {
        if (deviceScaleMax <= deviceScaleMin) {
            return deviceTemp >= deviceScaleMin ? 1.0f : 0.0f;
        }
        return Mth.clamp((deviceTemp - deviceScaleMin) / (deviceScaleMax - deviceScaleMin), 0.0f, 1.0f);
    }

    /** Эффективный радиус излучения при текущей мощности. */
    public float radiusAt(float power) {
        return radius * (MIN_RADIUS_FACTOR + (1.0f - MIN_RADIUS_FACTOR) * power);
    }

    /** Множитель радиуса от тепловыхода. */
    public float heatRadiusScale(float outputHeatValue) {
        return Mth.clamp(outputHeatValue / 100.0f, HEAT_RADIUS_MIN_SCALE, HEAT_RADIUS_MAX_SCALE);
    }

    /** Показатель формы спада температуры от расстояния (задаётся spread). */
    public float falloffExponent() {
        return FALLOFF_EXPONENT_MIN
                + (FALLOFF_EXPONENT_MAX - FALLOFF_EXPONENT_MIN) * (1.0f - spread);
    }

    /** Доля радиуса, доступная источнику из-за его формы излучения (spread). */
    public float spreadByConductivity() {
        return 0.6f + 0.4f * spread;
    }

    /**
     * Температура источника при заданном запасе (только для PASSIVE).
     * Нагреватель остывает вместе с запасом; охладитель остаётся на surfaceTemp.
     */
    public float temperatureAt(float reserve) {
        if (!isPassive() || isCooling()) {
            return surfaceTemp;
        }
        float span = surfaceTemp - phaseTemp;
        if (span <= 0.0f) {
            return surfaceTemp;
        }
        return phaseTemp + span * Mth.clamp(reserve, 0.0f, 1.0f);
    }

    /** Шкала полного дисбаланса с комнатой: делитель для скорости расхода запаса. */
    public float workSpan() {
        if (isCooling()) {
            return Math.max(1.0f, coolingPower);
        }
        return Math.max(1.0f, surfaceTemp - phaseTemp);
    }

    /** Создаёт builder с разумными значениями по умолчанию. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Builder с именованными полями — вместо набора позиционных float. */
    public static final class Builder {
        private final String id;
        private Role role = Role.HEATER;
        private Kind kind = Kind.ACTIVE;
        private float surfaceTemp;
        private float outputHeat;
        private float coolingPower;
        private float radius = 4.0f;
        private float conductivity = 0.5f;
        private float spread = 0.5f;
        private boolean negligibleInLargeRooms;
        private float deviceScaleMin = 1.0f;
        private float deviceScaleMax = 1.0f;
        private float phaseTemp;
        private float drainRate;
        private float restoreRate;
        private float restoreFactor = 2.0f;
        private float restoreBelow = 10.0f;
        private ResourceLocation depletedBlock;
        private MeltParams melt;
        private float freezeRadius;

        private Builder(String id) {
            this.id = id;
        }

        public Builder role(Role value) { this.role = value; return this; }
        public Builder kind(Kind value) { this.kind = value; return this; }
        public Builder heater() { return role(Role.HEATER); }
        public Builder cooler() { return role(Role.COOLER); }
        public Builder active() { return kind(Kind.ACTIVE); }
        public Builder passive() { return kind(Kind.PASSIVE); }
        public Builder surfaceTemp(float value) { this.surfaceTemp = value; return this; }
        public Builder outputHeat(float value) { this.outputHeat = value; return this; }
        public Builder coolingPower(float value) { this.coolingPower = value; return this; }
        public Builder radius(float value) { this.radius = value; return this; }
        public Builder conductivity(float value) { this.conductivity = value; return this; }
        /** Форма спада/радиус. По умолчанию 0.5 — как у прежней conductivity. */
        public Builder spread(float value) { this.spread = value; return this; }
        public Builder negligibleInLargeRooms(boolean value) { this.negligibleInLargeRooms = value; return this; }
        public Builder deviceScale(float min, float max) {
            this.deviceScaleMin = min;
            this.deviceScaleMax = max;
            return this;
        }
        public Builder phaseTemp(float value) { this.phaseTemp = value; return this; }
        public Builder drainRate(float value) { this.drainRate = value; return this; }
        public Builder restoreRate(float value) { this.restoreRate = value; return this; }
        public Builder restoreFactor(float value) { this.restoreFactor = value; return this; }
        public Builder restoreBelow(float value) { this.restoreBelow = value; return this; }
        public Builder depletedBlock(String value) {
            this.depletedBlock = value == null ? null : ResourceLocation.parse(value);
            return this;
        }
        public Builder melt(float chanceCold, float chanceHot,
                            int cooldownCold, int cooldownHot,
                            float tempSpan, float loss) {
            this.melt = new MeltParams(chanceCold, chanceHot, cooldownCold, cooldownHot, tempSpan, loss);
            return this;
        }
        public Builder freezeRadius(float value) { this.freezeRadius = value; return this; }

        public HeatSourceDef build() {
            if (role == Role.HEATER && coolingPower != 0.0f) {
                throw new IllegalArgumentException("HEATER " + id + " не может иметь coolingPower");
            }
            if (role == Role.COOLER && outputHeat != 0.0f) {
                throw new IllegalArgumentException("COOLER " + id + " не может иметь outputHeat");
            }
            if (kind == Kind.PASSIVE && role == Role.HEATER && phaseTemp >= surfaceTemp) {
                throw new IllegalArgumentException(
                        "PASSIVE HEATER " + id + ": phaseTemp должен быть ниже surfaceTemp");
            }
            return new HeatSourceDef(id, role, kind, surfaceTemp, outputHeat, coolingPower,
                    radius, conductivity, spread, negligibleInLargeRooms, deviceScaleMin, deviceScaleMax,
                    phaseTemp, drainRate, restoreRate, restoreFactor, restoreBelow, depletedBlock, melt,
                    freezeRadius);
        }
    }
}
