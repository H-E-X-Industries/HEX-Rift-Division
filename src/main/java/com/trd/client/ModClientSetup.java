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
                    if (fluidId.contains("lava")) return 0xFFE64306;
                    if (fluidId.contains("water")) return 0xFF4487FF;

                    try {
                        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(fluidId);
                        if (id != null) {
                            net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                            if (fluid != null) {
                                return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluidType())
                                        .getTintColor(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000)) | 0xFF000000;
                            }
                        }
                    } catch (Exception e) {}
                } else {
                    return 0xFF717070; // none
                }
            }
            return -1; // Default no tint
        }, com.trd.item.ModItems.FLUID_IDENTIFIER.get());

        for (net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item, ? extends net.minecraft.world.item.Item> dropObj : com.trd.api.fluids.ModFluids.getAllFluidDrops().values()) {
            event.register((stack, tintIndex) -> {
                if (tintIndex == 0) {
                    if (stack.getItem() instanceof com.trd.api.fluids.system.FluidDropItem drop) {
                        return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(drop.getFluidType())
                                .getTintColor() | 0xFF000000;
                    }
                }
                return -1;
            }, dropObj.get());
        }

        // Overwrite specific ones
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF717070 : -1, com.trd.api.fluids.ModFluids.FLUID_DROP_NONE.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFFE64306 : -1, com.trd.api.fluids.ModFluids.FLUID_DROP_LAVA.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF4487FF : -1, com.trd.api.fluids.ModFluids.FLUID_DROP_WATER.get());
    }

    @SubscribeEvent
    public static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1 && level != null && pos != null) {
                if (level.getBlockEntity(pos) instanceof com.trd.block.entity.industrial.fluids.FluidPipeBlockEntity be) {
                    net.minecraft.world.level.material.Fluid fluid = be.getFilterFluid();
                    if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        if (fluid == net.minecraft.world.level.material.Fluids.LAVA || fluid == net.minecraft.world.level.material.Fluids.FLOWING_LAVA) {
                            return 0xFF5500;
                        }
                        if (fluid == net.minecraft.world.level.material.Fluids.WATER || fluid == net.minecraft.world.level.material.Fluids.FLOWING_WATER) {
                            return 0x3F76E4;
                        }
                        return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluidType())
                                .getTintColor(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000));
                    }
                }
            }
            return -1;
        }, com.trd.block.basic.ModBlocks.BRONZE_FLUID_PIPE.get(), com.trd.block.basic.ModBlocks.STEEL_FLUID_PIPE.get(), com.trd.block.basic.ModBlocks.LEAD_FLUID_PIPE.get(), com.trd.block.basic.ModBlocks.TUNGSTEN_FLUID_PIPE.get());
    }

    @SubscribeEvent
    public static void onModifyBakingResult(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        net.minecraft.world.level.block.Block[] pipes = {
                com.trd.block.basic.ModBlocks.BRONZE_FLUID_PIPE.get(),
                com.trd.block.basic.ModBlocks.STEEL_FLUID_PIPE.get(),
                com.trd.block.basic.ModBlocks.LEAD_FLUID_PIPE.get(),
                com.trd.block.basic.ModBlocks.TUNGSTEN_FLUID_PIPE.get()
        };
        for (net.minecraft.world.level.block.Block pipe : pipes) {
            for (net.minecraft.world.level.block.state.BlockState state : pipe.getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.ModelResourceLocation location = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                net.minecraft.client.resources.model.BakedModel original = event.getModels().get(location);
                if (original != null) {
                    event.getModels().put(location, new com.trd.client.render.PipeBakedModel(original));
                }
            }
        }
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
