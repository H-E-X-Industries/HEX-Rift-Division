package com.trd.api.metallurgy.system;

import com.trd.api.metallurgy.system.recipe.AlloyRecipe;
import com.trd.api.metallurgy.system.recipe.SmeltRecipe;
import com.trd.api.recipe.ModRecipes;
import com.trd.item.conglomerates.MetalPieceItem;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.*;

public class MetallurgyRegistry {
    private static final Map<ResourceLocation, Metal> METALS = new LinkedHashMap<>();
    private static final Map<Item, SmeltRecipe> SMELT_RECIPES = new HashMap<>();
    private static final List<AlloyRecipe> ALLOY_RECIPES = new ArrayList<>();

    public static final int INGOT_SMELT_TIME = 60;   // 3 секунды
    public static final int BLOCK_SMELT_TIME = 180;  // 9 секунд
    public static final int NUGGET_SMELT_TIME = 20;  // 1 секунда

    public static Metal registerMetal(String name, int color, int meltingPoint,
                                      int baseUnits, int smallUnits, int blockUnits,
                                      float heatConsumptionPerTick) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name);
        Metal metal = new Metal(id, color, meltingPoint, baseUnits, smallUnits, blockUnits,
                heatConsumptionPerTick);
        METALS.put(id, metal);
        return metal;
    }

    public static void addSmeltRecipe(Item input, Metal output, int outputUnits,
                                      int minTemp, float heatConsumption, int timeTicks, int inputCount) {
        SmeltRecipe recipe = new SmeltRecipe(Ingredient.of(input), inputCount, output.getId(),
                outputUnits, minTemp, heatConsumption, timeTicks);
        SMELT_RECIPES.put(input, recipe);
    }

    public static void addSmeltRecipe(Item input, Metal output, int outputUnits,
                                      int minTemp, float heatConsumption, int timeTicks) {
        addSmeltRecipe(input, output, outputUnits, minTemp, heatConsumption, timeTicks, 1);
    }

    public static void generateStandardRecipes() {
        for (Metal metal : METALS.values()) {
            if (metal.getIngot() != null) {
                addSmeltRecipe(metal.getIngot(), metal, metal.getBaseUnits(),
                        metal.getMeltingPoint(), metal.getHeatConsumptionPerTick(),
                        INGOT_SMELT_TIME);
            }

            if (metal.getNugget() != null && metal.getSmallUnits() > 0) {
                float nuggetHeat = metal.getHeatConsumptionPerTick() / 3.0f;
                addSmeltRecipe(metal.getNugget(), metal, metal.getSmallUnits(),
                        metal.getMeltingPoint(), nuggetHeat, NUGGET_SMELT_TIME);
            }

            if (metal.getBlock() != null) {
                float blockHeat = metal.getHeatConsumptionPerTick() * 3.0f;
                addSmeltRecipe(metal.getBlock().asItem(), metal, metal.getBlockUnits(),
                        metal.getMeltingPoint(), blockHeat, BLOCK_SMELT_TIME);
            }
        }
    }

    public static void addAlloyRecipe(AlloyRecipe recipe) {
        ALLOY_RECIPES.add(recipe);
    }

    public static List<AlloyRecipe> getAllAlloyRecipes() {
        return Collections.unmodifiableList(ALLOY_RECIPES);
    }

    public static Collection<SmeltRecipe> getAllSmeltRecipes() {
        return Collections.unmodifiableCollection(SMELT_RECIPES.values());
    }

    public static Optional<Metal> get(ResourceLocation id) {
        return Optional.ofNullable(METALS.get(id));
    }

    public static Collection<Metal> getAllMetals() {
        return Collections.unmodifiableCollection(METALS.values());
    }

    public static SmeltRecipe getSmeltRecipe(ItemStack stack) {
        return getSmeltRecipe(null, stack);
    }

    public static SmeltRecipe getSmeltRecipe(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // Кусочек металла из тега NBT
        if (stack.getItem() instanceof MetalPieceItem) {
            String metalId = MetalPieceItem.getMetal(stack);
            if (metalId == null) return null;
            Metal metal = get(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, metalId)).orElse(null);
            if (metal == null || metal.getSmallUnits() <= 0) return null;
            float nuggetHeat = metal.getHeatConsumptionPerTick() / 3.0f;
            return new SmeltRecipe(Ingredient.of(stack.getItem()), 1, metal.getId(),
                    metal.getSmallUnits(), metal.getMeltingPoint(), nuggetHeat, NUGGET_SMELT_TIME);
        }

        // Поиск в JSON рецептах RecipeManager если доступен уровень
        if (level != null) {
            Optional<RecipeHolder<SmeltRecipe>> match = level.getRecipeManager()
                    .getRecipeFor(ModRecipes.SMELTER_MELTING_TYPE.get(), new SingleRecipeInput(stack), level);
            if (match.isPresent()) {
                return match.get().value();
            }
        }

        // Фолбэк на код-рецепты
        return SMELT_RECIPES.get(stack.getItem());
    }
}
