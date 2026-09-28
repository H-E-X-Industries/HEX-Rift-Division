package com.trd.api.vein;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сопоставляет биом с {@link VeinModifier}. Требует Minecraft (резолв тегов/температуры),
 * чистая логика размазывания живёт в {@link VeinModifier} и {@link FractionLayerMatrix#bakeWeights}.
 *
 * <p>Правила из плана:
 * <ul>
 *   <li>Температура (глобальный стержень): холод → буст тяжёлым и редкоземельным,
 *       жара → буст лёгким и благородным.</li>
 *   <li>Биом-теги (локальные бусты): меза/бэдленды → золото и алюминий,
 *       горы/тайга → вольфрам и свинец.</li>
 * </ul>
 */
public final class VeinBiomeResolver {

    /** Пороги базовой температуры ({@link Biome#getBaseTemperature()}). */
    private static final float HOT_TEMPERATURE = 1.0f;
    private static final float COLD_TEMPERATURE = 0.35f;

    private VeinBiomeResolver() {
    }

    public static VeinModifier of(Holder<Biome> biomeHolder) {
        Biome biome = biomeHolder.value();
        Map<FractionType, Float> fractionBoosts = new LinkedHashMap<>();
        Map<String, Float> metalBoosts = new LinkedHashMap<>();

        float temperature = biome.getBaseTemperature();

        // Жаркие биомы (пустыни, саванны, меза): лёгкие и щелочные металлы.
        if (temperature >= HOT_TEMPERATURE) {
            fractionBoosts.put(FractionType.LIGHT_METAL, 1.6f);
            fractionBoosts.put(FractionType.HEAVY_METAL, 0.7f);
            fractionBoosts.put(FractionType.NOBLE_METAL, 1.3f);
        }
        // Холодные биомы (горы, тайга, ледяные пики): тяжёлые и тугоплавкие.
        else if (temperature <= COLD_TEMPERATURE) {
            fractionBoosts.put(FractionType.HEAVY_METAL, 1.6f);
            fractionBoosts.put(FractionType.LIGHT_METAL, 0.7f);
            fractionBoosts.put(FractionType.RARE_EARTH, 1.4f);
        }

        // Меза → золото и алюминий.
        if (biomeHolder.is(BiomeTags.IS_BADLANDS)) {
            metalBoosts.put("gold", 2.0f);
            metalBoosts.put("aluminum", 2.0f);
        }
        // Горы / тайга → вольфрам и свинец.
        if (biomeHolder.is(BiomeTags.IS_MOUNTAIN) || biomeHolder.is(BiomeTags.IS_TAIGA)) {
            metalBoosts.put("tungsten", 2.0f);
            metalBoosts.put("lead", 1.5f);
        }

        return VeinModifier.of(fractionBoosts, metalBoosts);
    }
}