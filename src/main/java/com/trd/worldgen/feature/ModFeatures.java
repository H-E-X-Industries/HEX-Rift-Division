package com.trd.worldgen.feature;

import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MainRegistry.MOD_ID);

    public static final java.util.function.Supplier<Feature<ConglomerateVeinConfiguration>> CONGLOMERATE_VEIN =
            FEATURES.register("conglomerate_vein",
                    () -> new ConglomerateVeinFeature(ConglomerateVeinConfiguration.CODEC));

    public static final java.util.function.Supplier<Feature<SpecialVeinConfiguration>> SPECIAL_VEIN =
            FEATURES.register("special_vein",
                    () -> new SpecialVeinFeature(SpecialVeinConfiguration.CODEC));
}
