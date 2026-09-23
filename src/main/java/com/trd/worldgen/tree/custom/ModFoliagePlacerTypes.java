package com.trd.worldgen.tree.custom;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import com.trd.main.MainRegistry;

// Размещать в: src/main/java/razchexlitiel/trd/worldgen/tree/custom/ModFoliagePlacerTypes.java
public class ModFoliagePlacerTypes {

    // Реестр для генераторов листвы
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACER_TYPES =
            DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, MainRegistry.MOD_ID);

    // Наша массивная хвоя Секвойи
    public static final Supplier<FoliagePlacerType<GiantSequoiaFoliagePlacer>> GIANT_SEQUOIA_FOLIAGE_PLACER =
            FOLIAGE_PLACER_TYPES.register("giant_sequoia_foliage_placer",
                    () -> new FoliagePlacerType<>(GiantSequoiaFoliagePlacer.CODEC));

    public static final Supplier<FoliagePlacerType<MiniSequoiaFoliagePlacer>> MINI_SEQUOIA_FOLIAGE_PLACER =
            FOLIAGE_PLACER_TYPES.register("mini_sequoia_foliage_placer",
                    () -> new FoliagePlacerType<>(MiniSequoiaFoliagePlacer.CODEC));

    public static final Supplier<FoliagePlacerType<MediumSequoiaFoliagePlacer>> MEDIUM_SEQUOIA_FOLIAGE_PLACER =
            FOLIAGE_PLACER_TYPES.register("medium_sequoia_foliage_placer",
                    () -> new FoliagePlacerType<>(MediumSequoiaFoliagePlacer.CODEC));

    public static void register(IEventBus eventBus) {
        FOLIAGE_PLACER_TYPES.register(eventBus);
    }
}
