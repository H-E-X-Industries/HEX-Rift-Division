package com.trd.worldgen.feature;

import com.trd.main.MainRegistry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_CONGLOMERATE_VEIN =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "add_conglomerate_vein"));

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var biomes = context.lookup(Registries.BIOME);

        // === КОНГЛОМЕРАТЫ ===
        for (OreVeinRegistry.ConglomerateEntry entry : OreVeinRegistry.CONGLOMERATES) {
            context.register(entry.biomeModifierKey,
                    new BiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(entry.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }

        // === АВТО-ДОБАВЛЕНИЕ РУД В БИОМЫ ===
        for (OreVeinRegistry.OreEntry ore : OreVeinRegistry.ORES) {
            context.register(ore.biomeModifierKey,
                    new BiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(ore.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }

        // === СПЕЦ-ЖИЛЫ в биомы ===
        for (OreVeinRegistry.SpecialOreEntry ore : OreVeinRegistry.SPECIAL_ORES) {
            context.register(ore.biomeModifierKey,
                    new BiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(ore.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }
    }
}
