package com.trd.worldgen.feature.oil;

import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class OilVeinRegistry {

    public static final List<OilVeinEntry> OIL_VEINS = new ArrayList<>();

    public static class OilVeinEntry {
        public final String name;
        public final int minY, maxY;
        public final int minSize, maxSize;
        public final int rarity;
        public final float density;
        public final float maxStretch;
        public final VeinShape shape;
        public final ReservoirType reservoirType;
        public final TagKey<Biome> targetBiomeTag;

        public final ResourceKey<ConfiguredFeature<?, ?>> configuredKey;
        public final ResourceKey<PlacedFeature> placedKey;
        public final ResourceKey<BiomeModifier> biomeModifierKey;

        public OilVeinEntry(String name, int minY, int maxY, int minSize, int maxSize,
                            int rarity, float density, float maxStretch,
                            VeinShape shape, ReservoirType reservoirType,
                            TagKey<Biome> targetBiomeTag) {
            this.name = name;
            this.minY = minY;
            this.maxY = maxY;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.rarity = rarity;
            this.density = density;
            this.maxStretch = maxStretch;
            this.shape = shape;
            this.reservoirType = reservoirType;
            this.targetBiomeTag = targetBiomeTag;

            this.configuredKey = ResourceKey.create(
                    Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "oil_" + name)
            );
            this.placedKey = ResourceKey.create(
                    Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "oil_" + name + "_placed")
            );
            this.biomeModifierKey = ResourceKey.create(
                    NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                    ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "add_oil_" + name)
            );
        }
    }

    public static void register(String name, int minY, int maxY, int minSize, int maxSize,
                                int rarity, float density, float maxStretch,
                                VeinShape shape, ReservoirType reservoirType,
                                TagKey<Biome> targetBiomeTag) {
        OIL_VEINS.add(new OilVeinEntry(name, minY, maxY, minSize, maxSize, rarity, density, maxStretch, shape, reservoirType, targetBiomeTag));
    }

    static {
        // === ПО ВЕР Х Н О С Т Ь (только лужи, в 20 раз реже конгломератов, V=10x) ===
        // Пустыни: заменяют только песок -> oil_sand
        register("surface_desert", 40, 120, 30, 45, 200, 0.85f, 1.5f,
                VeinShape.PUDDLE, ReservoirType.TAR_SANDS, Tags.Biomes.IS_DESERT);

        // Тундра / Снежные биомы: заменяют только землю -> oil_dirt
        register("surface_tundra", 40, 120, 30, 45, 200, 0.85f, 1.5f,
                VeinShape.PUDDLE, ReservoirType.TAR_SANDS, Tags.Biomes.IS_SNOWY);

        // === С Р Е Д Н И Е (Y = 0..40, пористый песчаник porous_oil_stone, купола) ===
        register("medium", 0, 40, 11, 19, 280, 0.75f, 2.2f,
                VeinShape.DOME, ReservoirType.POROUS_STONE, BiomeTags.IS_OVERWORLD);

        // Океанический бонус: в океанах средние залежи спавнятся значительно чаще!
        register("medium_ocean", 0, 40, 11, 19, 90, 0.75f, 2.2f,
                VeinShape.DOME, ReservoirType.POROUS_STONE, BiomeTags.IS_OCEAN);

        // === Г Л У Б О К И Е (Y = -64..0, пласты толщиной 2-3 блока в дипслейте deepslate_oil_shale) ===
        register("deep", -64, 0, 35, 55, 360, 0.85f, 1.8f,
                VeinShape.STRATIFIED, ReservoirType.TIGHT_SHALE, BiomeTags.IS_OVERWORLD);

        // Океанический бонус: в океанах глубокие пласты спавнятся значительно чаще!
        register("deep_ocean", -64, 0, 35, 55, 120, 0.85f, 1.8f,
                VeinShape.STRATIFIED, ReservoirType.TIGHT_SHALE, BiomeTags.IS_OCEAN);
    }
}
