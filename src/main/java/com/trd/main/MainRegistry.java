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
        // Регистрация базовых компонентов
        com.trd.block.basic.ModBlocks.register(modEventBus);
        com.trd.item.ModItems.register(modEventBus);
        com.trd.main.ModCreativeTabs.register(modEventBus);
        com.trd.api.components.ModDataComponents.register(modEventBus);
        
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HEX Rift Division Setup Complete!");
    }
}
