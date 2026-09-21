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

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ModClientSetup {

@SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.trd.menu.ModMenuTypes.MACHINE_BATTERY_MENU.get(), com.trd.client.overlay.gui.GUIMachineBattery::new);
        event.register(com.trd.menu.ModMenuTypes.ELECTRIC_FURNACE_MENU.get(), com.trd.client.overlay.gui.GUIElectricFurnace::new);
        event.register(com.trd.menu.ModMenuTypes.FLUID_BARREL_MENU.get(), com.trd.client.overlay.gui.GUIFluidBarrel::new);
        event.register(com.trd.menu.ModMenuTypes.FUEL_TANK_MENU.get(), com.trd.client.overlay.gui.GUIFuelTank::new);
    }

    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(), com.trd.client.gecko.block.energy.MachineBatteryRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CONNECTOR_BE.get(), com.trd.client.render.ConnectorRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.FUEL_TANK_BE.get(), com.trd.client.render.ber.FuelTankRenderer::new);
        // event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.FUEL_TANK_SMALL_BE.get(), com.trd.client.render.ber.FuelTankRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) { // 1 is usually the overlay layer with the fluid drop
                String fluidId = com.trd.item.industrial.fluids.FluidIdentifierItem.getSelectedFluid(stack);
                if (!fluidId.equals("none")) {
                    try {
                        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(fluidId);
                        if (id != null) {
                            net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                            if (fluid != null) {
                                return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluidType())
                                        .getTintColor(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000));
                            }
                        }
                    } catch (Exception e) {}
                }
            }
            return 0xFFFFFF; // Default white
        }, com.trd.item.ModItems.FLUID_IDENTIFIER.get());
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

        VisualizerRegistry.setVisualizer(ModBlockEntities.FUEL_TANK_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.FuelTankVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity be) {
                return false;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.FUEL_TANK_SMALL_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.FuelTankSmallVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity be) {
                return false;
            }
        });
    }
}
