package com.trd.data;

import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.main.MainRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, MainRegistry.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        generateAllShafts();
        generateBuildingBlocks();
    }

    // ═══════════════════════════════════════════════════════
    // ВАЛЫ
    // ═══════════════════════════════════════════════════════

    public void generateAllShafts() {
        for (DeferredBlock<Block> blockObj : ModBlocks.ALL_SHAFTS) {
            if (blockObj.get() instanceof ShaftBlock shaft) {
                String name = blockObj.getId().getPath();
                ResourceLocation objModel = modLoc("models/block/shaft_" + shaft.getDiameter().name + ".obj");
                ResourceLocation texture = modLoc("block/" + name);

                // 1. Model of the block
                ModelFile blockModel = models().getBuilder(name)
                        .customLoader(ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("shaft_texture", texture)
                        .texture("particle", texture);

                // Blockstate with FACING
                directionalBlock(blockObj.get(), blockModel);

                // 2. Item model
                itemModels().getBuilder(name)
                        .parent(new ModelFile.UncheckedModelFile(modLoc("item/shaft_template")))
                        .customLoader(ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("shaft_texture", texture)
                        .texture("particle", texture);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // СТРОИТЕЛЬНЫЕ БЛОКИ
    //
    // BEAM_BLOCK / STEEL_PROPS / ROUND_LAMP / WIRE_FENCE / WIRE_FENCE_ALT / BARBED_WIRE
    // намеренно НЕ генерируются: их blockstate и модели (obj / multipart) написаны руками.
    // ═══════════════════════════════════════════════════════

    public void generateBuildingBlocks() {
        // Дерево
        cubeAllWithItem(ModBlocks.SEQUOIA_PLANKS);
        cubeAllWithItem(ModBlocks.WASTE_PLANKS);

        // Бетонная линейка
        cubeAllWithItem(ModBlocks.CONCRETE);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_NEW);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_OLD);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE_ALT);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE_ALT_BLUE);
        cubeAllWithItem(ModBlocks.CONCRETE_STRIPPED);
        cubeAllWithItem(ModBlocks.CONCRETE_REINFORCED);
        cubeAllWithItem(ModBlocks.CONCRETE_REINFORCED_HEAVY);
        cubeAllWithItem(ModBlocks.CONCRETE_MOSSY);
        cubeAllWithItem(ModBlocks.CONCRETE_OLD);
        cubeAllWithItem(ModBlocks.CONCRETE_NET);
        cubeAllWithItem(ModBlocks.CONCRETE_REBAR);
        cubeAllWithItem(ModBlocks.CONCRETE_CONSTRUCT_BLOCK);
        cubeAllWithItem(ModBlocks.STEEL_CONSTRUCT_BLOCK);
        cubeAllWithItem(ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED);

        // Боковые панели: своя текстура по бокам, обычный бетон сверху и снизу
        columnBlockWithItem(ModBlocks.CONCRETE_LINE);
        columnBlockWithItem(ModBlocks.CONCRETE_CUT);
        columnBlockWithItem(ModBlocks.CONCRETE_RAIL);
        columnBlockWithItem(ModBlocks.CONCRETE_VENT);

        // Минералы / плитка
        cubeAllWithItem(ModBlocks.MINERAL_BLOCK2);
        cubeAllWithItem(ModBlocks.MINERAL_TILE);
        cubeAllWithItem(ModBlocks.DOLOMITE_TILE);
        cubeAllWithItem(ModBlocks.TILE_LIGHT);
        cubeAllWithItem(ModBlocks.SULFUR_TILE);
        cubeAllWithItem(ModBlocks.SULFUR_BRICKS);

        // Металлы / декор
        cubeAllWithItem(ModBlocks.DECO_STEEL);
        cubeAllWithItem(ModBlocks.DECO_STEEL_DARK);
        cubeAllWithItem(ModBlocks.DECO_STEEL_SMOG);
        cubeAllWithItem(ModBlocks.DECO_LEAD);
        columnBlockWithItem(ModBlocks.DECO_BEAM, modLoc("block/deco_beam_side"), modLoc("block/deco_beam_top"));

        // Стекло
        cutoutBlockWithItem(ModBlocks.ARMORED_GLASS);

        // Плиты и лестницы
        stairsAndSlabs(ModBlocks.CONCRETE, ModBlocks.CONCRETE_STAIRS, ModBlocks.CONCRETE_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_MOSSY, ModBlocks.CONCRETE_MOSSY_STAIRS, ModBlocks.CONCRETE_MOSSY_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_OLD, ModBlocks.CONCRETE_OLD_STAIRS, ModBlocks.CONCRETE_OLD_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_NEW, ModBlocks.CONCRETE_HAZARD_NEW_STAIRS, ModBlocks.CONCRETE_HAZARD_NEW_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_OLD, ModBlocks.CONCRETE_HAZARD_OLD_STAIRS, ModBlocks.CONCRETE_HAZARD_OLD_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_TILE, ModBlocks.CONCRETE_TILE_STAIRS, ModBlocks.CONCRETE_TILE_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_TILE_ALT, ModBlocks.CONCRETE_TILE_ALT_STAIRS, ModBlocks.CONCRETE_TILE_ALT_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_TILE_ALT_BLUE, ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS, ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_STRIPPED, ModBlocks.CONCRETE_STRIPPED_STAIRS, ModBlocks.CONCRETE_STRIPPED_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_REINFORCED, ModBlocks.CONCRETE_REINFORCED_STAIRS, ModBlocks.CONCRETE_REINFORCED_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_REINFORCED_HEAVY, ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS, ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB);
        stairsAndSlabs(ModBlocks.CONCRETE_CONSTRUCT_BLOCK, ModBlocks.CONCRETE_CONSTRUCT_BLOCK_STAIRS, ModBlocks.CONCRETE_CONSTRUCT_BLOCK_SLAB);
        stairsAndSlabs(ModBlocks.STEEL_CONSTRUCT_BLOCK, ModBlocks.STEEL_CONSTRUCT_BLOCK_STAIRS, ModBlocks.STEEL_CONSTRUCT_BLOCK_SLAB);
        stairsAndSlabs(ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED, ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_STAIRS, ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_SLAB);
        stairsAndSlabs(ModBlocks.SEQUOIA_PLANKS, ModBlocks.SEQUOIA_STAIRS, ModBlocks.SEQUOIA_SLAB);
        stairsAndSlabs(ModBlocks.WASTE_PLANKS, ModBlocks.WASTE_PLANKS_STAIRS, ModBlocks.WASTE_PLANKS_SLAB);
        stairsAndSlabs(ModBlocks.FIREBRICK_BLOCK, ModBlocks.FIREBRICK_STAIRS, ModBlocks.FIREBRICK_SLAB);
        stairsAndSlabs(ModBlocks.REINFORCEDBRICK_BLOCK, ModBlocks.REINFORCEDBRICK_STAIRS, ModBlocks.REINFORCEDBRICK_SLAB);

        // Забор, калитка, нажимная пластина — на текстуре WASTE_PLANKS
        ResourceLocation wasteTexture = blockTexture(ModBlocks.WASTE_PLANKS.get());

        fenceBlock((FenceBlock) ModBlocks.WASTE_FENCE.get(), wasteTexture);
        simpleBlockItem(ModBlocks.WASTE_FENCE.get(), models().fenceInventory("waste_fence_inventory", wasteTexture));

        fenceGateBlock((FenceGateBlock) ModBlocks.WASTE_FENCE_GATE.get(), wasteTexture);
        simpleBlockItem(ModBlocks.WASTE_FENCE_GATE.get(), models().getExistingFile(modLoc("block/waste_fence_gate")));

        pressurePlateBlock((PressurePlateBlock) ModBlocks.WASTE_PRESSURE_PLATE.get(), wasteTexture);
        simpleBlockItem(ModBlocks.WASTE_PRESSURE_PLATE.get(), models().getExistingFile(modLoc("block/waste_pressure_plate")));

        // Двери (модель предмета — ручная: item/handheld + layer0)
        doorBlockWithRenderType((DoorBlock) ModBlocks.SEQUOIA_DOOR.get(),
                modLoc("block/sequoia_door_bottom"), modLoc("block/sequoia_door_top"), "cutout");
        doorBlockWithRenderType((DoorBlock) ModBlocks.STEEL_DOOR.get(),
                modLoc("block/steel_door_bottom"), modLoc("block/steel_door_top"), "cutout");
    }

    // ═══════════════════════════════════════════════════════
    // ХЕЛПЕРЫ
    // ═══════════════════════════════════════════════════════

    /**
     * Полный куб с одной текстурой + модель предмета.
     */
    public void cubeAllWithItem(DeferredHolder<Block, ? extends Block> block) {
        simpleBlockWithItem(block.get(), cubeAll(block.get()));
    }

    /**
     * Полный блок + его плита и лестница.
     * У плиты вариант {@code type=double} ссылается на модель полного куба.
     */
    public void stairsAndSlabs(DeferredHolder<Block, ? extends Block> fullBlock,
                               DeferredHolder<Block, ? extends StairBlock> stairs,
                               DeferredHolder<Block, ? extends SlabBlock> slab) {
        ResourceLocation texture = blockTexture(fullBlock.get());
        stairsBlock((StairBlock) stairs.get(), texture);
        slabBlock((SlabBlock) slab.get(), texture, texture);

        simpleBlockItem(stairs.get(), models().getExistingFile(modLoc("block/" + stairs.getId().getPath())));
        simpleBlockItem(slab.get(), models().getExistingFile(modLoc("block/" + slab.getId().getPath())));
    }

    /**
     * Блок с отдельной текстурой по бокам и переиспользуемой сверху/снизу.
     */
    public void columnBlockWithItem(DeferredHolder<Block, ? extends Block> block) {
        columnBlockWithItem(block, modLoc("block/" + block.getId().getPath()), blockTexture(ModBlocks.CONCRETE.get()));
    }

    /**
     * Блок с отдельной текстурой по бокам и заданной сверху/снизу.
     */
    public void columnBlockWithItem(DeferredHolder<Block, ? extends Block> block, ResourceLocation side, ResourceLocation cap) {
        String name = block.getId().getPath();
        simpleBlock(block.get(), models().cubeBottomTop(name, side, cap, cap));
        // Модель предмета парентится на сгенерированную модель блока, а не на текстуру бока
        simpleBlockItem(block.get(), models().getExistingFile(modLoc("block/" + name)));
    }

    /**
     * Полный куб + модель предмета (вызывает simpleBlock, который сам создаёт cube_all).
     */
    public void blockWithItem(DeferredHolder<Block, ? extends Block> block) {
        simpleBlock(block.get());
        simpleBlockItem(block.get(), models().getExistingFile(blockTexture(block.get())));
    }

    /**
     * Полный куб с вырезом: прозрачные пиксели требуют cutout-слоя.
     * AO выключен — иначе нижняя грань блока, стоящего на земле, уходит в темноту.
     */
    public void cutoutBlockWithItem(DeferredHolder<Block, ? extends Block> block) {
        ModelFile model = models().cubeAll(block.getId().getPath(), blockTexture(block.get()))
                .ao(false)
                .renderType("cutout");
        simpleBlockWithItem(block.get(), model);
    }
}
