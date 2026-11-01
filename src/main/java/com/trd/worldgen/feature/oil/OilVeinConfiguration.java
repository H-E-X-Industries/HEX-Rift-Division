package com.trd.worldgen.feature.oil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record OilVeinConfiguration(
        int minSize,
        int maxSize,
        int minY,
        int maxY,
        float density,
        int rarity,
        float maxStretch,
        String veinId,
        VeinShape shape,
        ReservoirType reservoirType
) implements FeatureConfiguration {

    public static final Codec<OilVeinConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_size").forGetter(OilVeinConfiguration::minSize),
            Codec.INT.fieldOf("max_size").forGetter(OilVeinConfiguration::maxSize),
            Codec.INT.fieldOf("min_y").forGetter(OilVeinConfiguration::minY),
            Codec.INT.fieldOf("max_y").forGetter(OilVeinConfiguration::maxY),
            Codec.FLOAT.fieldOf("density").forGetter(OilVeinConfiguration::density),
            Codec.INT.optionalFieldOf("rarity", 200).forGetter(OilVeinConfiguration::rarity),
            Codec.FLOAT.optionalFieldOf("max_stretch", 2.0f).forGetter(OilVeinConfiguration::maxStretch),
            Codec.STRING.optionalFieldOf("vein_id", "").forGetter(OilVeinConfiguration::veinId),
            StringRepresentable.fromEnum(VeinShape::values).optionalFieldOf("shape", VeinShape.DOME).forGetter(OilVeinConfiguration::shape),
            StringRepresentable.fromEnum(ReservoirType::values).optionalFieldOf("reservoir_type", ReservoirType.POROUS_STONE).forGetter(OilVeinConfiguration::reservoirType)
    ).apply(instance, OilVeinConfiguration::new));
}
