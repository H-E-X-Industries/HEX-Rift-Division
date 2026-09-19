package com.trd.datagen.stats;

import com.trd.main.MainRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import com.trd.block.basic.ModBlocks;
import org.jetbrains.annotations.Nullable;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MainRegistry.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // --- ДЕФОЛТ: Все блоки мода ломаются железной киркой ---
        var pickaxeTag = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        var ironTag = tag(BlockTags.NEEDS_IRON_TOOL);
        var stoneTag = tag(BlockTags.NEEDS_STONE_TOOL);

        ModBlocks.BLOCKS.getEntries().forEach(block -> {
            // Если ты НЕ добавил блок в исключения ниже, он попадает сюда
            pickaxeTag.add(block.get());
            ironTag.add(block.get());
        });

        // --- ЖИЛЫ И РУДЫ НЕМЕТАЛЛОВ (ломаются каменной киркой) ---
        Set<net.minecraft.world.level.block.Block> stoneToolBlocks = Set.of(
                ModBlocks.BAUXITE.get(),
                ModBlocks.DOLOMITE.get(),
                ModBlocks.LIMESTONE.get(),
                ModBlocks.ASBESOTS_ORE.get(),
                ModBlocks.CINNABAR_ORE.get(),
                ModBlocks.CINNABAR_ORE_DEEPSLATE.get(),
                ModBlocks.COLTAN_ORE.get(),
                ModBlocks.LIGNITE_ORE.get(),
                ModBlocks.FLUORITE_ORE.get(),
                ModBlocks.FLUORITE_ORE_DEEPSLATE.get(),
                ModBlocks.RAREGROUND_ORE.get(),
                ModBlocks.RAREGROUND_ORE_DEEPSLATE.get(),
                ModBlocks.SEQUESTRUM_ORE.get(),
                ModBlocks.SEQUESTRUM_ORE_DEEPSLATE.get(),
                ModBlocks.SULFUR_ORE.get(),
                ModBlocks.SULFUR_ORE_DEEPSLATE.get(),
                ModBlocks.SULFUR_CLUSTER.get(),
                ModBlocks.SALT_ORE.get()
        );

        stoneToolBlocks.forEach(block -> {
            ironTag.remove(block);
            stoneTag.add(block);
        });

        // --- ИСКЛЮЧЕНИЯ (Например, топор или лопата) ---
        // tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WASTE_LOG.get());
        // tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.WASTE_DIRT.get());

        this.tag(BlockTags.LOGS)
                .add(ModBlocks.SEQUOIA_HEARTWOOD.get())
                .add(ModBlocks.SEQUOIA_BARK.get())
                .add(ModBlocks.SEQUOIA_BARK_MOSSY.get())
                .add(ModBlocks.SEQUOIA_BARK_DARK.get())
                .add(ModBlocks.BARBED_WIRE.get())
                .add(ModBlocks.SEQUOIA_BARK_LIGHT.get());

        // Опционально: если хочешь, чтобы они горели в печке и от огня
        this.tag(BlockTags.LOGS_THAT_BURN)
                .add(ModBlocks.SEQUOIA_HEARTWOOD.get());

        this.tag(BlockTags.DIRT)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        this.tag(BlockTags.LEAVES)
                .add(ModBlocks.SEQUOIA_LEAVES.get());

        this.tag(BlockTags.MINEABLE_WITH_HOE)
                .add(ModBlocks.SEQUOIA_LEAVES.get());

    }
}