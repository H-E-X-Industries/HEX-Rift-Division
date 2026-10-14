package com.trd.worldgen.feature.vien;

import com.mojang.serialization.Codec;
import com.trd.main.MainRegistry;
import com.trd.worldgen.hive.HivePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Улей глубинных червей, растущий прямо в породе.
 * <p>
 * Работает только по мягким якорям (гранит, андезит, диорит, земля и прочее).
 * Обязательные якоря — рельсы, доски, каменные кирпичи, сундуки, бочки, спавнеры —
 * здесь обрабатывать бессмысленно: они приходят только из структур, а структуры
 * строятся уже ПОСЛЕ всех шагов декорации и на момент вызова фичи в мире их ещё нет.
 * Их разбирает {@link StructureHivePass}.
 */
public class DepthWormHiveFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * Мягкий якорь: породы, в которых черви селятся сами по себе.
     */
    public static final TagKey<Block> ANCHOR_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "depth_worm_hive_anchors"));

    /** Сколько колонок прочесать в поисках якоря (одна колонка = 56 чтений). */
    private static final int ANCHOR_COLUMNS = 3;

    /**
     * Дополнительное разрежение мягких якорей: 1 улей на N чанков с мягкой породой.
     */
    private static final int SOFT_ANCHOR_GATE = 3;

    public DepthWormHiveFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // 1. Find a soft rock anchor. Without one there is no hive at all: this is what
        //    keeps the feature from scanning an entire cave column per attempt, which is
        //    what overloaded chunk loading.
        BlockPos anchor = null;
        for (int attempt = 0; attempt < ANCHOR_COLUMNS && anchor == null; attempt++) {
            int cx = (origin.getX() & ~15) + random.nextInt(16);
            int cz = (origin.getZ() & ~15) + random.nextInt(16);
            anchor = HivePlacer.findAnchor(level, cx, cz, ANCHOR_TAG);
        }

        if (anchor == null) {
            return false;
        }

        if (random.nextInt(SOFT_ANCHOR_GATE) != 0) {
            return false;
        }

        return HivePlacer.place(level, level.getLevel(), anchor, random);
    }
}