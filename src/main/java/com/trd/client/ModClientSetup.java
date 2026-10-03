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
        event.register(com.trd.menu.ModMenuTypes.CONVEYOR_BUFFER_MENU.get(), com.trd.client.overlay.gui.ConveyorBufferScreen::new);
        event.register(com.trd.menu.ModMenuTypes.SORTIROVSHIK_MENU.get(), com.trd.client.overlay.gui.GUISortirovshik::new);
        event.register(com.trd.menu.ModMenuTypes.OPTIC_MICROSCOPE_MENU.get(), com.trd.client.overlay.gui.GUIOpticMicroscope::new);
        event.register(com.trd.menu.ModMenuTypes.MOTOR_ELECTRO_MENU.get(), com.trd.client.overlay.gui.GUIMotorElectro::new);
        event.register(com.trd.menu.ModMenuTypes.HEATER_MENU.get(), com.trd.client.overlay.gui.GUIHeater::new);
        event.register(com.trd.menu.ModMenuTypes.SMELTER_MENU.get(), com.trd.client.overlay.gui.GUISmelter::new);
        event.register(com.trd.menu.ModMenuTypes.CC_MACHINE_MENU.get(), com.trd.client.overlay.gui.GUICCMachine::new);
        event.register(com.trd.menu.ModMenuTypes.DROBITEL_MENU.get(), com.trd.client.overlay.gui.DrobitelScreen::new);
        event.register(com.trd.menu.ModMenuTypes.CENTRIFUGE_MENU.get(), com.trd.client.overlay.gui.CentrifugeScreen::new);
        event.register(com.trd.menu.ModMenuTypes.CENTRIFUGE_CYLINDER_MENU.get(), com.trd.client.overlay.gui.CentrifugeCylinderScreen::new);
        event.register(com.trd.menu.ModMenuTypes.VISHELASHIVATEL_MENU.get(), com.trd.client.overlay.gui.VishelashivatelScreen::new);
        event.register(com.trd.menu.ModMenuTypes.CHEMICAL_PLANT_PORT_MENU.get(), com.trd.client.overlay.gui.GUIChemicalPlantPort::new);
        event.register(com.trd.menu.ModMenuTypes.CHEMICAL_PLANT_REACTION_CHAMBER_MENU.get(), com.trd.client.overlay.gui.GUIChemicalPlantReactionChamber::new);
        event.register(com.trd.menu.ModMenuTypes.COCCER_OVEN_MENU.get(), com.trd.client.overlay.gui.CoccerOvenScreen::new);
        event.register(com.trd.menu.ModMenuTypes.STANOK_MENU.get(), com.trd.client.overlay.gui.GUIStanok::new);
        event.register(com.trd.menu.ModMenuTypes.STEEL_STORAGE_MENU.get(), com.trd.client.overlay.gui.SteelStorageScreen::new);
        event.register(com.trd.menu.ModMenuTypes.TURRET_AMMO_MENU.get(), com.trd.client.overlay.gui.GUITurretAmmo::new);
        event.register(com.trd.menu.ModMenuTypes.TROMBONE_MENU.get(), com.trd.client.overlay.gui.GUITrombone::new);
    }

    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(), com.trd.client.gecko.block.energy.MachineBatteryRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CONNECTOR_BE.get(), com.trd.client.render.ConnectorRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.FUEL_TANK_BE.get(), com.trd.client.render.ber.FuelTankRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.PAINTABLE_PIPE_BE.get(), com.trd.client.render.ber.PaintableConduitRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.PAINTABLE_WIRE_BE.get(), com.trd.client.render.ber.PaintableConduitRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CONVEYOR_BE.get(), com.trd.client.render.ber.ConveyorRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CASTING_POT.get(), com.trd.client.renderer.CastingPotRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CASTING_DESCENT.get(), com.trd.client.renderer.CastingDescentRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CC_MACHINE_BE.get(), com.trd.client.renderer.CCMachineRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(), com.trd.client.render.ber.CentrifugeCylinderRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.VISHELASHIVATEL_BE.get(), com.trd.client.render.ber.VishelachivatelRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.STANOK_BE.get(), com.trd.client.render.StanokRenderer::new);
        // event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.FUEL_TANK_SMALL_BE.get(), com.trd.client.render.ber.FuelTankRenderer::new);

        event.registerEntityRenderer(com.trd.entity.ModEntities.TURRET_BULLET.get(),
                com.trd.client.gecko.entity.bullets.TurretBulletRenderer::new);
        event.registerEntityRenderer(com.trd.entity.ModEntities.TURRET_LIGHT.get(),
                com.trd.client.gecko.entity.turrets.TurretLightRenderer::new);
        event.registerEntityRenderer(com.trd.entity.ModEntities.TURRET_LIGHT_LINKED.get(),
                com.trd.client.gecko.entity.turrets.TurretLightLinkedRenderer::new);
        event.registerEntityRenderer(com.trd.entity.ModEntities.MISSILE_LIGHT.get(),
                com.trd.client.renderer.MissileLightRenderer::new);
        event.registerEntityRenderer(com.trd.entity.ModEntities.DEPTH_WORM.get(),
                com.trd.client.gecko.entity.mobs.DepthWormRenderer::new);
        event.registerEntityRenderer(com.trd.entity.ModEntities.DEPTH_WORM_BRUTAL.get(),
                com.trd.client.gecko.entity.mobs.DepthWormBrutalRenderer::new);
        event.registerBlockEntityRenderer(com.trd.block.entity.ModBlockEntities.TURRET_LIGHT_PLACER_BE.get(),
                com.trd.client.gecko.block.turrets.TurretLightPlacerRenderer::new);


        // Гранаты рисуются ванильным рендерером брошенного предмета (модель самого предмета).
        // Без регистрации клиент рисует на их месте свинью.
        for (var grenade : java.util.List.of(
                com.trd.entity.ModEntities.GRAVITY_GRENADE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADEHE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADEFIRE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADESMART_PROJECTILE,
                com.trd.entity.ModEntities.GRENADESLIME_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_IF_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_IF_HE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_IF_SLIME_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_IF_FIRE_PROJECTILE,
                com.trd.entity.ModEntities.GRENADE_NUC_PROJECTILE)) {
            event.registerEntityRenderer(grenade.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.trd.client.config.ModKeyBindings.RELOAD_KEY);
        event.register(com.trd.client.config.ModKeyBindings.UNLOAD_KEY);
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

        event.register((stack, tintIndex) -> {
            if (tintIndex == 1 && stack.getItem() instanceof com.trd.item.industrial.fluids.FluidContainerItem) {
                net.neoforged.neoforge.fluids.FluidStack fluid = com.trd.item.industrial.fluids.FluidContainerItem.getFluid(stack);
                if (!fluid.isEmpty()) {
                    return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid())
                            .getTintColor(fluid) | 0xFF000000;
                }
            }
            return -1;
        }, com.trd.item.ModItems.PIPETTE.get(), com.trd.item.ModItems.PIPETTE_IDUSTRIAL.get(), com.trd.item.ModItems.FLUID_TANK_IRON.get());

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

        // === Metallurgy Heat Gradients ===
        com.trd.api.metallurgy.system.ItemHeatColorRegistry.registerMixed(
                com.trd.api.metallurgy.system.ItemHeatColorRegistry.HeatGradient.RED_TO_WHITE,
                net.minecraft.world.item.Items.NETHERITE_INGOT,
                net.minecraft.world.item.Items.NETHERITE_BLOCK,
                net.minecraft.world.item.Items.NETHERITE_PICKAXE,
                net.minecraft.world.item.Items.NETHERITE_AXE,
                net.minecraft.world.item.Items.NETHERITE_SHOVEL,
                net.minecraft.world.item.Items.NETHERITE_HOE,
                net.minecraft.world.item.Items.NETHERITE_SWORD,
                net.minecraft.world.item.Items.NETHERITE_HELMET,
                net.minecraft.world.item.Items.NETHERITE_CHESTPLATE,
                net.minecraft.world.item.Items.NETHERITE_LEGGINGS,
                net.minecraft.world.item.Items.NETHERITE_BOOTS
        );

        com.trd.api.metallurgy.system.ItemHeatColorRegistry.registerMixed(
                com.trd.api.metallurgy.system.ItemHeatColorRegistry.HeatGradient.ORANGE_TO_WHITE,
                net.minecraft.world.item.Items.COPPER_INGOT,
                net.minecraft.world.item.Items.COPPER_BLOCK,
                net.minecraft.world.item.Items.RAW_COPPER,
                net.minecraft.world.item.Items.RAW_COPPER_BLOCK,
                net.minecraft.world.item.Items.COPPER_ORE,
                net.minecraft.world.item.Items.DEEPSLATE_COPPER_ORE,
                net.minecraft.world.item.Items.LIGHTNING_ROD,
                net.minecraft.world.item.Items.IRON_INGOT,
                net.minecraft.world.item.Items.IRON_NUGGET,
                net.minecraft.world.item.Items.IRON_BLOCK,
                net.minecraft.world.item.Items.RAW_IRON,
                net.minecraft.world.item.Items.RAW_IRON_BLOCK,
                net.minecraft.world.item.Items.IRON_ORE,
                net.minecraft.world.item.Items.DEEPSLATE_IRON_ORE,
                net.minecraft.world.item.Items.IRON_PICKAXE,
                net.minecraft.world.item.Items.IRON_AXE,
                net.minecraft.world.item.Items.IRON_SHOVEL,
                net.minecraft.world.item.Items.IRON_HOE,
                net.minecraft.world.item.Items.IRON_SWORD,
                net.minecraft.world.item.Items.IRON_HELMET,
                net.minecraft.world.item.Items.IRON_CHESTPLATE,
                net.minecraft.world.item.Items.IRON_LEGGINGS,
                net.minecraft.world.item.Items.IRON_BOOTS,
                net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.GOLD_NUGGET,
                net.minecraft.world.item.Items.GOLD_BLOCK,
                net.minecraft.world.item.Items.RAW_GOLD,
                net.minecraft.world.item.Items.RAW_GOLD_BLOCK,
                net.minecraft.world.item.Items.GOLD_ORE,
                net.minecraft.world.item.Items.DEEPSLATE_GOLD_ORE,
                net.minecraft.world.item.Items.GOLDEN_PICKAXE,
                net.minecraft.world.item.Items.GOLDEN_AXE,
                net.minecraft.world.item.Items.GOLDEN_SHOVEL,
                net.minecraft.world.item.Items.GOLDEN_HOE,
                net.minecraft.world.item.Items.GOLDEN_SWORD,
                net.minecraft.world.item.Items.GOLDEN_HELMET,
                net.minecraft.world.item.Items.GOLDEN_CHESTPLATE,
                net.minecraft.world.item.Items.GOLDEN_LEGGINGS,
                net.minecraft.world.item.Items.GOLDEN_BOOTS,
                com.trd.item.ModItems.CAST_PICKAXE_STEEL_BASE.get(),
                com.trd.item.ModItems.CAST_PICKAXE_IRON_BASE.get(),
                com.trd.item.ModItems.STEEL_PLATE.get(),
                com.trd.item.ModItems.TUNGSTEN_PLATE.get(),
                com.trd.item.ModItems.ALUMINUM_PLATE.get(),
                com.trd.item.ModItems.TITANIUM_PLATE.get(),
                com.trd.item.ModItems.INDUSTRIAL_COPPER_PLATE.get(),
                com.trd.item.ModItems.GOLD_PLATE.get(),
                com.trd.item.ModItems.LEAD_PLATE.get(),
                com.trd.item.ModItems.IRON_PLATE.get(),
                com.trd.item.ModItems.ALUMINA.get(),
                com.trd.main.ResourceRegistry.getMainUnit("steel"),
                com.trd.main.ResourceRegistry.getSmallUnit("steel"),
                com.trd.main.ResourceRegistry.getBlock("steel"),
                com.trd.main.ResourceRegistry.getMainUnit("aluminum"),
                com.trd.main.ResourceRegistry.getSmallUnit("aluminum"),
                com.trd.main.ResourceRegistry.getBlock("aluminum"),
                com.trd.main.ResourceRegistry.getMainUnit("bronze"),
                com.trd.main.ResourceRegistry.getSmallUnit("bronze"),
                com.trd.main.ResourceRegistry.getBlock("bronze"),
                com.trd.main.ResourceRegistry.getMainUnit("tin"),
                com.trd.main.ResourceRegistry.getSmallUnit("tin"),
                com.trd.main.ResourceRegistry.getBlock("tin"),
                com.trd.main.ResourceRegistry.getMainUnit("titanium"),
                com.trd.main.ResourceRegistry.getSmallUnit("titanium"),
                com.trd.main.ResourceRegistry.getBlock("titanium"),
                com.trd.main.ResourceRegistry.getBlock("industrial_copper"),
                com.trd.main.ResourceRegistry.getMainUnit("industrial_copper"),
                com.trd.main.ResourceRegistry.getSmallUnit("industrial_copper"),
                com.trd.main.ResourceRegistry.getMainUnit("lead"),
                com.trd.main.ResourceRegistry.getSmallUnit("lead"),
                com.trd.main.ResourceRegistry.getBlock("lead"),
                com.trd.main.ResourceRegistry.getMainUnit("tungsten"),
                com.trd.main.ResourceRegistry.getSmallUnit("tungsten"),
                com.trd.main.ResourceRegistry.getBlock("tungsten"),
                com.trd.main.ResourceRegistry.getMainUnit("beryllium"),
                com.trd.main.ResourceRegistry.getSmallUnit("beryllium"),
                com.trd.main.ResourceRegistry.getBlock("beryllium"),
                com.trd.main.ResourceRegistry.getMainUnit("zinc"),
                com.trd.main.ResourceRegistry.getSmallUnit("zinc"),
                com.trd.main.ResourceRegistry.getBlock("zinc")
        );

        com.trd.api.metallurgy.system.ItemHeatColorRegistry.registerMixed(
                com.trd.api.metallurgy.system.ItemHeatColorRegistry.HeatGradient.BLUE_TO_WHITE,
                com.trd.main.ResourceRegistry.getMainUnit("neodymium"),
                com.trd.main.ResourceRegistry.getSmallUnit("neodymium"),
                com.trd.main.ResourceRegistry.getBlock("neodymium")
        );

        // Heat item color registration
        registerItemHeatColor(event,
                net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.IRON_NUGGET,
                net.minecraft.world.item.Items.IRON_BLOCK,
                net.minecraft.world.item.Items.RAW_IRON, net.minecraft.world.item.Items.RAW_IRON_BLOCK,
                net.minecraft.world.item.Items.IRON_ORE, net.minecraft.world.item.Items.DEEPSLATE_IRON_ORE,
                net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.IRON_AXE,
                net.minecraft.world.item.Items.IRON_SHOVEL, net.minecraft.world.item.Items.IRON_HOE, net.minecraft.world.item.Items.IRON_SWORD,
                net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.IRON_CHESTPLATE,
                net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.IRON_BOOTS,
                com.trd.main.ResourceRegistry.getMainUnit("steel"),
                com.trd.main.ResourceRegistry.getSmallUnit("steel"),
                com.trd.item.ModItems.CAST_PICKAXE_STEEL_BASE.get(),
                com.trd.item.ModItems.CAST_PICKAXE_IRON_BASE.get(),
                com.trd.main.ResourceRegistry.getBlock("steel"),
                net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.GOLD_NUGGET,
                net.minecraft.world.item.Items.GOLD_BLOCK,
                net.minecraft.world.item.Items.RAW_GOLD, net.minecraft.world.item.Items.RAW_GOLD_BLOCK,
                net.minecraft.world.item.Items.GOLD_ORE, net.minecraft.world.item.Items.DEEPSLATE_GOLD_ORE,
                net.minecraft.world.item.Items.GOLDEN_PICKAXE, net.minecraft.world.item.Items.GOLDEN_AXE,
                net.minecraft.world.item.Items.GOLDEN_SHOVEL, net.minecraft.world.item.Items.GOLDEN_HOE, net.minecraft.world.item.Items.GOLDEN_SWORD,
                net.minecraft.world.item.Items.GOLDEN_HELMET, net.minecraft.world.item.Items.GOLDEN_CHESTPLATE,
                net.minecraft.world.item.Items.GOLDEN_LEGGINGS, net.minecraft.world.item.Items.GOLDEN_BOOTS,
                net.minecraft.world.item.Items.COPPER_INGOT,
                net.minecraft.world.item.Items.COPPER_BLOCK,
                net.minecraft.world.item.Items.RAW_COPPER, net.minecraft.world.item.Items.RAW_COPPER_BLOCK,
                net.minecraft.world.item.Items.COPPER_ORE, net.minecraft.world.item.Items.DEEPSLATE_COPPER_ORE,
                net.minecraft.world.item.Items.LIGHTNING_ROD,
                net.minecraft.world.item.Items.NETHERITE_INGOT,
                com.trd.item.ModItems.ALUMINA.get(),
                net.minecraft.world.item.Items.NETHERITE_BLOCK,
                net.minecraft.world.item.Items.NETHERITE_PICKAXE, net.minecraft.world.item.Items.NETHERITE_AXE,
                net.minecraft.world.item.Items.NETHERITE_SHOVEL, net.minecraft.world.item.Items.NETHERITE_HOE, net.minecraft.world.item.Items.NETHERITE_SWORD,
                com.trd.main.ResourceRegistry.getMainUnit("aluminum"),
                com.trd.main.ResourceRegistry.getSmallUnit("aluminum"),
                com.trd.main.ResourceRegistry.getBlock("aluminum"),
                com.trd.main.ResourceRegistry.getMainUnit("bronze"),
                com.trd.main.ResourceRegistry.getSmallUnit("bronze"),
                com.trd.main.ResourceRegistry.getBlock("bronze"),
                com.trd.main.ResourceRegistry.getMainUnit("tin"),
                com.trd.main.ResourceRegistry.getSmallUnit("tin"),
                com.trd.main.ResourceRegistry.getBlock("tin"),
                com.trd.main.ResourceRegistry.getMainUnit("zinc"),
                com.trd.main.ResourceRegistry.getSmallUnit("zinc"),
                com.trd.main.ResourceRegistry.getBlock("zinc"),
                com.trd.main.ResourceRegistry.getMainUnit("titanium"),
                com.trd.main.ResourceRegistry.getSmallUnit("titanium"),
                com.trd.main.ResourceRegistry.getBlock("titanium"),
                com.trd.main.ResourceRegistry.getMainUnit("industrial_copper"),
                com.trd.main.ResourceRegistry.getSmallUnit("industrial_copper"),
                com.trd.main.ResourceRegistry.getBlock("industrial_copper"),
                com.trd.main.ResourceRegistry.getMainUnit("lead"),
                com.trd.main.ResourceRegistry.getSmallUnit("lead"),
                com.trd.main.ResourceRegistry.getBlock("lead"),
                com.trd.main.ResourceRegistry.getMainUnit("beryllium"),
                com.trd.main.ResourceRegistry.getSmallUnit("beryllium"),
                com.trd.main.ResourceRegistry.getBlock("beryllium"),
                com.trd.main.ResourceRegistry.getMainUnit("tungsten"),
                com.trd.main.ResourceRegistry.getSmallUnit("tungsten"),
                com.trd.main.ResourceRegistry.getBlock("tungsten"),
                com.trd.main.ResourceRegistry.getMainUnit("neodymium"),
                com.trd.main.ResourceRegistry.getSmallUnit("neodymium"),
                com.trd.main.ResourceRegistry.getBlock("neodymium"),
                com.trd.item.ModItems.STEEL_PLATE.get(),
                com.trd.item.ModItems.TUNGSTEN_PLATE.get(),
                com.trd.item.ModItems.ALUMINUM_PLATE.get(),
                com.trd.item.ModItems.TITANIUM_PLATE.get(),
                com.trd.item.ModItems.INDUSTRIAL_COPPER_PLATE.get(),
                com.trd.item.ModItems.GOLD_PLATE.get(),
                com.trd.item.ModItems.LEAD_PLATE.get(),
                com.trd.item.ModItems.IRON_PLATE.get()
        );

        // Slag handler
        event.register((stack, tintIndex) -> com.trd.api.metallurgy.system.ItemHeatColorRegistry.getSlagHeatColor(stack, tintIndex),
                com.trd.item.ModItems.SLAG.get()
        );

        // Liquid metal handler
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                    if (tag.contains("MetalColor")) {
                        return 0xFF000000 | tag.getInt("MetalColor");
                    }
                    if (tag.contains("MetalId")) {
                        try {
                            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(tag.getString("MetalId"));
                            return com.trd.api.metallurgy.system.MetallurgyRegistry.get(id)
                                    .map(m -> 0xFF000000 | m.getColor())
                                    .orElse(-1);
                        } catch (Exception ignored) {}
                    }
                }
            }
            return -1;
        }, com.trd.item.ModItems.LIQUID_METAL.get());

        // Кусок фракции: тинт по цвету фракции и состоянию
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0 && stack.getItem() instanceof com.trd.item.conglomerates.FractionChunkItem) {
                return com.trd.item.conglomerates.FractionChunkItem.getDisplayColor(stack);
            }
            return -1;
        }, com.trd.item.ModItems.FRACTION_CHUNK.get());

        // Кусочек металла: тинт по цвету металла
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0 && stack.getItem() instanceof com.trd.item.conglomerates.MetalPieceItem) {
                return com.trd.item.conglomerates.MetalPieceItem.getDisplayColor(stack);
            }
            return -1;
        }, com.trd.item.ModItems.METAL_PIECE.get());

        // Хвоя секвойи в инвентаре
        event.register((stack, tintIndex) -> {
            return 9219125;
        }, com.trd.block.basic.ModBlocks.SEQUOIA_LEAVES.get());
    }

    private static void registerItemHeatColor(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event, Object... items) {
        java.util.List<net.minecraft.world.level.ItemLike> valid = new java.util.ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof net.minecraft.world.level.ItemLike itemLike) {
                valid.add(itemLike);
            } else if (obj instanceof java.util.function.Supplier<?> sup) {
                Object val = sup.get();
                if (val instanceof net.minecraft.world.level.ItemLike itemLike) {
                    valid.add(itemLike);
                }
            }
        }
        if (!valid.isEmpty()) {
            event.register((stack, tintIndex) -> com.trd.api.metallurgy.system.ItemHeatColorRegistry.getHeatColor(stack, tintIndex), valid.toArray(new net.minecraft.world.level.ItemLike[0]));
        }
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

        // Кратерные блоки с собственным свойством DARKNESS (мягкий базальт + выжженная трава):
        // тёмный уровень живёт в BlockState, а не в позиционной базе, поэтому красим здесь.
        // Их модели помечены tintindex, значит CraterTints.onModelBake их НЕ оборачивает —
        // без этого хендлера tint не применяется вовсе.
        event.register((state, level, pos, tintIndex) -> {
            int dark = state.getValue(com.trd.block.basic.CraterBasaltBlock.DARKNESS);
            float f = 1.0f - com.trd.client.render.CraterTints.MAX_DARKNESS_RATIO
                    * (dark / (float) com.trd.block.basic.CraterBasaltBlock.MAX_DARK);
            int c = (int) (255.0f * f);
            return 0xFF000000 | (c << 16) | (c << 8) | c;
        }, com.trd.block.basic.ModBlocks.BASALT_SOFT.get(), com.trd.block.basic.ModBlocks.BASALT_SOFT_2.get(),
                com.trd.block.basic.ModBlocks.BASALT_SOFT_3.get(), com.trd.block.basic.ModBlocks.BASALT_SOFT_4.get(),
                com.trd.block.basic.ModBlocks.WASTE_GRASS.get());

        // Запёкшийся базальт: у него обратная шкала — LIGHT=7 это нормальный свет,
        // LIGHT=0 это темно у центра воронки. Модели помечены tintindex, поэтому
        // CraterTints их не оборачивает и цвет обязан приходить отсюда.
        event.register((state, level, pos, tintIndex) -> {
            int light = state.getValue(com.trd.block.basic.ScorchedBasaltBlock.LIGHT);
            float f = 1.0f - com.trd.client.render.CraterTints.MAX_DARKNESS_RATIO
                    * ((com.trd.block.basic.ScorchedBasaltBlock.MAX_LIGHT - light)
                       / (float) com.trd.block.basic.ScorchedBasaltBlock.MAX_LIGHT);
            int c = (int) (255.0f * f);
            return 0xFF000000 | (c << 16) | (c << 8) | c;
        }, com.trd.block.basic.ModBlocks.BASALT_SCORCHED.get());

        // Дёрн: у него есть свой биомный тинт, поэтому CraterTints его не оборачивает.
        // Умножаем биомный цвет на позиционное затемнение — снаружи множитель == 1 и цвет
        // возвращается без изменений, то есть блок ведёт себя как обычно.
        // ВНИМАНИЕ: не добавлять сюда блоки с кастомным рендерером (как у дверей/жидкостных труб) —
        // рисуются через BlockEntityRenderer, квадов не отдают.
        event.register((state, level, pos, tintIndex) -> {
            int biome = level != null && pos != null
                    ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level, pos)
                    // В 1.20.1 здесь стоял FoliageColor.getDefaultColor() — он заметно темнее
                    // и в инвентаре дёрн выглядел не так, как у ваниллы. GrassColor — то,
                    // что использует сам BlockColors.createDefault() для Blocks.GRASS_BLOCK.
                    : net.minecraft.world.level.GrassColor.getDefaultColor();
            float f = com.trd.client.render.CraterTints.darknessMultiplier(level, pos);
            if (f >= 1.0f) return biome;
            int r = (int) ((biome >> 16 & 0xFF) * f);
            int g = (int) ((biome >> 8 & 0xFF) * f);
            int b = (int) ((biome & 0xFF) * f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);

        // Хвоя секвойи: красится в цвет листвы биома
        event.register((state, level, pos, tintIndex) -> {
            return level != null && pos != null
                    ? net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(level, pos)
                    : 9219125;
        }, com.trd.block.basic.ModBlocks.SEQUOIA_LEAVES.get());
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

        // Подмена модели для невидимых блоков коллизии балок
        net.minecraft.client.resources.model.ModelResourceLocation beamBaseLocation =
                net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(com.trd.block.basic.ModBlocks.BEAM_BLOCK.get().defaultBlockState());
        net.minecraft.client.resources.model.BakedModel beamBaseModel = event.getModels().get(beamBaseLocation);
        if (beamBaseModel != null) {
            for (net.minecraft.world.level.block.state.BlockState state : com.trd.block.basic.ModBlocks.BEAM_COLLISION.get().getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.ModelResourceLocation location =
                        net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                event.getModels().put(location, new com.trd.client.render.DynamicBeamModel(beamBaseModel));
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModModels.init();

        net.minecraft.client.renderer.item.ItemProperties.register(com.trd.item.ModItems.PIPETTE.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "filled"),
                (pStack, pLevel, pEntity, pSeed) ->
                        com.trd.item.industrial.fluids.FluidContainerItem.isFilled(pStack) ? 1.0f : 0.0f);
        net.minecraft.client.renderer.item.ItemProperties.register(com.trd.item.ModItems.FLUID_TANK_IRON.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "filled"),
                (pStack, pLevel, pEntity, pSeed) ->
                        com.trd.item.industrial.fluids.FluidContainerItem.isFilled(pStack) ? 1.0f : 0.0f);

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

        VisualizerRegistry.setVisualizer(ModBlockEntities.SHAFT_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.ShaftBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.ShaftBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.ShaftBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.ShaftVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.ShaftBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.BEARING_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.BearingBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.BearingBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.BearingBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.BearingVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.BearingBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.HAND_CRANK_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.HandCrankBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.HandCrankBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.HandCrankBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.HandCrankVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.HandCrankBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.CLUTCH_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.ClutchBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.ClutchBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.ClutchBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.ClutchVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.ClutchBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.MOTOR_ELECTRO_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.MotorVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.TACHOMETER_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.TachometerBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.TachometerBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.TachometerBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.TachometerVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.TachometerBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.WATER_PUMP_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.WaterPumpVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.BOILER_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.boiler.BoilerBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.boiler.BoilerBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.boiler.BoilerBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.BoilerVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.boiler.BoilerBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.STEAM_ENGINE_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.SteamEngineVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.STATOR_BE.get(), new BlockEntityVisualizer<com.trd.block.entity.industrial.rotation.StatorBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.block.entity.industrial.rotation.StatorBlockEntity> createVisual(VisualizationContext ctx, com.trd.block.entity.industrial.rotation.StatorBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.StatorVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.block.entity.industrial.rotation.StatorBlockEntity be) {
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.DROBITEL_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.DrobitelVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity be) {
                return false;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.CentrifugeCylinderVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity be) {
                return false;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.VISHELASHIVATEL_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.VishelashivatelVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity be) {
                return false;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.STANOK_BE.get(), new BlockEntityVisualizer<com.trd.multiblock.industrial.stanok.StanokBlockEntity>() {
            @Override
            public BlockEntityVisual<? super com.trd.multiblock.industrial.stanok.StanokBlockEntity> createVisual(VisualizationContext ctx, com.trd.multiblock.industrial.stanok.StanokBlockEntity be, float partialTick) {
                return new com.trd.client.render.flywheel.StanokVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.trd.multiblock.industrial.stanok.StanokBlockEntity be) {
                return false;
            }
        });
    }
}

