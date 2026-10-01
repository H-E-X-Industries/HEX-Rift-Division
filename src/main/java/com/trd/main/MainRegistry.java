package com.trd.main;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(MainRegistry.MOD_ID)
public class MainRegistry {
    public static final String MOD_ID = "trd";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MainRegistry(IEventBus modEventBus) {
        // Инициализация динамических ресурсов металлургии (до регистрации блоков и предметов)
        com.trd.main.ResourceRegistry.init();

        // Регистрация базовых компонентов
        com.trd.block.basic.ModBlocks.register(modEventBus);
        com.trd.block.entity.ModBlockEntities.register(modEventBus);
        com.trd.entity.ModEntities.register(modEventBus);
        com.trd.fx.particle.ModExplosionParticles.PARTICLE_TYPES.register(modEventBus);
        com.trd.item.ModItems.register(modEventBus);
        com.trd.main.ModCreativeTabs.register(modEventBus);
        com.trd.api.components.ModDataComponents.register(modEventBus);
        com.trd.api.recipe.ModRecipes.register(modEventBus);
        com.trd.api.energy.ModRecipes.register(modEventBus);
        com.trd.api.fluids.ModFluids.register(modEventBus);
        com.trd.menu.ModMenuTypes.register(modEventBus);
        com.trd.sound.ModSounds.register(modEventBus);
        com.trd.worldgen.feature.ModFeatures.FEATURES.register(modEventBus);
        com.trd.worldgen.tree.custom.ModTrunkPlacerTypes.register(modEventBus);
        com.trd.worldgen.tree.custom.ModFoliagePlacerTypes.register(modEventBus);
        
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.trd.api.metallurgy.ModMetallurgy.init();
            com.trd.multiblock.industrial.drobitel.DrobitelRecipes.register();
            com.trd.multiblock.industrial.centrifuge.conus.CentrifugeRecipes.init();
            com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderRecipes.init();
            com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipes.init();
            com.trd.api.chemistry.ChemicalPlantRecipeRegistry.init();
            com.trd.multiblock.industrial.coccer.CoccerOvenRecipeRegistry.init();
            com.trd.multiblock.industrial.stanok.StanokRecipes.register();

            // TerraBlender
            terrablender.api.Regions.register(new com.trd.worldgen.biome.terrablender.ModOverworldRegion(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "overworld"), 2));
            terrablender.api.SurfaceRuleManager.addSurfaceRules(
                    terrablender.api.SurfaceRuleManager.RuleCategory.OVERWORLD,
                    MOD_ID,
                    com.trd.worldgen.biome.ModSurfaceRules.makeRules());
        });
        LOGGER.info("HEX Rift Division Setup Complete!");
    }
}
