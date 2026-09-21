package com.trd.api.fluids;

import com.trd.api.fluids.system.BaseFluidType;
import com.trd.api.fluids.system.FluidDropItem;
import com.trd.api.fluids.system.UnbucketableLiquidBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;



import java.util.HashMap;
import java.util.Map;

public class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, "trd");
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.FLUID.key(), "trd");
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.BLOCK.key(), "trd");
    public static final DeferredRegister<Item> FLUID_DROP_ITEMS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ITEM.key(), "trd");

    private static final ResourceLocation WATER_STILL = net.minecraft.resources.ResourceLocation.parse("block/water_still");
    private static final ResourceLocation WATER_FLOW = net.minecraft.resources.ResourceLocation.parse("block/water_flow");
    private static final ResourceLocation DEFAULT_GUI_TEXTURE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/fluid_base.png");
    private static final Map<String, DeferredHolder<Item, ? extends Item>> FLUID_DROPS = new HashMap<>();

    public static final DeferredHolder<FluidType, ? extends FluidType> HYDROGEN_PEROXIDE_TYPE = FLUID_TYPES.register("hydrogen_peroxide",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/hydrogen_peroxide.png"),
                    0xc2b590, 20, 20));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> HYDROGEN_PEROXIDE_SOURCE = FLUIDS.register("hydrogen_peroxide",
            () -> new BaseFlowingFluid.Source(ModFluids.HYDROGEN_PEROXIDE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> HYDROGEN_PEROXIDE_FLOWING = FLUIDS.register("flowing_hydrogen_peroxide",
            () -> new BaseFlowingFluid.Flowing(ModFluids.HYDROGEN_PEROXIDE_PROPS));
    public static final DeferredHolder<Block, ? extends LiquidBlock> HYDROGEN_PEROXIDE_BLOCK = BLOCKS.register("hydrogen_peroxide_block",
            () -> new UnbucketableLiquidBlock(HYDROGEN_PEROXIDE_SOURCE.get(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).replaceable().noCollission().strength(100.0F).noLootTable()));
    private static final BaseFlowingFluid.Properties HYDROGEN_PEROXIDE_PROPS = new BaseFlowingFluid.Properties(
            HYDROGEN_PEROXIDE_TYPE, HYDROGEN_PEROXIDE_SOURCE, HYDROGEN_PEROXIDE_FLOWING).block(HYDROGEN_PEROXIDE_BLOCK);

    public static final DeferredHolder<FluidType, ? extends FluidType> SULFURIC_ACID_TYPE = FLUID_TYPES.register("sulfuric_acid",
            () -> new BaseFluidType(FluidType.Properties.create().density(1830).viscosity(2000).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/sulfuric_acid.png"),
                    0xbcc13f, 20, 65));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SULFURIC_ACID_SOURCE = FLUIDS.register("sulfuric_acid",
            () -> new BaseFlowingFluid.Source(ModFluids.SULFURIC_ACID_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SULFURIC_ACID_FLOWING = FLUIDS.register("flowing_sulfuric_acid",
            () -> new BaseFlowingFluid.Flowing(ModFluids.SULFURIC_ACID_PROPS));
    public static final DeferredHolder<Block, ? extends LiquidBlock> SULFURIC_ACID_BLOCK = BLOCKS.register("sulfuric_acid_block",
            () -> new UnbucketableLiquidBlock(SULFURIC_ACID_SOURCE.get(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollission().strength(100.0F).noLootTable()));
    private static final BaseFlowingFluid.Properties SULFURIC_ACID_PROPS = new BaseFlowingFluid.Properties(
            SULFURIC_ACID_TYPE, SULFURIC_ACID_SOURCE, SULFURIC_ACID_FLOWING).block(SULFURIC_ACID_BLOCK);

    public static final DeferredHolder<FluidType, ? extends FluidType> NATURAL_GAS_TYPE = FLUID_TYPES.register("natural_gas",
            () -> new BaseFluidType(FluidType.Properties.create().density(-800).viscosity(500).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/natural_gas.png"),
                    0xa3b8c4, 20, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> NATURAL_GAS_SOURCE = FLUIDS.register("natural_gas",
            () -> new BaseFlowingFluid.Source(ModFluids.NATURAL_GAS_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> NATURAL_GAS_FLOWING = FLUIDS.register("flowing_natural_gas",
            () -> new BaseFlowingFluid.Flowing(ModFluids.NATURAL_GAS_PROPS));
    private static final BaseFlowingFluid.Properties NATURAL_GAS_PROPS = new BaseFlowingFluid.Properties(
            NATURAL_GAS_TYPE, NATURAL_GAS_SOURCE, NATURAL_GAS_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> STEAM_TYPE = FLUID_TYPES.register("steam",
            () -> new BaseFluidType(FluidType.Properties.create().density(-1000).viscosity(200).temperature(373),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/steam.png"),
                    0x88FFFFFF, 100, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> STEAM_SOURCE = FLUIDS.register("steam",
            () -> new BaseFlowingFluid.Source(ModFluids.STEAM_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> STEAM_FLOWING = FLUIDS.register("flowing_steam",
            () -> new BaseFlowingFluid.Flowing(ModFluids.STEAM_PROPS));
    private static final BaseFlowingFluid.Properties STEAM_PROPS = new BaseFlowingFluid.Properties(
            STEAM_TYPE, STEAM_SOURCE, STEAM_FLOWING);


    public static final DeferredHolder<FluidType, ? extends FluidType> LOW_PRESSURE_STEAM_TYPE = FLUID_TYPES.register("low_pressure_steam",
            () -> new BaseFluidType(FluidType.Properties.create().density(-1000).viscosity(200).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/low_pressure_steam.png"),
                    0x636a7c, 20, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> LOW_PRESSURE_STEAM_SOURCE = FLUIDS.register("low_pressure_steam",
            () -> new BaseFlowingFluid.Source(ModFluids.LOW_PRESSURE_STEAM_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> LOW_PRESSURE_STEAM_FLOWING = FLUIDS.register("flowing_low_pressure_steam",
            () -> new BaseFlowingFluid.Flowing(ModFluids.LOW_PRESSURE_STEAM_PROPS));
    private static final BaseFlowingFluid.Properties LOW_PRESSURE_STEAM_PROPS = new BaseFlowingFluid.Properties(
            LOW_PRESSURE_STEAM_TYPE, LOW_PRESSURE_STEAM_SOURCE, LOW_PRESSURE_STEAM_FLOWING);


    public static final DeferredHolder<FluidType, ? extends FluidType> MERCURY_TYPE = FLUID_TYPES.register("mercury",
            () -> new BaseFluidType(FluidType.Properties.create().density(-1000).viscosity(200).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/mercury.png"),
                    0xaeaeae, 20, 20));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> MERCURY_SOURCE = FLUIDS.register("mercury",
            () -> new BaseFlowingFluid.Source(ModFluids.MERCURY_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> MERCURY_FLOWING = FLUIDS.register("flowing_mercury",
            () -> new BaseFlowingFluid.Flowing(ModFluids.MERCURY_PROPS));
    private static final BaseFlowingFluid.Properties MERCURY_PROPS = new BaseFlowingFluid.Properties(
            MERCURY_TYPE, MERCURY_SOURCE, MERCURY_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> SODIUM_SULFATE_TYPE = FLUID_TYPES.register("sodium_sulfate",
            () -> new BaseFluidType(FluidType.Properties.create().density(-1000).viscosity(200).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/sodium_sulfate.png"),
                    0xdadd8f, 20, 55));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODIUM_SULFATE_SOURCE = FLUIDS.register("sodium_sulfate",
            () -> new BaseFlowingFluid.Source(ModFluids.SODIUM_SULFATE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODIUM_SULFATE_FLOWING = FLUIDS.register("flowing_sodium_sulfate",
            () -> new BaseFlowingFluid.Flowing(ModFluids.SODIUM_SULFATE_PROPS));
    private static final BaseFlowingFluid.Properties SODIUM_SULFATE_PROPS = new BaseFlowingFluid.Properties(
            SODIUM_SULFATE_TYPE, SODIUM_SULFATE_SOURCE, SODIUM_SULFATE_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> HYDROGEN_CHLORINE_TYPE = FLUID_TYPES.register("hydrogen_chlorine",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/hydrogen_chloride.png"),
                    0xb3b38c, 20, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> HYDROGEN_CHLORINE_SOURCE = FLUIDS.register("hydrogen_chlorine",
            () -> new BaseFlowingFluid.Source(ModFluids.HYDROGEN_CHLORINE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> HYDROGEN_CHLORINE_FLOWING = FLUIDS.register("flowing_hydrogen_chlorine",
            () -> new BaseFlowingFluid.Flowing(ModFluids.HYDROGEN_PEROXIDE_PROPS));
    private static final BaseFlowingFluid.Properties HYDROGEN_CHLORINE_PROPS = new BaseFlowingFluid.Properties(
            HYDROGEN_CHLORINE_TYPE, HYDROGEN_CHLORINE_SOURCE, HYDROGEN_CHLORINE_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> CARBON_DIOXIDE_TYPE = FLUID_TYPES.register("carbon_dioxide",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/carbon_dioxide.png"),
                    0x595959, 20, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> CARBON_DIOXIDE_SOURCE = FLUIDS.register("carbon_dioxide",
            () -> new BaseFlowingFluid.Source(ModFluids.CARBON_DIOXIDE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> CARBON_DIOXIDE_FLOWING = FLUIDS.register("flowing_carbon_dioxide",
            () -> new BaseFlowingFluid.Flowing(ModFluids.CARBON_DIOXIDE_PROPS));
    private static final BaseFlowingFluid.Properties CARBON_DIOXIDE_PROPS = new BaseFlowingFluid.Properties(
            CARBON_DIOXIDE_TYPE, CARBON_DIOXIDE_SOURCE, CARBON_DIOXIDE_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> SODA_TYPE = FLUID_TYPES.register("soda",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/soda.png"),
                    0xe0f6ef, 20, 5));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODA_SOURCE = FLUIDS.register("soda",
            () -> new BaseFlowingFluid.Source(ModFluids.SODA_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODA_FLOWING = FLUIDS.register("flowing_soda",
            () -> new BaseFlowingFluid.Flowing(ModFluids.SODA_PROPS));
    private static final BaseFlowingFluid.Properties SODA_PROPS = new BaseFlowingFluid.Properties(
            SODA_TYPE, SODA_SOURCE, SODA_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> SODIUM_HYDROXIDE_TYPE = FLUID_TYPES.register("sodium_hydroxide",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/sodium_hydroxide.png"),
                    0xc5a56e, 20, 73));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODIUM_HYDROXIDE_SOURCE = FLUIDS.register("sodium_hydroxide",
            () -> new BaseFlowingFluid.Source(ModFluids.SODIUM_HYDROXIDE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> SODIUM_HYDROXIDE_FLOWING = FLUIDS.register("flowing_sodium_hydroxide",
            () -> new BaseFlowingFluid.Flowing(ModFluids.SODIUM_HYDROXIDE_PROPS));
    private static final BaseFlowingFluid.Properties SODIUM_HYDROXIDE_PROPS = new BaseFlowingFluid.Properties(
            SODIUM_HYDROXIDE_TYPE, SODIUM_HYDROXIDE_SOURCE, SODIUM_HYDROXIDE_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> RED_SLUDGE_TYPE = FLUID_TYPES.register("red_sludge",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/red_sludge.png"),
                    0xb14949, 20, 10));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> RED_SLUDGE_SOURCE = FLUIDS.register("red_sludge",
            () -> new BaseFlowingFluid.Source(ModFluids.RED_SLUDGE_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> RED_SLUDGE_FLOWING = FLUIDS.register("flowing_red_sludge",
            () -> new BaseFlowingFluid.Flowing(ModFluids.RED_SLUDGE_PROPS));
    private static final BaseFlowingFluid.Properties RED_SLUDGE_PROPS = new BaseFlowingFluid.Properties(
            RED_SLUDGE_TYPE, RED_SLUDGE_SOURCE, RED_SLUDGE_FLOWING);

    public static final DeferredHolder<FluidType, ? extends FluidType> ALUMINATE_SOLUTION_TYPE = FLUID_TYPES.register("aluminate_solution",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/fluid/aluminate_solution.png"),
                    0x92b1a1, 20, 0));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> ALUMINATE_SOLUTION_SOURCE = FLUIDS.register("aluminate_solution",
            () -> new BaseFlowingFluid.Source(ModFluids.ALUMINATE_SOLUTION_PROPS));
    public static final DeferredHolder<Fluid, ? extends FlowingFluid> ALUMINATE_SOLUTION_FLOWING = FLUIDS.register("flowing_aluminate_solution",
            () -> new BaseFlowingFluid.Flowing(ModFluids.ALUMINATE_SOLUTION_PROPS));
    private static final BaseFlowingFluid.Properties ALUMINATE_SOLUTION_PROPS = new BaseFlowingFluid.Properties(
            ALUMINATE_SOLUTION_TYPE, ALUMINATE_SOLUTION_SOURCE, ALUMINATE_SOLUTION_FLOWING);



    public static final DeferredHolder<Item, ? extends Item> FLUID_DROP_NONE = FLUID_DROP_ITEMS.register("fluid_drop_none", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, ? extends Item> FLUID_DROP_WATER = FLUID_DROP_ITEMS.register("fluid_drop_water",
            () -> new FluidDropItem(() -> net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse("water")).getFluidType(), new Item.Properties()));
    public static final DeferredHolder<Item, ? extends Item> FLUID_DROP_LAVA = FLUID_DROP_ITEMS.register("fluid_drop_lava",
            () -> new FluidDropItem(() -> net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse("lava")).getFluidType(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        FLUID_TYPES.register(eventBus);
        FLUIDS.register(eventBus);
        BLOCKS.register(eventBus);
        FLUID_DROPS.put("water", FLUID_DROP_WATER);
        FLUID_DROPS.put("lava", FLUID_DROP_LAVA);
        registerFluidDrops();
        FLUID_DROP_ITEMS.register(eventBus);
    }

    private static void registerFluidDrops() {
        registerDrop("hydrogen_peroxide", HYDROGEN_PEROXIDE_TYPE);
        registerDrop("hydrogen_chlorine", HYDROGEN_CHLORINE_TYPE);
        registerDrop("sulfuric_acid", SULFURIC_ACID_TYPE);
        registerDrop("natural_gas", NATURAL_GAS_TYPE);
        registerDrop("steam", STEAM_TYPE);
        registerDrop("mercury", MERCURY_TYPE);
        registerDrop("sodium_sulfate", SODIUM_SULFATE_TYPE);
        registerDrop("carbon_dioxide", CARBON_DIOXIDE_TYPE);
        registerDrop("soda", SODA_TYPE);
        registerDrop("aluminate_solution", ALUMINATE_SOLUTION_TYPE);
        registerDrop("red_sludge", RED_SLUDGE_TYPE);
        registerDrop("sodium_hydroxide", SODIUM_HYDROXIDE_TYPE);
        registerDrop("low_pressure_steam", LOW_PRESSURE_STEAM_TYPE);
    }

    private static void registerDrop(String name, DeferredHolder<FluidType, ? extends FluidType> fluidTypeObj) {
        DeferredHolder<Item, ? extends Item> dropItem = FLUID_DROP_ITEMS.register("fluid_drop_" + name,
                () -> new FluidDropItem(fluidTypeObj::get, new Item.Properties()));
        FLUID_DROPS.put(name, dropItem);
    }

    public static ResourceLocation getGuiTexture(Fluid fluid) {
        FluidType type = fluid.getFluidType();
        if (type instanceof BaseFluidType base) return base.getGuiTexture();
        if (type == NeoForgeMod.WATER_TYPE.value()) return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/item/water.png");
        if (type == NeoForgeMod.LAVA_TYPE.value()) return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "textures/item/lava.png");
        return DEFAULT_GUI_TEXTURE;
    }

    public static Item getFluidDrop(FluidType fluidType) {
        for (DeferredHolder<Item, ? extends Item> dropObj : FLUID_DROPS.values()) {
            Item item = dropObj.get();
            if (item instanceof FluidDropItem drop && drop.getFluidType() == fluidType) return item;
        }
        return null;
    }

    public static Map<String, DeferredHolder<Item, ? extends Item>> getAllFluidDrops() {
        return new HashMap<>(FLUID_DROPS);
    }

    /** Все исходные жидкости мода (источники) + ванильные вода/лава. */
    public static java.util.List<net.minecraft.world.level.material.Fluid> getAllSourceFluids() {
        java.util.List<net.minecraft.world.level.material.Fluid> fluids = new java.util.ArrayList<>();
        fluids.add(net.minecraft.world.level.material.Fluids.WATER);
        fluids.add(net.minecraft.world.level.material.Fluids.LAVA);
        fluids.add(HYDROGEN_PEROXIDE_SOURCE.get());
        fluids.add(SULFURIC_ACID_SOURCE.get());
        fluids.add(NATURAL_GAS_SOURCE.get());
        fluids.add(STEAM_SOURCE.get());
        fluids.add(LOW_PRESSURE_STEAM_SOURCE.get());
        fluids.add(MERCURY_SOURCE.get());
        fluids.add(SODIUM_SULFATE_SOURCE.get());
        fluids.add(HYDROGEN_CHLORINE_SOURCE.get());
        fluids.add(CARBON_DIOXIDE_SOURCE.get());
        fluids.add(SODA_SOURCE.get());
        fluids.add(SODIUM_HYDROXIDE_SOURCE.get());
        fluids.add(RED_SLUDGE_SOURCE.get());
        fluids.add(ALUMINATE_SOLUTION_SOURCE.get());
        return fluids;
    }
}
