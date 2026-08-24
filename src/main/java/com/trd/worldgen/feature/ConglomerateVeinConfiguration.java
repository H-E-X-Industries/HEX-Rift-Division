package com.trd.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record ConglomerateVeinConfiguration(
        int minSize,
        int maxSize,
        int minY,
        int maxY,
        float density,
        float depletionChance,
        int rarity,       // в среднем одна жила раз в N чанков
        float maxStretch, // максимальное растяжение главной оси (1 = сфера)
        String veinId     // стабильный id для детерминированной генерации и UUID жилы
) implements FeatureConfiguration {

    public static final Codec<ConglomerateVeinConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_size").forGetter(ConglomerateVeinConfiguration::minSize),
            Codec.INT.fieldOf("max_size").forGetter(ConglomerateVeinConfiguration::maxSize),
            Codec.INT.fieldOf("min_y").forGetter(ConglomerateVeinConfiguration::minY),
            Codec.INT.fieldOf("max_y").forGetter(ConglomerateVeinConfiguration::maxY),
            Codec.FLOAT.fieldOf("density").forGetter(ConglomerateVeinConfiguration::density),
            Codec.FLOAT.fieldOf("depletion_chance").forGetter(ConglomerateVeinConfiguration::depletionChance),
            Codec.INT.optionalFieldOf("rarity", 10).forGetter(ConglomerateVeinConfiguration::rarity),
            Codec.FLOAT.optionalFieldOf("max_stretch", 2.0f).forGetter(ConglomerateVeinConfiguration::maxStretch),
            Codec.STRING.optionalFieldOf("vein_id", "").forGetter(ConglomerateVeinConfiguration::veinId)
    ).apply(instance, ConglomerateVeinConfiguration::new));
}
