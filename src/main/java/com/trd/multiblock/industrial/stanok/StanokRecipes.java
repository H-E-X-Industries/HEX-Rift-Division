package com.trd.multiblock.industrial.stanok;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import com.trd.main.ResourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Регистрирует все рецепты для станка (stanok).
 * Вызывается из MainRegistry.commonSetup().
 */
public class StanokRecipes {

    public static void register() {
        // ═════════════════════════════════════════════════════════════════
        // ШТАМПОВКА ПЛАСТИН (PRESS CARRIAGE)
        // 100 RPM | 300 Нм | 60 тиков (стартовое время, разгон до 20 тиков)
        // ═════════════════════════════════════════════════════════════════

        // 1. Медная пластина (промышленная медь)
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_copper_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("industrial_copper"), 1)),
                List.of(new ItemStack(ModItems.INDUSTRIAL_COPPER_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 2. Железная пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_iron_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(Items.IRON_INGOT, 1)),
                List.of(new ItemStack(ModItems.IRON_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 3. Золотая пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_gold_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(Items.GOLD_INGOT, 1)),
                List.of(new ItemStack(ModItems.GOLD_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 4. Стальная пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_steel_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("steel"), 1)),
                List.of(new ItemStack(ModItems.STEEL_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 5. Алюминиевая пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_aluminum_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("aluminum"), 1)),
                List.of(new ItemStack(ModItems.ALUMINUM_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 6. Титановая пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_titanium_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("titanium"), 1)),
                List.of(new ItemStack(ModItems.TITANIUM_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 7. Свинцовая пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_lead_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("lead"), 1)),
                List.of(new ItemStack(ModItems.LEAD_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // 8. Вольфрамовая пластина
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "press_tungsten_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("tungsten"), 1)),
                List.of(new ItemStack(ModItems.TUNGSTEN_PLATE.get(), 1)),
                100L,
                300L,
                60
        ));

        // ═════════════════════════════════════════════════════════════════
        // ПРОКАТКА ПРОВОДОВ (WIRE CARRIAGE)
        // 200 RPM | 100 Нм | 20 тиков (1 сек)
        // ═════════════════════════════════════════════════════════════════

        // 1. Медный провод
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "wire_copper_wire"),
                CarriageType.WIRE,
                List.of(new ItemStack(ModItems.INDUSTRIAL_COPPER_PLATE.get(), 1)),
                List.of(new ItemStack(ModItems.INDUSTRIAL_COPPER_WIRE.get(), 8)),
                200L,
                100L,
                20
        ));

        // 2. Золотой провод
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "wire_gold_wire"),
                CarriageType.WIRE,
                List.of(new ItemStack(ModItems.GOLD_PLATE.get(), 1)),
                List.of(new ItemStack(ModItems.GOLD_WIRE.get(), 8)),
                200L,
                100L,
                20
        ));

        // 3. Неодимовый провод
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "wire_neodymium_wire"),
                CarriageType.WIRE,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("neodymium"), 1)),
                List.of(new ItemStack(ModItems.NEODYMIUM_WIRE.get(), 8)),
                200L,
                100L,
                20
        ));

        // ═════════════════════════════════════════════════════════════════
        // ФРЕЗЕРОВКА ВАЛОВ (FREZA CARRIAGE)
        // ═════════════════════════════════════════════════════════════════

        // 1. Вытачивание лёгкого титанового вала
        //    500 RPM | 200 Нм | 160 тиков (8 сек) | Вход: 2 титановых слитка
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "freza_titanium_shaft"),
                CarriageType.FREZA,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("titanium"), 2)),
                List.of(new ItemStack(ModBlocks.SHAFT_LIGHT_TITANIUM.get().asItem(), 1)),
                500L,
                200L,
                160
        ));

        // 2. Вытачивание лёгкого стального вала
        //    250 RPM | 100 Нм | 160 тиков (8 сек) | Вход: 2 стальных слитка
        StanokRecipeRegistry.register(new StanokRecipe(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "freza_steel_shaft"),
                CarriageType.FREZA,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("steel"), 2)),
                List.of(new ItemStack(ModBlocks.SHAFT_LIGHT_STEEL.get().asItem(), 1)),
                250L,
                100L,
                160
        ));
    }
}
