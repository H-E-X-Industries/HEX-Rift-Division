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
        // === ЕДИНАЯ СИСТЕМА НЕФТЯНЫХ МАКРО-БАССЕЙНОВ (1800x1800 блоков) ===
        // Гарантирует изоляцию 1500-2000 блоков между месторождениями,
        // спавнит гигантские глубинные пласты (2-3 блока, R=75..110),
        // 4-кратные пористые купола (R=26..40), вертикальные штоки DIAPIR (от -50 до +45)
        // и огромные поверхностные лужи в пустыне/тундре/тайге.
        register("basin_system", -64, 120, 25, 110, 1, 0.85f, 1.8f,
                VeinShape.DOME, ReservoirType.POROUS_STONE, BiomeTags.IS_OVERWORLD);
    }
}
