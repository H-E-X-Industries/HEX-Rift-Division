package com.trd.compat.jei;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.api.fluids.ModFluids;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.recipe.AlloyRecipe;
import com.trd.api.metallurgy.system.recipe.AlloySlot;
import com.trd.api.metallurgy.system.recipe.MoldRecipe;
import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.industrial.rotation.MillstoneBlockEntity;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.item.ModItems;
import com.trd.item.industrial.fluids.FluidContainerItem;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.centrifuge.conus.CentrifugeRecipe;
import com.trd.multiblock.industrial.centrifuge.conus.CentrifugeRecipes;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderRecipe;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderRecipes;
import com.trd.multiblock.industrial.coccer.CoccerOvenRecipe;
import com.trd.multiblock.industrial.coccer.CoccerOvenRecipeRegistry;
import com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@JeiPlugin
public class trdJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = new ResourceLocation(MainRegistry.MOD_ID, "jei_plugin");

    // Макет 140x44: сетка входов 2x3 на (5,5), сетка выходов 2x3 на (83,5), текст на (60,22) — химическая установка (gui4)
    public static final ResourceLocation TEXTURE_UNIVERSAL_140x44 =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui4.png");
    // Макет 102x60: вход на (5,22), выходы 2x2 на (63,13), текст на (24,33) — жернов и дробитель (gui3)
    public static final ResourceLocation TEXTURE_UNIVERSAL_102x60 =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui3.png");
    // Коксовая печь: файл 256x256, видимая часть 90x64
    public static final ResourceLocation TEXTURE_COKE_OVEN =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui5.png");
    // Выщелащиватель: макет 130x60: входы 2шт на (5,22), выходы 3шт на (73,22) — gui6
    public static final ResourceLocation TEXTURE_UNIVERSAL_130x60 =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui6.png");
    private static final int[][] GRID_INPUT_SLOTS = {
            {5, 5}, {23, 5}, {41, 5},
            {5, 23}, {23, 23}, {41, 23}
    };
    private static final int[][] GRID_OUTPUT_SLOTS = {
            {83, 5}, {101, 5}, {119, 5},
            {83, 23}, {101, 23}, {119, 23}
    };
    private static final int[][] CHAMBER_OUTPUT_SLOTS = {
            {63, 13}, {81, 13}, {63, 31}, {81, 31}
    };

    public static final RecipeType<DrobitelWrapper> DROBITEL_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "drobitel", DrobitelWrapper.class);
    public record DrobitelWrapper(Item input, List<ItemStack> outputs) {}
    public static final RecipeType<SmeltingWrapper> SMELTING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "smelting", SmeltingWrapper.class);
    public static final RecipeType<CastingWrapper> CASTING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "casting", CastingWrapper.class);
    public static final RecipeType<AlloyingWrapper> ALLOYING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "alloying", AlloyingWrapper.class);
    public static final RecipeType<MillstoneWrapper> MILLSTONE_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "millstone", MillstoneWrapper.class);
    public static final RecipeType<ElectricFurnaceWrapper> ELECTRIC_FURNACE_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "electric_furnace", ElectricFurnaceWrapper.class);
    public static final RecipeType<BoilingWrapper> BOILING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "boiling", BoilingWrapper.class);
    public static final RecipeType<SteamEngineWrapper> STEAM_ENGINE_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "steam_engine", SteamEngineWrapper.class);
    public static final RecipeType<CondensingWrapper> CONDENSING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "condensing", CondensingWrapper.class);
    public static final RecipeType<CoccerOvenWrapper> COCCER_OVEN_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "coccer_oven", CoccerOvenWrapper.class);
    public static final RecipeType<ChemicalPlantWrapper> CHEMICAL_PLANT_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "chemical_plant", ChemicalPlantWrapper.class);
    public static final RecipeType<VishelashivatelWrapper> VISHELASHIVATEL_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "vishelashivatel", VishelashivatelWrapper.class);
    public static final RecipeType<CentrifugeWrapper> CENTRIFUGE_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "centrifuge", CentrifugeWrapper.class);
    public static final RecipeType<CentrifugeCylinderWrapper> CENTRIFUGE_CYLINDER_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "centrifuge_cylinder", CentrifugeCylinderWrapper.class);
    public static final RecipeType<ForgingWrapper> FORGING_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "forging", ForgingWrapper.class);
    public static final RecipeType<FluidContainerWrapper> FLUID_CONTAINER_TYPE =
            RecipeType.create(MainRegistry.MOD_ID, "fluid_container", FluidContainerWrapper.class);

    public record ElectricFurnaceWrapper(net.minecraft.world.item.crafting.AbstractCookingRecipe recipe, int cookTime, int energyPerTick) {}
    public record SmeltingWrapper(ItemStack input, Metal metal, int outputUnits, int temp, float heatConsumption, int timeTicks, int inputCount) {}
    public record CastingWrapper(MoldRecipe mold, Metal metal, ItemStack output, int requiredUnits) {}
    public record AlloyingWrapper(AlloyRecipe recipe) {}
    public record MillstoneWrapper(Item input, List<ItemStack> outputs, int grindsRequired) {}
    public record BoilingWrapper(ItemStack waterInput, ItemStack steamOutput, int tempC) {}
    public record SteamEngineWrapper(ItemStack steamInput, ItemStack lowPressureOutput) {}
    public record CondensingWrapper(ItemStack lowPressureInput, ItemStack waterOutput) {}
    public record CoccerOvenWrapper(CoccerOvenRecipe recipe) {}
    public record ChemicalPlantWrapper(ChemicalPlantRecipe recipe) {}
    public record VishelashivatelWrapper(com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipe recipe) {}
    public record CentrifugeWrapper(CentrifugeRecipe recipe) {}
    public record CentrifugeCylinderWrapper(CentrifugeCylinderRecipe recipe) {}
    public record ForgingWrapper(ItemStack hotIngot, ItemStack hammer, ItemStack hotPlate, ItemStack hammerDamaged, Metal metal, int requiredTemp) {}

    public record FluidContainerWrapper(ItemStack input, ItemStack output, boolean fill) {}

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SmeltingCategory(guiHelper));
        registration.addRecipeCategories(new CastingCategory(guiHelper));
        registration.addRecipeCategories(new AlloyingCategory(guiHelper));
        registration.addRecipeCategories(new MillstoneCategory(guiHelper));
        registration.addRecipeCategories(new ElectricFurnaceCategory(guiHelper));
        registration.addRecipeCategories(new BoilingCategory(guiHelper));
        registration.addRecipeCategories(new SteamEngineCategory(guiHelper));
        registration.addRecipeCategories(new CondensingCategory(guiHelper));
        registration.addRecipeCategories(new DrobitelCategory(guiHelper));
        registration.addRecipeCategories(new CoccerOvenCategory(guiHelper));
        registration.addRecipeCategories(new ChemicalPlantCategory(guiHelper));
        registration.addRecipeCategories(new VishelashivatelCategory(guiHelper));
        registration.addRecipeCategories(new CentrifugeCategory(guiHelper));
        registration.addRecipeCategories(new CentrifugeCylinderCategory(guiHelper));
        registration.addRecipeCategories(new ForgingCategory(guiHelper));
        registration.addRecipeCategories(new FluidContainerCategory(guiHelper));
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.LIQUID_METAL.get(),
                (stack, context) -> {
                    if (stack.hasTag() && stack.getTag().contains("MetalId")) {
                        return stack.getTag().getString("MetalId");
                    }
                    return IIngredientSubtypeInterpreter.NONE;
                });
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<SmeltingWrapper> smeltingRecipes = new ArrayList<>();

        for (var recipe : MetallurgyRegistry.getAllSmeltRecipes()) {
            ItemStack inputStack = new ItemStack(recipe.input());
            inputStack.setCount(recipe.inputCount());
            smeltingRecipes.add(new SmeltingWrapper(
                    inputStack,
                    recipe.output(),
                    recipe.outputUnits(),
                    recipe.minTemp(),
                    recipe.heatConsumption(),
                    recipe.smeltTimeTicks(),
                    recipe.inputCount()
            ));
        }

        for (Metal metal : MetallurgyRegistry.getAllMetals()) {
            ItemStack slag = SlagItem.createSlag(metal, MetalUnits2.UNITS_PER_INGOT);
            smeltingRecipes.add(new SmeltingWrapper(
                    slag,
                    metal,
                    MetalUnits2.UNITS_PER_INGOT,
                    metal.getMeltingPoint(),
                    metal.getHeatConsumptionPerTick(),
                    metal.calculateSmeltTimeForUnits(MetalUnits2.UNITS_PER_INGOT),
                    1
            ));
        }

        registration.addRecipes(SMELTING_TYPE, smeltingRecipes);

        List<CastingWrapper> castingRecipes = new ArrayList<>();
        for (MoldRecipe mold : MoldRecipeRegistry.getAllRecipes()) {
            for (Metal metal : MetallurgyRegistry.getAllMetals()) {
                ItemStack output = mold.createOutput(metal);
                if (!output.isEmpty()) {
                    castingRecipes.add(new CastingWrapper(mold, metal, output, mold.getRequiredUnits()));
                }
            }
        }
        registration.addRecipes(CASTING_TYPE, castingRecipes);

        List<AlloyingWrapper> alloyingRecipes = new ArrayList<>();
        for (AlloyRecipe recipe : MetallurgyRegistry.getAllAlloyRecipes()) {
            alloyingRecipes.add(new AlloyingWrapper(recipe));
        }
        registration.addRecipes(ALLOYING_TYPE, alloyingRecipes);

        // === ЖЕРНОВА ===
        List<MillstoneWrapper> millstoneRecipes = new ArrayList<>();
        for (Map.Entry<Item, MillstoneBlockEntity.GrindRecipe> entry : MillstoneBlockEntity.RECIPES.entrySet()) {
            List<ItemStack> outputs = entry.getValue().outputs().stream()
                    .map(ItemStack::copy)
                    .toList();
            millstoneRecipes.add(new MillstoneWrapper(
                    entry.getKey(),
                    outputs,
                    entry.getValue().grindsRequired()
            ));
        }
        registration.addRecipes(MILLSTONE_TYPE, millstoneRecipes);

        // === ДРОБИТЕЛЬ ===
        List<DrobitelWrapper> drobitelRecipes = new ArrayList<>();
        for (Map.Entry<Item, List<ItemStack>> entry : DrobitelBlockEntity.RECIPES.entrySet()) {
            drobitelRecipes.add(new DrobitelWrapper(
                    entry.getKey(),
                    entry.getValue().stream().map(ItemStack::copy).toList()
            ));
        }
        registration.addRecipes(DROBITEL_TYPE, drobitelRecipes);

        // === ЭЛЕКТРО-ПЕЧЬ ===
        List<ElectricFurnaceWrapper> electricRecipes = new ArrayList<>();
        var recipeManager = Minecraft.getInstance().level.getRecipeManager();

        for (var recipe : recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
            net.minecraft.world.item.crafting.AbstractCookingRecipe cookingRecipe =
                    (net.minecraft.world.item.crafting.AbstractCookingRecipe) recipe;
            electricRecipes.add(new ElectricFurnaceWrapper(
                    cookingRecipe,
                    (int) (cookingRecipe.getCookingTime() * 0.7f),
                    5
            ));
        }
        registration.addRecipes(ELECTRIC_FURNACE_TYPE, electricRecipes);

        // === БОЙЛЕР ===
        List<BoilingWrapper> boilingRecipes = new ArrayList<>();
        ItemStack waterDrop = new ItemStack(ModFluids.FLUID_DROP_WATER.get());
        ItemStack steamDrop = new ItemStack(ModFluids.getFluidDrop(ModFluids.STEAM_TYPE.get()));
        boilingRecipes.add(new BoilingWrapper(waterDrop, steamDrop, 100));
        registration.addRecipes(BOILING_TYPE, boilingRecipes);

        // === ПАРОВОЙ ДВИГАТЕЛЬ ===
        List<SteamEngineWrapper> steamEngineRecipes = new ArrayList<>();
        ItemStack lowPressureDrop = new ItemStack(ModFluids.getFluidDrop(ModFluids.LOW_PRESSURE_STEAM_TYPE.get()));
        steamEngineRecipes.add(new SteamEngineWrapper(steamDrop.copy(), lowPressureDrop));
        registration.addRecipes(STEAM_ENGINE_TYPE, steamEngineRecipes);

        // === КОНДЕНСАТОР ===
        List<CondensingWrapper> condensingRecipes = new ArrayList<>();
        condensingRecipes.add(new CondensingWrapper(lowPressureDrop.copy(), waterDrop.copy()));
        registration.addRecipes(CONDENSING_TYPE, condensingRecipes);

        // === КОКСОВАЯ ПЕЧЬ ===
        List<CoccerOvenWrapper> coccerRecipes = new ArrayList<>();
        for (CoccerOvenRecipe recipe : CoccerOvenRecipeRegistry.getAllRecipes()) {
            coccerRecipes.add(new CoccerOvenWrapper(recipe));
        }
        registration.addRecipes(COCCER_OVEN_TYPE, coccerRecipes);

        // === ХИМИЧЕСКАЯ УСТАНОВКА ===
        List<ChemicalPlantWrapper> chemicalPlantRecipes = new ArrayList<>();
        for (ChemicalPlantRecipe recipe : ChemicalPlantRecipeRegistry.getAllRecipes()) {
            chemicalPlantRecipes.add(new ChemicalPlantWrapper(recipe));
        }
        registration.addRecipes(CHEMICAL_PLANT_TYPE, chemicalPlantRecipes);

        // === ВЫЩЕЛАЩИВАТЕЛЬ ===
        List<VishelashivatelWrapper> vishelashivatelRecipes = new ArrayList<>();
        for (com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipe recipe :
                com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipes.getAllRecipes()) {
            vishelashivatelRecipes.add(new VishelashivatelWrapper(recipe));
        }
        registration.addRecipes(VISHELASHIVATEL_TYPE, vishelashivatelRecipes);

        // === ЦЕНТРИФУГА ===
        List<CentrifugeWrapper> centrifugeRecipes = new ArrayList<>();
        for (CentrifugeRecipe recipe :
                CentrifugeRecipes.getAllRecipes()) {
            centrifugeRecipes.add(new CentrifugeWrapper(recipe));
        }
        registration.addRecipes(CENTRIFUGE_TYPE, centrifugeRecipes);

        // === ЖИДКОСТНАЯ ЦЕНТРИФУГА ===
        List<CentrifugeCylinderWrapper> centrifugeCylinderRecipes = new ArrayList<>();
        for (CentrifugeCylinderRecipe recipe :
                CentrifugeCylinderRecipes.getAllRecipes()) {
            centrifugeCylinderRecipes.add(new CentrifugeCylinderWrapper(recipe));
        }
        registration.addRecipes(CENTRIFUGE_CYLINDER_TYPE, centrifugeCylinderRecipes);

        // === РУЧНАЯ КОВКА НА НАКОВАЛЬНЕ ===
        List<ForgingWrapper> forgingRecipes = new ArrayList<>();
        MoldRecipe plateRecipe = MoldRecipeRegistry.getRecipe(ModItems.MOLD_PLATE.get());
        if (plateRecipe != null) {
            for (Metal metal : MetallurgyRegistry.getAllMetals()) {
                // Для ковки нужен слиток и доступная пластина
                if (metal.getIngot() == null) continue;
                ItemStack plate = plateRecipe.createOutput(metal);
                if (plate.isEmpty()) continue;

                // Нагретый до необходимой температуры слиток
                ItemStack hotIngot = new ItemStack(metal.getIngot());
                HotItemHandler.setHot(hotIngot, metal.getMeltingPoint(), false);

                // Нагретая пластина (перенос тегов нагрева, как в AnvilForgeEventHandler)
                ItemStack hotPlate = plate.copy();
                HotItemHandler.setHot(hotPlate, metal.getMeltingPoint(), false);

                // Молот: целый на входе и с -1 прочности (1 урона) на выходе
                ItemStack hammer = new ItemStack(ModItems.HAMMER.get());
                ItemStack hammerDamaged = new ItemStack(ModItems.HAMMER.get());
                hammerDamaged.setDamageValue(1);

                int requiredTemp = (int) (metal.getMeltingPoint() * 0.15f);
                forgingRecipes.add(new ForgingWrapper(hotIngot, hammer, hotPlate, hammerDamaged, metal, requiredTemp));
            }
        }
        registration.addRecipes(FORGING_TYPE, forgingRecipes);

        // === ЖИДКОСТЬ ↔ КОНТЕЙНЕР (пипетки / жидкостные контейнеры) ===
        List<FluidContainerWrapper> fluidContainerRecipes = new ArrayList<>();
        Item[] containers = {
                ModItems.PIPETTE.get(),
                ModItems.PIPETTE_IDUSTRIAL.get(),
                ModItems.FLUID_TANK_IRON.get()
        };
        for (Item container : containers) {
            if (!(container instanceof FluidContainerItem fci)) continue;
            for (Fluid fluid : ModFluids.getAllSourceFluids()) {
                ItemStack filled = FluidContainerItem.createFilled(container, fluid);
                if (filled.isEmpty()) continue; // несовместимая жидкость — контейнер растворится
                int cap = fci.getCapacity();
                ItemStack drop = fluidDropStack(new FluidStack(fluid, cap));
                if (drop.isEmpty()) continue;
                // Жидкость → заполненный контейнер
                fluidContainerRecipes.add(new FluidContainerWrapper(drop, filled.copy(), true));
                // Заполненный контейнер → жидкость
                fluidContainerRecipes.add(new FluidContainerWrapper(filled.copy(), drop, false));
            }
        }
        registration.addRecipes(FLUID_CONTAINER_TYPE, fluidContainerRecipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SMELTER.get()), SMELTING_TYPE, ALLOYING_TYPE, CASTING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SMALL_SMELTER.get()), SMELTING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.trd.block.basic.ModBlocks.JERNOVA.get()), MILLSTONE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get()), ELECTRIC_FURNACE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.BOILER_ITEM.get()), BOILING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.STEAM_ENGINE_ITEM.get()), STEAM_ENGINE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get()), CONDENSING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DROBITEL.get()), DROBITEL_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.COCCER_OVEN.get()), COCCER_OVEN_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CHEMICAL_PLANT_REACTION_CHAMBER.get()), CHEMICAL_PLANT_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.VISHELASHIVATEL.get()), VISHELASHIVATEL_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CENTRIFUGE_CONUS.get()), CENTRIFUGE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CENTRIFUGE_CYLINDER.get()), CENTRIFUGE_CYLINDER_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CENTRIFUGE_MOTOR.get()), CENTRIFUGE_TYPE, CENTRIFUGE_CYLINDER_TYPE);
        registration.addRecipeCatalyst(new ItemStack(net.minecraft.world.level.block.Blocks.ANVIL), FORGING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.PIPETTE.get()), FLUID_CONTAINER_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.PIPETTE_IDUSTRIAL.get()), FLUID_CONTAINER_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.FLUID_TANK_IRON.get()), FLUID_CONTAINER_TYPE);
    }

    private static ItemStack createLiquidMetalStack(Metal metal, int amount) {
        ItemStack stack = new ItemStack(ModItems.LIQUID_METAL.get());
        stack.getOrCreateTag().putString("MetalId", metal.getId().toString());
        stack.getTag().putInt("Amount", amount);
        stack.getTag().putInt("MetalColor", metal.getColor());
        return stack;
    }

    public static ItemStack fluidDropStack(FluidStack fluid) {
        Item drop = ModFluids.getFluidDrop(fluid.getFluid().getFluidType());
        if (drop == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(drop);
        // Актуальный объём для тултипа (FluidDropItem читает этот тег)
        stack.getOrCreateTag().putInt("FluidVolume", fluid.getAmount());
        return stack;
    }

    // ==================== КАТЕГОРИИ ====================

    public static class BoilingCategory implements IRecipeCategory<BoilingWrapper> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui2.png");

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public BoilingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 76, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModItems.BOILER_ITEM.get()));
            this.title = Component.translatable("jei.category.trd.boiling");
        }

        @Override public RecipeType<BoilingWrapper> getRecipeType() { return BOILING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BoilingWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.waterInput());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                    .addItemStack(recipe.steamOutput());
        }

        @Override
        public void draw(BoilingWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            String tempText = recipe.tempC() + "°C";
            gg.drawString(font, tempText, 5, 43, 0xFF555555, false);
        }
    }

    public static class SteamEngineCategory implements IRecipeCategory<SteamEngineWrapper> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui2.png");

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public SteamEngineCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 76, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModItems.STEAM_ENGINE_ITEM.get()));
            this.title = Component.translatable("jei.category.trd.steam_engine");
        }

        @Override public RecipeType<SteamEngineWrapper> getRecipeType() { return STEAM_ENGINE_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SteamEngineWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.steamInput());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                    .addItemStack(recipe.lowPressureOutput());
        }

        @Override
        public void draw(SteamEngineWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            // Без текста
        }
    }

    public static class CondensingCategory implements IRecipeCategory<CondensingWrapper> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui2.png");

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CondensingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 76, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get()));
            this.title = Component.translatable("jei.category.trd.condensing");
        }

        @Override public RecipeType<CondensingWrapper> getRecipeType() { return CONDENSING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CondensingWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.lowPressureInput());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                    .addItemStack(recipe.waterOutput());
        }

        @Override
        public void draw(CondensingWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            // Без текста
        }
    }

    public static class MillstoneCategory implements IRecipeCategory<MillstoneWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public MillstoneCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_102x60, 0, 0, 102, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(com.trd.block.basic.ModBlocks.JERNOVA.get()));
            this.title = Component.translatable("jei.category.trd.millstone");
        }

        @Override public RecipeType<MillstoneWrapper> getRecipeType() { return MILLSTONE_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, MillstoneWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(new ItemStack(recipe.input()));

            List<ItemStack> outputs = recipe.outputs();
            for (int i = 0; i < outputs.size() && i < CHAMBER_OUTPUT_SLOTS.length; i++) {
                ItemStack stack = outputs.get(i);
                if (!stack.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, CHAMBER_OUTPUT_SLOTS[i][0], CHAMBER_OUTPUT_SLOTS[i][1])
                            .addItemStack(stack.copy());
                }
            }
        }

        @Override
        public void draw(MillstoneWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            String grindsText = recipe.grindsRequired() + " об";
            gg.drawString(font, grindsText, 24, 33, 0xFF555555, false);
        }
    }

    public static class SmeltingCategory implements IRecipeCategory<SmeltingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;
        private final List<ItemStack> machines;

        public SmeltingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.CASTING_DESCENT.get()));
            this.title = Component.translatable("jei.category.trd.smelting");
            this.machines = Arrays.asList(
                    new ItemStack(ModBlocks.SMELTER.get()),
                    new ItemStack(ModBlocks.SMALL_SMELTER.get())
            );
        }

        @Override public RecipeType<SmeltingWrapper> getRecipeType() { return SMELTING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SmeltingWrapper recipe, IFocusGroup focuses) {
            ItemStack displayInput = recipe.input().copy();
            displayInput.setCount(recipe.inputCount());
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13).addItemStack(displayInput);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.outputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13).addItemStack(liquidMetal);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }

        @Override
        public void draw(SmeltingWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            long sec = System.currentTimeMillis() / 1000;
            ItemStack machine = machines.get((int) (sec % machines.size()));
            gg.renderItem(machine, 52, 13);
            gg.renderItemDecorations(Minecraft.getInstance().font, machine, 52, 13);

            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.temp() + "°C", 42, 41, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.timeTicks() / 20f), 42, 51, 0xFF555555, false);
        }
    }

    public static class CastingCategory implements IRecipeCategory<CastingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;
        private final List<ItemStack> machines;
        public CastingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.CASTING_POT.get()));
            this.title = Component.translatable("jei.category.trd.casting");
            this.machines = Arrays.asList(
                    new ItemStack(ModBlocks.SMELTER.get()),
                    new ItemStack(ModBlocks.SMALL_SMELTER.get())
            );
        }

        @Override public RecipeType<CastingWrapper> getRecipeType() { return CASTING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CastingWrapper recipe, IFocusGroup focuses) {
            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.requiredUnits());
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13).addItemStack(liquidMetal);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            builder.addSlot(RecipeIngredientRole.INPUT, 52, 13)
                    .addItemStack(new ItemStack(recipe.mold().getMoldItem()));

            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13).addItemStack(recipe.output());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }
    }

    public static class AlloyingCategory implements IRecipeCategory<AlloyingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public AlloyingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_alloy_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.SMELTER.get()));
            this.title = Component.translatable("jei.category.trd.alloying");
        }

        @Override public RecipeType<AlloyingWrapper> getRecipeType() { return ALLOYING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AlloyingWrapper wrapper, IFocusGroup focuses) {
            AlloyRecipe recipe = wrapper.recipe();
            AlloySlot[] slots = recipe.getSlots();
            int[] xs = {5, 23, 41, 59};

            for (int i = 0; i < 4; i++) {
                if (slots[i].item() != null && slots[i].count() > 0) {
                    var slotBuilder = builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22);
                    // Показываем все допустимые варианты предмета (альтернативы циклически)
                    for (Item alt : slots[i].items()) {
                        slotBuilder.addItemStack(new ItemStack(alt, slots[i].count()));
                    }
                } else {
                    builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22);
                }
            }

            ItemStack liquidMetal = createLiquidMetalStack(recipe.getOutputMetal(), recipe.getOutputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 22).addItemStack(liquidMetal);
        }

        @Override
        public void draw(AlloyingWrapper wrapper, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            AlloyRecipe recipe = wrapper.recipe();
            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.getOutputMetal().getMeltingPoint() + "°C", 4, 42, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.getSmeltTimeTicks() / 20f), 4, 52, 0xFF555555, false);
        }
    }

    public static class ElectricFurnaceCategory implements IRecipeCategory<ElectricFurnaceWrapper> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_electric_furnace_gui.png");

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public ElectricFurnaceCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 76, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(com.trd.block.basic.ModBlocks.ELECTRO_FURNACE.get()));
            this.title = Component.translatable("jei.category.trd.electric_furnace");
        }

        @Override public RecipeType<ElectricFurnaceWrapper> getRecipeType() { return ELECTRIC_FURNACE_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ElectricFurnaceWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addIngredients(recipe.recipe().getIngredients().get(0));

            builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                    .addItemStack(recipe.recipe().getResultItem(
                            Minecraft.getInstance().level.registryAccess()));
        }

        @Override
        public void draw(ElectricFurnaceWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            int cookTime = recipe.cookTime();
            int progress = (int) ((System.currentTimeMillis() / 50) % cookTime);
            int barWidth = (int) (24.0 * progress / cookTime);
            if (barWidth > 0) {
                gg.blit(TEXTURE, 26, 27, 77, 0, barWidth, 6);
            }

            var font = Minecraft.getInstance().font;
            float xp = recipe.recipe().getExperience();
            if (xp > 0) {
                String xpText;
                if (xp == (int) xp) {
                    xpText = String.format("%d XP", (int) xp);
                } else {
                    xpText = String.format("%.1f XP", xp);
                }
                gg.drawString(font, xpText, 5, 10, 0xFF555555, false);
            }

            String info = String.format("%.1fs | %d JE/t", cookTime / 20f, recipe.energyPerTick());
            gg.drawString(font, info, 5, 43, 0xFF555555, false);
        }
    }

    public static class DrobitelCategory implements IRecipeCategory<DrobitelWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public DrobitelCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_102x60, 0, 0, 102, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.DROBITEL.get()));
            this.title = Component.translatable("jei.category.trd.drobitel");
        }

        @Override public RecipeType<DrobitelWrapper> getRecipeType() { return DROBITEL_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, DrobitelWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(new ItemStack(recipe.input()));

            List<ItemStack> outputs = recipe.outputs();
            for (int i = 0; i < outputs.size() && i < CHAMBER_OUTPUT_SLOTS.length; i++) {
                ItemStack stack = outputs.get(i);
                if (!stack.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, CHAMBER_OUTPUT_SLOTS[i][0], CHAMBER_OUTPUT_SLOTS[i][1])
                            .addItemStack(stack.copy());
                }
            }
        }

        @Override
        public void draw(DrobitelWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            // Автоматизирован — обороты не показываем
        }
    }

    public static class CoccerOvenCategory implements IRecipeCategory<CoccerOvenWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CoccerOvenCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_COKE_OVEN, 0, 0, 90, 64);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.COCCER_OVEN.get()));
            this.title = Component.translatable("jei.category.trd.coccer_oven");
        }

        @Override public RecipeType<CoccerOvenWrapper> getRecipeType() { return COCCER_OVEN_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CoccerOvenWrapper wrapper, IFocusGroup focuses) {
            CoccerOvenRecipe recipe = wrapper.recipe();

            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(new ItemStack(recipe.getInput()));

            int cell = 0;
            if (recipe.hasItemOutput()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                        .addItemStack(recipe.getOutputItem().copy());
                cell++;
            }
            if (recipe.hasFluidOutput() && cell < 2) {
                ItemStack drop = fluidDropStack(recipe.getOutputFluid());
                if (!drop.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, 73, 22)
                            .addItemStack(drop);
                }
            }
        }

        @Override
        public void draw(CoccerOvenWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.recipe().getRequiredTemp() + "°C", 26, 33, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.recipe().getBaseTicks() / 20f), 26, 43, 0xFF555555, false);
        }
    }

    public static class ChemicalPlantCategory implements IRecipeCategory<ChemicalPlantWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public ChemicalPlantCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_140x44, 0, 0, 140, 44);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.CHEMICAL_PLANT_REACTION_CHAMBER.get()));
            this.title = Component.translatable("jei.category.trd.chemical_plant");
        }

        @Override public RecipeType<ChemicalPlantWrapper> getRecipeType() { return CHEMICAL_PLANT_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ChemicalPlantWrapper wrapper, IFocusGroup focuses) {
            ChemicalPlantRecipe recipe = wrapper.recipe();

            int cell = 0;
            for (ItemStack stack : recipe.getItemInputs()) {
                if (cell >= GRID_INPUT_SLOTS.length || stack.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.INPUT, GRID_INPUT_SLOTS[cell][0], GRID_INPUT_SLOTS[cell][1])
                        .addItemStack(stack.copy());
                cell++;
            }
            for (FluidStack fluid : recipe.getFluidInputs()) {
                if (cell >= GRID_INPUT_SLOTS.length) break;
                ItemStack drop = fluidDropStack(fluid);
                if (drop.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.INPUT, GRID_INPUT_SLOTS[cell][0], GRID_INPUT_SLOTS[cell][1])
                        .addItemStack(drop);
                cell++;
            }

            cell = 0;
            for (ItemStack stack : recipe.getItemOutputs()) {
                if (cell >= GRID_OUTPUT_SLOTS.length || stack.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.OUTPUT, GRID_OUTPUT_SLOTS[cell][0], GRID_OUTPUT_SLOTS[cell][1])
                        .addItemStack(stack.copy());
                cell++;
            }
            for (FluidStack fluid : recipe.getFluidOutputs()) {
                if (cell >= GRID_OUTPUT_SLOTS.length) break;
                ItemStack drop = fluidDropStack(fluid);
                if (drop.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.OUTPUT, GRID_OUTPUT_SLOTS[cell][0], GRID_OUTPUT_SLOTS[cell][1])
                        .addItemStack(drop);
                cell++;
            }
        }

        @Override
        public void draw(ChemicalPlantWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.recipe().getMinTemperature() + "°C", 60, 26, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.recipe().getProcessTime() / 20f), 60, 34, 0xFF555555, false);
        }
    }

    public static class VishelashivatelCategory implements IRecipeCategory<VishelashivatelWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public VishelashivatelCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_130x60, 0, 0, 130, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.VISHELASHIVATEL.get()));
            this.title = Component.translatable("jei.category.trd.vishelashivatel");
        }

        @Override public RecipeType<VishelashivatelWrapper> getRecipeType() { return VISHELASHIVATEL_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, VishelashivatelWrapper wrapper, IFocusGroup focuses) {
            com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipe recipe = wrapper.recipe();

            // Входной предмет
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.getItemInput().copy());

            // Требуемая жидкость (каплей)
            ItemStack fluidDrop = fluidDropStack(recipe.getRequiredFluid());
            if (!fluidDrop.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 23, 22)
                        .addItemStack(fluidDrop);
            }

            // Выходы — до 3 шт в ряд
            List<ItemStack> outputs = recipe.getItemOutputs();
            for (int i = 0; i < outputs.size() && i < 3; i++) {
                ItemStack out = outputs.get(i);
                if (!out.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, 73 + i * 18, 22)
                            .addItemStack(out.copy());
                }
            }
        }

        @Override
        public void draw(VishelashivatelWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.recipe().getMinRpm() + " об/мин", 4, 42, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.recipe().getProcessTime() / 20f), 4, 50, 0xFF555555, false);
        }
    }

    // Макет 102x60: вход на (5,22), выходы 2x2 на (63,13), текст на (24,33)
    public static class CentrifugeCategory implements IRecipeCategory<CentrifugeWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CentrifugeCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_102x60, 0, 0, 102, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.CENTRIFUGE_CONUS.get()));
            this.title = Component.translatable("jei.category.trd.centrifuge");
        }

        @Override public RecipeType<CentrifugeWrapper> getRecipeType() { return CENTRIFUGE_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CentrifugeWrapper wrapper, IFocusGroup focuses) {
            CentrifugeRecipe recipe = wrapper.recipe();

            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.getInput().copy());

            List<ItemStack> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size() && i < CHAMBER_OUTPUT_SLOTS.length; i++) {
                ItemStack stack = outputs.get(i);
                if (!stack.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, CHAMBER_OUTPUT_SLOTS[i][0], CHAMBER_OUTPUT_SLOTS[i][1])
                            .addItemStack(stack.copy());
                }
            }
        }

        @Override
        public void draw(CentrifugeWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            gg.drawString(font, String.format("%.1fs", recipe.recipe().getProcessTime() / 20f), 24, 33, 0xFF555555, false);
        }
    }

    // Жидкостная центрифуга — шаблон gui3 (102x60): вход на (5,22), выходы 2x2 на (63,13)
    public static class CentrifugeCylinderCategory implements IRecipeCategory<CentrifugeCylinderWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CentrifugeCylinderCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE_UNIVERSAL_102x60, 0, 0, 102, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.CENTRIFUGE_CYLINDER.get()));
            this.title = Component.translatable("jei.category.trd.centrifuge_cylinder");
        }

        @Override public RecipeType<CentrifugeCylinderWrapper> getRecipeType() { return CENTRIFUGE_CYLINDER_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CentrifugeCylinderWrapper wrapper, IFocusGroup focuses) {
            CentrifugeCylinderRecipe recipe = wrapper.recipe();

            // Входная жидкость (каплей) с количеством
            ItemStack fluidDrop = fluidDropStack(recipe.getInputFluid());
            if (!fluidDrop.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                        .addItemStack(fluidDrop);
            }

            // Выходы: сначала жидкости, затем предметы — до 4 ячеек 2x2
            int cell = 0;
            for (FluidStack fluid : recipe.getFluidOutputs()) {
                if (cell >= CHAMBER_OUTPUT_SLOTS.length) break;
                ItemStack drop = fluidDropStack(fluid);
                if (drop.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.OUTPUT, CHAMBER_OUTPUT_SLOTS[cell][0], CHAMBER_OUTPUT_SLOTS[cell][1])
                        .addItemStack(drop);
                cell++;
            }
            for (ItemStack stack : recipe.getItemOutputs()) {
                if (cell >= CHAMBER_OUTPUT_SLOTS.length || stack.isEmpty()) continue;
                builder.addSlot(RecipeIngredientRole.OUTPUT, CHAMBER_OUTPUT_SLOTS[cell][0], CHAMBER_OUTPUT_SLOTS[cell][1])
                        .addItemStack(stack.copy());
                cell++;
            }
        }

        @Override
        public void draw(CentrifugeCylinderWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            var font = Minecraft.getInstance().font;
            gg.drawString(font, String.format("%.1fs", recipe.recipe().getProcessTime() / 20f), 24, 33, 0xFF555555, false);
        }
    }

    // === РУЧНАЯ КОВКА НА НАКОВАЛЬНЕ ===
    // Шаблон jei_cast_gui.png (120x60), как у плавки и литья:
    // входы на (5,13)/(23,13), наковальня в центре (52,13), выходы на (81,13)/(99,13)
    public static class ForgingCategory implements IRecipeCategory<ForgingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public ForgingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(net.minecraft.world.level.block.Blocks.ANVIL));
            this.title = Component.translatable("jei.category.trd.forging");
        }

        @Override public RecipeType<ForgingWrapper> getRecipeType() { return FORGING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ForgingWrapper recipe, IFocusGroup focuses) {
            // Нагретый слиток
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13).addItemStack(recipe.hotIngot());
            // Молот
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13).addItemStack(recipe.hammer());
            // Пустые ячейки справа от наковальни
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            // Нагретая пластина
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13).addItemStack(recipe.hotPlate());
            // Тот же молот, но с -1 прочности
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13).addItemStack(recipe.hammerDamaged());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }

        @Override
        public void draw(ForgingWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            // Наковальня в серединном слоте (аналог машины в плавке/литье)
            ItemStack anvil = new ItemStack(net.minecraft.world.level.block.Blocks.ANVIL);
            gg.renderItem(anvil, 52, 13);
            gg.renderItemDecorations(Minecraft.getInstance().font, anvil, 52, 13);

            var font = Minecraft.getInstance().font;
            // Необходимая температура ковки
            gg.drawString(font, recipe.requiredTemp() + "°C", 42, 41, 0xFF555555, false);
            // Нагрев до температуры плавления
            gg.drawString(font, recipe.metal().getMeltingPoint() + "°C", 42, 51, 0xFF555555, false);
        }
    }

    // === ЖИДКОСТЬ ↔ КОНТЕЙНЕР (пипетки / жидкостные контейнеры) ===
    // Шаблон jei_universal_gui2.png (76x60): вход на (5,22), выход на (55,22)
    public static class FluidContainerCategory implements IRecipeCategory<FluidContainerWrapper> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/jei/jei_universal_gui2.png");

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public FluidContainerCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 76, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModItems.FLUID_TANK_IRON.get()));
            this.title = Component.translatable("jei.category.trd.fluid_container");
        }

        @Override public RecipeType<FluidContainerWrapper> getRecipeType() { return FLUID_CONTAINER_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FluidContainerWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 22)
                    .addItemStack(recipe.input());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 55, 22)
                    .addItemStack(recipe.output());
        }

        @Override
        public void draw(FluidContainerWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
        }
    }
}