package com.trd.datagen.stats;

import com.trd.block.basic.ModBlocks;
import com.trd.main.MainRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Раздаёт блокам мода теги инструментов.
 * <p>
 * Механика 1.21.1: у кирки в компоненте {@code Tool} два правила — «не роняет
 * блоки из needs_X_tool» и «роет и роняет всё из mineable/pickaxe». Первое
 * правило идёт первым, поэтому тег {@code needs_*_tool} задаёт МИНИМАЛЬНЫЙ тир.
 * Без этих тегов блок с {@code requiresCorrectToolForDrops()} не выпадает вовсе.
 * <p>
 * Политика тиров:
 * <ul>
 *   <li>по умолчанию — каменная кирка (needs_stone_tool): руды, камень, плитка;</li>
 *   <li>вся бетонная линия, металлический декор и прочие стальные блоки — железная;</li>
 *   <li>прочность ≥ 10 (арматура, стальные рамы, стальная дверь, бетонный блок) — алмазная;</li>
 *   <li>прочность ≥ 20 (усиленная стальная рама, 30.0/380.0) — незеритовая.</li>
 * </ul>
 */
public class ModBlockTagProvider extends BlockTagsProvider {

    /** Блоки, которые копаются топором. */
    private static final Set<Block> AXE_BLOCKS = Set.of(
            ModBlocks.WASTE_PLANKS.get(),
            ModBlocks.WASTE_PLANKS_STAIRS.get(),
            ModBlocks.WASTE_PLANKS_SLAB.get(),
            ModBlocks.WASTE_FENCE.get(),
            ModBlocks.WASTE_FENCE_GATE.get(),
            ModBlocks.WASTE_PRESSURE_PLATE.get(),
            ModBlocks.SEQUOIA_PLANKS.get(),
            ModBlocks.SEQUOIA_SLAB.get(),
            ModBlocks.SEQUOIA_STAIRS.get(),
            ModBlocks.SEQUOIA_DOOR.get(),
            ModBlocks.SEQUOIA_TRAPDOOR.get(),
            ModBlocks.SEQUOIA_BARK.get(),
            ModBlocks.SEQUOIA_BARK_MOSSY.get(),
            ModBlocks.SEQUOIA_BARK_DARK.get(),
            ModBlocks.SEQUOIA_BARK_LIGHT.get(),
            ModBlocks.SEQUOIA_HEARTWOOD.get(),
            ModBlocks.SEQUOIA_ROOTS.get(),
            ModBlocks.SEQUOIA_ROOTS_MOSSY.get()
    );

    /** Киркой уровня алмаза: прочность от 10. */
    private static final Set<Block> DIAMOND_BLOCKS = Set.of(
            ModBlocks.CONCRETE_CONSTRUCT_BLOCK.get(),
            ModBlocks.CONCRETE_CONSTRUCT_BLOCK_SLAB.get(),
            ModBlocks.CONCRETE_CONSTRUCT_BLOCK_STAIRS.get(),
            ModBlocks.STEEL_CONSTRUCT_BLOCK.get(),
            ModBlocks.STEEL_CONSTRUCT_BLOCK_SLAB.get(),
            ModBlocks.STEEL_CONSTRUCT_BLOCK_STAIRS.get(),
            ModBlocks.CONCRETE_REBAR.get(),
            ModBlocks.STEEL_DOOR.get()
    );

    /** Киркой уровня незерита: прочность от 20. */
    private static final Set<Block> NETHERITE_BLOCKS = Set.of(
            ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED.get(),
            ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_SLAB.get(),
            ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_STAIRS.get()
    );

    /**
     * Киркой уровня железной: вся бетонная линия, металлический декор,
     * бронированное стекло, ограждения и прочие «крепкие» блоки.
     */
    private static final Set<Block> IRON_BLOCKS = Set.of(
            // бетон
            ModBlocks.CONCRETE.get(),
            ModBlocks.CONCRETE_SLAB.get(),
            ModBlocks.CONCRETE_STAIRS.get(),
            ModBlocks.CONCRETE_OLD.get(),
            ModBlocks.CONCRETE_OLD_SLAB.get(),
            ModBlocks.CONCRETE_OLD_STAIRS.get(),
            ModBlocks.CONCRETE_MOSSY.get(),
            ModBlocks.CONCRETE_MOSSY_SLAB.get(),
            ModBlocks.CONCRETE_MOSSY_STAIRS.get(),
            ModBlocks.CONCRETE_HAZARD_NEW.get(),
            ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(),
            ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(),
            ModBlocks.CONCRETE_HAZARD_OLD.get(),
            ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(),
            ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(),
            ModBlocks.CONCRETE_TILE.get(),
            ModBlocks.CONCRETE_TILE_SLAB.get(),
            ModBlocks.CONCRETE_TILE_STAIRS.get(),
            ModBlocks.CONCRETE_TILE_ALT.get(),
            ModBlocks.CONCRETE_TILE_ALT_SLAB.get(),
            ModBlocks.CONCRETE_TILE_ALT_STAIRS.get(),
            ModBlocks.CONCRETE_TILE_ALT_BLUE.get(),
            ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get(),
            ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get(),
            ModBlocks.CONCRETE_STRIPPED.get(),
            ModBlocks.CONCRETE_STRIPPED_SLAB.get(),
            ModBlocks.CONCRETE_STRIPPED_STAIRS.get(),
            ModBlocks.CONCRETE_REINFORCED.get(),
            ModBlocks.CONCRETE_REINFORCED_SLAB.get(),
            ModBlocks.CONCRETE_REINFORCED_STAIRS.get(),
            ModBlocks.CONCRETE_REINFORCED_HEAVY.get(),
            ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get(),
            ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get(),
            ModBlocks.CONCRETE_IMPACT_RESISTANT.get(),
            ModBlocks.CONCRETE_LINE.get(),
            ModBlocks.CONCRETE_CUT.get(),
            ModBlocks.CONCRETE_RAIL.get(),
            ModBlocks.CONCRETE_VENT.get(),
            ModBlocks.ARMORED_GLASS.get(),
            // кирпичи
            ModBlocks.FIREBRICK_BLOCK.get(),
            ModBlocks.FIREBRICK_SLAB.get(),
            ModBlocks.FIREBRICK_STAIRS.get(),
            ModBlocks.REINFORCEDBRICK_BLOCK.get(),
            ModBlocks.REINFORCEDBRICK_SLAB.get(),
            ModBlocks.REINFORCEDBRICK_STAIRS.get(),
            // металл / декор
            ModBlocks.DECO_STEEL.get(),
            ModBlocks.DECO_STEEL_DARK.get(),
            ModBlocks.DECO_STEEL_SMOG.get(),
            ModBlocks.DECO_LEAD.get(),
            ModBlocks.DECO_BEAM.get(),
            ModBlocks.BEAM_BLOCK.get(),
            ModBlocks.STEEL_PROPS.get(),
            // ограждения и проволока
            ModBlocks.BARBED_WIRE.get(),
            ModBlocks.WIRE_FENCE.get(),
            ModBlocks.WIRE_FENCE_ALT.get(),
            ModBlocks.ROUND_LAMP.get(),
            // прочее
            ModBlocks.MINERAL_BLOCK2.get(),
            ModBlocks.BEARING_BLOCK.get(),
            ModBlocks.CLUTCH.get(),
            ModBlocks.MOTOR_ELECTRO.get(),
            ModBlocks.TACHOMETER.get()
    );

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MainRegistry.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        var pickaxeTag = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        var stoneTag = tag(BlockTags.NEEDS_STONE_TOOL);
        var ironTag = tag(BlockTags.NEEDS_IRON_TOOL);
        var diamondTag = tag(BlockTags.NEEDS_DIAMOND_TOOL);
        // Отдельного needs_netherite_tool в 1.21.1 нет: Tiers.NETHERITE в своём
        // incorrectBlocksForDrops ссылается на incorrect_for_netherite_tool.
        var netheriteTag = tag(BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
        var axeTag = tag(BlockTags.MINEABLE_WITH_AXE);

        // Дефолт — каменная кирка. Исключения проверяются здесь, а не через
        // TagBuilder#remove: в формате тега {replace, values} записи удаления нет,
        // и блок остался бы одновременно в mineable/pickaxe и в needs_iron_tool,
        // а значит ломался бы железной киркой.
        ModBlocks.BLOCKS.getEntries().forEach(entry -> {
            Block block = entry.get();

            if (block instanceof net.minecraft.world.level.block.LeavesBlock) {
                return;
            }

            if (AXE_BLOCKS.contains(block)) {
                axeTag.add(block);
                return;
            }
            if (NETHERITE_BLOCKS.contains(block)) {
                pickaxeTag.add(block);
                netheriteTag.add(block);
                return;
            }
            if (DIAMOND_BLOCKS.contains(block)) {
                pickaxeTag.add(block);
                diamondTag.add(block);
                return;
            }
            if (IRON_BLOCKS.contains(block)) {
                pickaxeTag.add(block);
                ironTag.add(block);
                return;
            }
            // каменная кирка
            pickaxeTag.add(block);
            stoneTag.add(block);
        });

        tag(BlockTags.LEAVES)
                .add(ModBlocks.SEQUOIA_LEAVES.get());

        tag(BlockTags.LOGS)
                .add(ModBlocks.SEQUOIA_HEARTWOOD.get())
                .add(ModBlocks.SEQUOIA_BARK.get())
                .add(ModBlocks.SEQUOIA_BARK_MOSSY.get())
                .add(ModBlocks.SEQUOIA_BARK_DARK.get())
                .add(ModBlocks.SEQUOIA_BARK_LIGHT.get())
                .add(ModBlocks.SEQUOIA_ROOTS.get())
                .add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get());

        tag(BlockTags.LOGS_THAT_BURN)
                .add(ModBlocks.SEQUOIA_HEARTWOOD.get());

        tag(BlockTags.DIRT)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        tag(BlockTags.ANIMALS_SPAWNABLE_ON)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        tag(BlockTags.WOLVES_SPAWNABLE_ON)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        tag(BlockTags.FOXES_SPAWNABLE_ON)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        tag(BlockTags.RABBITS_SPAWNABLE_ON)
                .add(ModBlocks.SEQUOIA_BIOME_MOSS.get());

        tag(BlockTags.FENCES)
                .add(ModBlocks.WIRE_FENCE.get())
                .add(ModBlocks.WIRE_FENCE_ALT.get())
                .add(ModBlocks.WASTE_FENCE.get());

        tag(BlockTags.FENCE_GATES)
                .add(ModBlocks.WASTE_FENCE_GATE.get());

        // FenceBlock#isSameFence требует попадания в fences И совпадения флага
        // wooden_fences у обоих заборов, поэтому мусорный забор обязан быть здесь.
        tag(BlockTags.WOODEN_FENCES)
                .add(ModBlocks.WASTE_FENCE.get());

        // К каким блокам проволочный забор «пускает» сетку.
        // Семейство одно: обычный и сеточный заборы стыкуются друг с другом
        // и со стойками, плюс калитка на waste-досках.
        tag(TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "wire_fence_connections")))
                .add(ModBlocks.WIRE_FENCE.get())
                .add(ModBlocks.WIRE_FENCE_ALT.get())
                .add(ModBlocks.WASTE_FENCE_GATE.get())
                .add(ModBlocks.STEEL_PROPS.get());

tag(TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "wire_fence_alt_connections")))
                .add(ModBlocks.WIRE_FENCE_ALT.get())
                .add(ModBlocks.WIRE_FENCE.get())
                .add(ModBlocks.WASTE_PRESSURE_PLATE.get())
                .add(ModBlocks.STEEL_PROPS.get());

// === УЛЬЯ ГЛУБИННЫХ ЧЕРВЕЙ: ОБЯЗАТЕЛЬНЫЕ ЯКОРЯ ===
        // Рельсы, доски, каменные кирпичи, сундуки, бочки, спавнеры. Возле такого
        // блока улей строится на полной частоте (шанс 1 из 4 чанков, в которых
        // блок реально есть), поэтому улей почти всегда рядом с шахтой/крепостью,
        // но и не тридцать штук в одной шахте.
        // Структуры других модов добавляются сюда датапаком одной записью.
        tag(com.trd.worldgen.hive.StructureHivePass.REQUIRED_ANCHOR_TAG)
                // рельсы — заброшенные шахты
                .add(net.minecraft.world.level.block.Blocks.RAIL)
                .add(net.minecraft.world.level.block.Blocks.POWERED_RAIL)
                .add(net.minecraft.world.level.block.Blocks.DETECTOR_RAIL)
                // доски — шахты, крепости, деревни
                .add(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.JUNGLE_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.ACACIA_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.MANGROVE_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.CHERRY_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.BAMBOO_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.CRIMSON_PLANKS)
                .add(net.minecraft.world.level.block.Blocks.WARPED_PLANKS)
                // каменные кирпичи — шахты, крепости, подземелья
                .add(net.minecraft.world.level.block.Blocks.STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_CRACKED_STONE_BRICKS)
                // хранилища
                .add(net.minecraft.world.level.block.Blocks.CHEST)
                .add(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)
                .add(net.minecraft.world.level.block.Blocks.BARREL)
                // спавнеры — крепости, древние города
                .add(net.minecraft.world.level.block.Blocks.SPAWNER);

        // === УЛЬЯ ГЛУБИННЫХ ЧЕРВЕЙ: МЯГКИЕ ЯКОРЯ ===
        // Порода и оформление, в которых черви селятся сами по себе. Улей здесь
        // дополнительно разрежается в SOFT_ANCHOR_GATE раз, иначе мягкая порода,
        // которой в пещерах полно, давала бы улей чуть ли не в каждом чанке.
        // Скалк сюда намеренно НЕ входит: его слишком много в древних городах.
        tag(com.trd.worldgen.feature.vien.DepthWormHiveFeature.ANCHOR_TAG)
                // мягкие породы — в пещерах и копаных проходах
                .add(net.minecraft.world.level.block.Blocks.GRANITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_GRANITE)
                .add(net.minecraft.world.level.block.Blocks.ANDESITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE)
                .add(net.minecraft.world.level.block.Blocks.DIORITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE)
                .add(net.minecraft.world.level.block.Blocks.DIRT)
                .add(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                .add(net.minecraft.world.level.block.Blocks.GRAVEL)
                .add(net.minecraft.world.level.block.Blocks.TUFF)
                .add(net.minecraft.world.level.block.Blocks.CALCITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS)
                // оформление структур
                .add(net.minecraft.world.level.block.Blocks.SCAFFOLDING)
                .add(net.minecraft.world.level.block.Blocks.LADDER)
                .add(net.minecraft.world.level.block.Blocks.TORCH)
                .add(net.minecraft.world.level.block.Blocks.SOUL_TORCH)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE_STAIRS)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE_SLAB)
                .add(net.minecraft.world.level.block.Blocks.COBWEB)
                .add(net.minecraft.world.level.block.Blocks.BOOKSHELF)
                .add(net.minecraft.world.level.block.Blocks.LODESTONE)
                // древние города
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_DEEPSLATE)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_TILES)
// триальные залы
                .add(net.minecraft.world.level.block.Blocks.CHISELED_TUFF_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.HEAVY_CORE);

        // === УЛЬЯ ГЛУБИННЫХ ЧЕРВЕЙ: ЧТО УЛЕЙ ВПРАВЕ ЗАМЕНИТЬ ===
        // Белый список, потолок прочности — дипслейт и кирпичи из него. Руды, обсидиан,
        // древний мусор, укреплённый дипслейт, спавнеры, сундуки и любые блоки других
        // модов сюда НЕ входят: улей на таком полу просто не построится.
        // Свои блоки мод открывает сюда датапаком.
        tag(com.trd.worldgen.hive.HivePlacer.REPLACEABLE_TAG)
                // камень и интрузивные породы
                .add(net.minecraft.world.level.block.Blocks.STONE)
                .add(net.minecraft.world.level.block.Blocks.GRANITE)
                .add(net.minecraft.world.level.block.Blocks.ANDESITE)
                .add(net.minecraft.world.level.block.Blocks.DIORITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_GRANITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE)
                .add(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)
                .add(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE)
                // каменные кирпичи — в том числе заражённые: они выглядят как камень
                // и ничего ценного не содержат
                .add(net.minecraft.world.level.block.Blocks.STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.MOSSY_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_MOSSY_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_CRACKED_STONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_CHISELED_STONE_BRICKS)
                // дипслейт и кирпичи из него — верхняя граница прочности
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE)
                .add(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE)
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_TILES)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_DEEPSLATE)
                .add(net.minecraft.world.level.block.Blocks.INFESTED_DEEPSLATE)
                // туф, кальцит, грязь
                .add(net.minecraft.world.level.block.Blocks.TUFF)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_TUFF)
                .add(net.minecraft.world.level.block.Blocks.TUFF_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_TUFF_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CALCITE)
                .add(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)
                .add(net.minecraft.world.level.block.Blocks.DIRT)
                .add(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                .add(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)
                .add(net.minecraft.world.level.block.Blocks.GRAVEL)
                .add(net.minecraft.world.level.block.Blocks.CLAY)
                .add(net.minecraft.world.level.block.Blocks.MUD)
                .add(net.minecraft.world.level.block.Blocks.PACKED_MUD)
                // чёрный камень и базальт — бастионы оверворлда, мягкие
                .add(net.minecraft.world.level.block.Blocks.BLACKSTONE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_POLISHED_BLACKSTONE)
                .add(net.minecraft.world.level.block.Blocks.BASALT)
                .add(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT)
                .add(net.minecraft.world.level.block.Blocks.POLISHED_BASALT)
                .add(net.minecraft.world.level.block.Blocks.NETHERRACK)
                // песок (подземелья в засушливых биомах)
                .add(net.minecraft.world.level.block.Blocks.SAND)
                .add(net.minecraft.world.level.block.Blocks.RED_SAND)
                .add(net.minecraft.world.level.block.Blocks.SANDSTONE)
                .add(net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE)
                .add(net.minecraft.world.level.block.Blocks.CHISELED_SANDSTONE);
    }
}
