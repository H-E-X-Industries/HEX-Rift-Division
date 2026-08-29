package com.trd.api.vein;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Модификаторы жилы, действующие в момент генерации/добычи и запекаемые в кусок.
 *
 * <p>Два независимых слоя:
 * <ul>
 *   <li>{@link #fractionMultipliers()} — глобальный стержень: температура биома
 *       сдвигает веса ФРАКЦИЙ (холод → тяжёлые/тугоплавкие, жара → лёгкие).</li>
 *   <li>{@link #metalMultipliers()} — локальные бусты конкретных МЕТАЛЛОВ от
 *       биом-тегов (меза → золото/алюминий, горы/тайга → вольфрам/свинец).</li>
 * </ul>
 *
 * <p>Класс чистый (без Minecraft) — можно грузить и тестировать офлайн.
 */
public record VeinModifier(Map<FractionType, Float> fractionMultipliers,
                           Map<String, Float> metalMultipliers) {

    public static final VeinModifier NONE = new VeinModifier(Map.of(), Map.of());

    public static VeinModifier of(Map<FractionType, Float> fractionMultipliers,
                                  Map<String, Float> metalMultipliers) {
        return new VeinModifier(
                Collections.unmodifiableMap(new LinkedHashMap<>(fractionMultipliers)),
                Collections.unmodifiableMap(new LinkedHashMap<>(metalMultipliers)));
    }

    public float fractionMultiplier(FractionType fraction) {
        return fractionMultipliers.getOrDefault(fraction, 1.0f);
    }

    public float metalMultiplier(String metal) {
        return metalMultipliers.getOrDefault(metal, 1.0f);
    }

    public boolean isEmpty() {
        return fractionMultipliers.isEmpty() && metalMultipliers.isEmpty();
    }

    /**
     * Сливает два модификатора: при совпадении берётся больший буст.
     */
    public VeinModifier combine(VeinModifier other) {
        Map<FractionType, Float> fractions = new LinkedHashMap<>(fractionMultipliers);
        other.fractionMultipliers.forEach((fraction, mult) -> fractions.merge(fraction, mult, Math::max));

        Map<String, Float> metals = new LinkedHashMap<>(metalMultipliers);
        other.metalMultipliers.forEach((metal, mult) -> metals.merge(metal, mult, Math::max));

        return of(fractions, metals);
    }
}