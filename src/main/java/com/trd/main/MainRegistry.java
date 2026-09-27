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
        com.trd.item.ModItems.register(modEventBus);
        com.trd.main.ModCreativeTabs.register(modEventBus);
        com.trd.api.components.ModDataComponents.register(modEventBus);
        com.trd.api.recipe.ModRecipes.register(modEventBus);
        com.trd.api.energy.ModRecipes.register(modEventBus);
        com.trd.api.fluids.ModFluids.register(modEventBus);
        com.trd.menu.ModMenuTypes.register(modEventBus);
        com.trd.sound.ModSounds.register(modEventBus);
        com.trd.worldgen.feature.ModFeatures.FEATURES.register(modEventBus);
        
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
        });
        LOGGER.info("HEX Rift Division Setup Complete!");
    }
}
