package com.trd.worldgen.feature;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import com.trd.main.MainRegistry;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import java.util.List;

public class ModConfiguredFeatures {

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {

        // --- АВТО-ГЕНЕРАЦИЯ РУД (ванильная Feature.ORE) ---
        for (OreVeinRegistry.OreEntry ore : OreVeinRegistry.ORES) {
            List<OreConfiguration.TargetBlockState> targets = List.of(
                    OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), ore.block.defaultBlockState()),
                    OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), ore.block.defaultBlockState())
            );
            register(context, ore.configuredKey, Feature.ORE, new OreConfiguration(targets, ore.veinSize));
        }

        // Используем зарегистрированные экземпляры из реестра.
        // ModFeatures.FEATURES теперь зарегистрирован через MainRegistry -> .get() безопасен.
        Feature<SpecialVeinConfiguration> specialVein = ModFeatures.SPECIAL_VEIN.get();

        // --- СПЕЦИАЛЬНЫЕ ЖИЛЫ ---
        for (OreVeinRegistry.SpecialOreEntry ore : OreVeinRegistry.SPECIAL_ORES) {
            List<OreConfiguration.TargetBlockState> targets = List.of(
                    OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), ore.block.defaultBlockState()),
                    OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), ore.block.defaultBlockState())
            );
            register(context, ore.configuredKey, specialVein,
                    new SpecialVeinConfiguration(targets, ore.block.defaultBlockState(),
                            ore.minSize, ore.maxSize, ore.minY, ore.maxY,
                            ore.respectAir, ore.density, 0.15f,
                            ore.rarity, ore.maxStretch, "special_ore_" + ore.name));
        }

        // --- КОНГЛОМЕРАТЫ ---
        Feature<ConglomerateVeinConfiguration> conglomerateVein = ModFeatures.CONGLOMERATE_VEIN.get();

        for (OreVeinRegistry.ConglomerateEntry entry : OreVeinRegistry.CONGLOMERATES) {
            register(context, entry.configuredKey, conglomerateVein,
                    new ConglomerateVeinConfiguration(
                            entry.minSize, entry.maxSize, entry.minY, entry.maxY,
                            entry.density, entry.depletionChance,
                            entry.rarity, entry.maxStretch, "conglomerate_" + entry.name
                    ));
        }
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(
            BootstrapContext<ConfiguredFeature<?, ?>> context,
            ResourceKey<ConfiguredFeature<?, ?>> key,
            F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
