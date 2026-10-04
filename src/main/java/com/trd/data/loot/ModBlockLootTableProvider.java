package com.trd.data.loot;

import com.trd.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.trd.block.basic.ModBlocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Генератор лут-таблиц для всех блоков мода.
 * <p>
 * В 1.20.1 это был {@code datagen/stats/ModBlockLootTableProvider} на
 * {@code RegistryObject}; в 1.21.1 холдеры — {@link DeferredHolder}, зачарования стали
 * {@code ResourceKey} (искать их надо через реестр, а не по полю), а конструктор
 * {@code BlockLootSubProvider} требует {@link HolderLookup.Provider}.
 */
public class ModBlockLootTableProvider extends net.minecraft.data.loot.BlockLootSubProvider {
    private final Set<Block> exceptions = new HashSet<>();

    // ═══════════════════════════════════════════════════════
    // СПИСОК РУД С ОПЫТОМ — единственная точка конфигурации
    // ═══════════════════════════════════════════════════════

    public record OreXpConfig(
            DeferredHolder<Block, ?> block,
            int minXp,
            int maxXp
    ) {}

    /** Руды с _ore — дают опыт при добыче без Silk Touch */
    public static final List<OreXpConfig> ORES_WITH_EXPERIENCE = List.of(
            new OreXpConfig(ModBlocks.LIGNITE_ORE,              2, 5),
            new OreXpConfig(ModBlocks.CINNABAR_ORE,             2, 5),
            new OreXpConfig(ModBlocks.CINNABAR_ORE_DEEPSLATE,   2, 5),
            new OreXpConfig(ModBlocks.SULFUR_ORE,               2, 5),
            new OreXpConfig(ModBlocks.SULFUR_ORE_DEEPSLATE,     2, 5),
            new OreXpConfig(ModBlocks.SEQUESTRUM_ORE,           2, 5),
            new OreXpConfig(ModBlocks.SEQUESTRUM_ORE_DEEPSLATE, 2, 5),
            new OreXpConfig(ModBlocks.FLUORITE_ORE,             2, 5),
            new OreXpConfig(ModBlocks.FLUORITE_ORE_DEEPSLATE,   2, 5),
            new OreXpConfig(ModBlocks.SALT_ORE,   2, 5),
            new OreXpConfig(ModBlocks.ASBESOTS_ORE,             2, 5)
    );

    // ═══════════════════════════════════════════════════════

    public ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // В 1.21.1 зачарования — ResourceKey, Holder берётся из реестра провайдера
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        var fortune = enchantments.getOrThrow(Enchantments.FORTUNE);

        // Руды с опытом — дроп 1-3 с удачей
        for (OreXpConfig xpConfig : ORES_WITH_EXPERIENCE) {
            registerOreWithMultipleDrops(xpConfig.block, getItemForOre(xpConfig.block), fortune);
        }

        // Минералы без опыта — дроп 1-3 с удачей
        registerOreWithMultipleDrops(ModBlocks.BAUXITE, ModItems.BAUXITE_CHUNK, fortune);
        registerOreWithMultipleDrops(ModBlocks.LIMESTONE, ModItems.LIMESTONE_CHUNK, fortune);
        registerOreWithMultipleDrops(ModBlocks.DOLOMITE, ModItems.DOLOMITE_CHUNK, fortune);
        registerOreWithMultipleDrops(ModBlocks.SULFUR_CLUSTER, ModItems.SULFUR, fortune);

        // Обугленное бревно: в 1.20.1 провайдер содержал charcoal+Silk Touch, НО закоммиченный
        // вывод датагена был простым dropSelf, и ForgeGradle 1.20.1 по умолчанию включал
        // src/generated/resources в ресурсы — то есть в игре отдавался именно простой дроп.
        // Поэтому WASTE_LOG НЕ добавляется в exceptions и уходит в общий dropSelf ниже:
        // так датаген даёт ровно ту таблицу, которую 1.20.1 реально отдавал в игре.
        // Чтобы вернуть charcoal-поведение из исходника провайдера 1.20.1, раскомментируй блок
        // ниже — тогда бревно начнёт рассыпаться в 1-3 угля (с Silk Touch — само бревно).
        /*
        this.add(ModBlocks.WASTE_LOG.get(), createSilkTouchDispatchTable(ModBlocks.WASTE_LOG.get(),
                this.applyExplosionDecay(ModBlocks.WASTE_LOG.get(),
                        LootItem.lootTableItem(Items.CHARCOAL)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F)))
                                .apply(ApplyBonusCount.addOreBonusCount(fortune)))));
        exceptions.add(ModBlocks.WASTE_LOG.get());
        */


        // --- ДЕФОЛТ ДЛЯ ВСЕХ ОСТАЛЬНЫХ БЛОКОВ ---
        for (DeferredHolder<Block, ? extends Block> entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (exceptions.contains(block)) continue;
            if (block == ModBlocks.BEAM_COLLISION.get() || block == ModBlocks.MULTIBLOCK_PART.get() || block == ModBlocks.PIPE_SPOTS.get()) continue;

            if (block instanceof net.minecraft.world.level.block.DoorBlock) {
                this.add(block, this::createDoorTable);
                continue;
            }

            if (block instanceof net.minecraft.world.level.block.LeavesBlock) {
                this.add(block, b -> createLeavesDrops(b, net.minecraft.world.level.block.Blocks.SPRUCE_SAPLING, NORMAL_LEAVES_SAPLING_CHANCES));
                continue;
            }

            if (block instanceof net.minecraft.world.level.block.SlabBlock) {
                this.add(block, this::createSlabItemTable);
                continue;
            }

            this.dropSelf(block);
        }
    }

    // Хелпер: получаем предмет-дроп по блоку (для руд из списка)
    private DeferredHolder<Item, ? extends Item> getItemForOre(DeferredHolder<Block, ?> block) {
        // Маппинг блок→предмет (можно сделать красивее, но для 10 руд — ок)
        if (block == ModBlocks.LIGNITE_ORE) return ModItems.LIGNITE;
        if (block == ModBlocks.CINNABAR_ORE || block == ModBlocks.CINNABAR_ORE_DEEPSLATE) return ModItems.CINNABAR;
        if (block == ModBlocks.SULFUR_ORE || block == ModBlocks.SULFUR_ORE_DEEPSLATE || block == ModBlocks.SULFUR_CLUSTER) return ModItems.SULFUR;
        if (block == ModBlocks.SEQUESTRUM_ORE || block == ModBlocks.SEQUESTRUM_ORE_DEEPSLATE) return ModItems.SEQUESTRUM;
        if (block == ModBlocks.FLUORITE_ORE || block == ModBlocks.FLUORITE_ORE_DEEPSLATE) return ModItems.FLUORITE;
        if (block == ModBlocks.ASBESOTS_ORE) return ModItems.ASBESTOS;
        if (block == ModBlocks.SALT_ORE) return ModItems.SALT;
        return ModItems.LIGNITE; // fallback
    }

    /**
     * Спавнит опыт для руд из списка ORES_WITH_EXPERIENCE.
     * Использовать при кастомной добыче (литые кирки, взрывы и т.д.),
     * т.к. BlockEvent.BreakEvent при этом не вызывается.
     */
    public static void spawnOreExperience(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        for (OreXpConfig config : ORES_WITH_EXPERIENCE) {
            if (config.block.get() == block) {
                int xp = level.random.nextIntBetweenInclusive(config.minXp, config.maxXp);
                block.popExperience(level, pos, xp);
                return;
            }
        }
    }

    // Новый метод для руд из конфига (с опытом)
    private void registerOreWithMultipleDrops(DeferredHolder<Block, ?> block,
                                              DeferredHolder<Item, ? extends Item> item,
                                              Holder<Enchantment> fortune) {
        this.add(block.get(), createSilkTouchDispatchTable(block.get(),
                this.applyExplosionDecay(block.get(),
                        LootItem.lootTableItem(item.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F)))
                                .apply(ApplyBonusCount.addOreBonusCount(fortune))
                )));
        exceptions.add(block.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(DeferredHolder::get)
                .filter(block -> block != ModBlocks.BEAM_COLLISION.get() && block != ModBlocks.MULTIBLOCK_PART.get() && block != ModBlocks.PIPE_SPOTS.get())
                .collect(Collectors.toList());
    }
}
