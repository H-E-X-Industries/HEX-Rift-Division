package com.trd.client;

import com.trd.block.entity.ModBlockEntities;
import com.trd.client.render.flywheel.MillstoneVisual;
import com.trd.client.render.flywheel.ModModels;
import com.trd.main.MainRegistry;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModClientSetup {

@SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.trd.menu.ModMenuTypes.MACHINE_BATTERY_MENU.get(), com.trd.client.overlay.gui.GUIMachineBattery::new);
        event.register(com.trd.menu.ModMenuTypes.ELECTRIC_FURNACE_MENU.get(), com.trd.client.overlay.gui.GUIElectricFurnace::new);
    }

    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(), com.trd.client.gecko.block.energy.MachineBatteryRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CONNECTOR_BE.get(), com.trd.client.render.ConnectorRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModModels.init();

        VisualizerRegistry.setVisualizer(ModBlockEntities.MILLSTONE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.MillstoneBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.MillstoneBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.MillstoneBlockEntity be, float partialTick) {
                return new MillstoneVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.MillstoneBlockEntity be) {
                return false;
            }
        });
    }
}
