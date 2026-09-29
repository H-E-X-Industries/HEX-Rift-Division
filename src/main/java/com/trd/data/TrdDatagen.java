package com.trd.data;

import com.trd.main.MainRegistry;
import com.trd.worldgen.feature.ModBiomeModifiers;
import com.trd.worldgen.feature.ModConfiguredFeatures;
import com.trd.worldgen.feature.ModPlacedFeatures;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class TrdDatagen {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(
                output, lookupProvider,
                new RegistrySetBuilder()
                        .add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap)
                        .add(Registries.PLACED_FEATURE, ModPlacedFeatures::bootstrap)
                        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModBiomeModifiers::bootstrap),
                Set.of(MainRegistry.MOD_ID)
        ));

        generator.addProvider(event.includeServer(), new com.trd.datagen.recipes.ModRecipeProvider(output, lookupProvider));

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new com.trd.data.tags.ModBlockTagProvider(output, lookupProvider, event.getExistingFileHelper()));
        // В 1.21.1 BlockLootSubProvider больше не DataProvider: его надо заворачивать
        // в LootTableProvider с SubProviderEntry, отдающим HolderLookup.Provider.
        generator.addProvider(event.includeServer(), new net.minecraft.data.loot.LootTableProvider(
                output,
                java.util.Set.of(),
                java.util.List.of(new net.minecraft.data.loot.LootTableProvider.SubProviderEntry(
                        com.trd.data.loot.ModBlockLootTableProvider::new,
                        net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK)),
                lookupProvider));    }
}
