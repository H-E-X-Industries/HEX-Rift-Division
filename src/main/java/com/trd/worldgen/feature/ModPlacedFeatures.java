package com.trd.worldgen.feature;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;
import com.trd.main.MainRegistry;

import java.util.List;

// Размещать в: src/main/java/razchexlitiel/trd/worldgen/ModPlacedFeatures.java
public class ModPlacedFeatures {


    // 2. Сборка (DataGen)
    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        // === АВТО-РАЗМЕЩЕНИЕ РУД (вставлять СЮДА) ===
        for (OreVeinRegistry.OreEntry ore : OreVeinRegistry.ORES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(ore.configuredKey);
            register(context, ore.placedKey, configured, List.of(
                    CountPlacement.of(ore.countPerChunk),
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(
                            VerticalAnchor.absolute(ore.minY),
                            VerticalAnchor.absolute(ore.maxY)
                    ),
                    BiomeFilter.biome()
            ));
        }

        // === СПЕЦ-ЖИЛЫ: кросс-чанковая генерация ===
        // Якоря и редкость живут в ячейках региона внутри фичи (CrossChunkVeins),
        // поэтому здесь НЕ должно быть RarityFilter/InSquare/HeightRange —
        // иначе часть чанков не достроит свою порцию чужой жилы.
        for (OreVeinRegistry.SpecialOreEntry ore : OreVeinRegistry.SPECIAL_ORES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(ore.configuredKey);
            register(context, ore.placedKey, configured, List.of(
                    BiomeFilter.biome()
            ));
        }


        // Аналогично спец-жилам: вся логика размещения внутри фичи.
        for (OreVeinRegistry.ConglomerateEntry entry : OreVeinRegistry.CONGLOMERATES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(entry.configuredKey);
            register(context, entry.placedKey, configured, List.of(
                    BiomeFilter.biome()
            ));
        }
    }

    // --- Вспомогательные методы ---
    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
