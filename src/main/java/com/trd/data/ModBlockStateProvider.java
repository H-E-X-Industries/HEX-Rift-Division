package com.trd.data;

import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.main.MainRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, MainRegistry.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        generateAllShafts();
    }

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
}
