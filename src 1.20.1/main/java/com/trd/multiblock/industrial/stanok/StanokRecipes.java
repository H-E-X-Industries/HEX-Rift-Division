package com.trd.multiblock.industrial.stanok;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import com.trd.main.ResourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Регистрирует все рецепты для станка (stanok).
 * Вызывается из MainRegistry.commonSetup().
 */
public class StanokRecipes {

    public static void register() {
        // ─────────────────────────────────────────────────────────────────
        // 1. Прессовка пластины из промышленной меди
        //    industrial_copper_ingot → industrial_copper_plate
        //    Насадка: PRESS | 100 RPM | 300 Нм
        //    Время: 60 тиков стартовое (разгон до 20 тиков за 4 операции)
        // ─────────────────────────────────────────────────────────────────
        StanokRecipeRegistry.register(new StanokRecipe(
                new ResourceLocation("trd", "press_copper_plate"),
                CarriageType.PRESS,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("industrial_copper"), 1)),
                List.of(new ItemStack(ModItems.INDUSTRIAL_COPPER_PLATE.get(), 1)),
                100L,
                300L,
                60 // начальное время (3 сек); логика разгона в BlockEntity
        ));

        // ─────────────────────────────────────────────────────────────────
        // 2. Прокатка проводов из промышленной меди
        //    industrial_copper_plate → copper_coil ×8
        //    Насадка: WIRE | 200 RPM | 100 Нм | 20 тиков (1 сек)
        // ─────────────────────────────────────────────────────────────────
        StanokRecipeRegistry.register(new StanokRecipe(
                new ResourceLocation("trd", "wire_copper_coil"),
                CarriageType.WIRE,
                List.of(new ItemStack(ModItems.INDUSTRIAL_COPPER_PLATE.get(), 1)),
                List.of(new ItemStack(ModItems.COPPER_COIL.get(), 8)),
                200L,
                100L,
                20 // 1 секунда
        ));

        // ─────────────────────────────────────────────────────────────────
        // 3. Вытачивание лёгкого титанового вала
        //    titanium_ingot ×4 → shaft_light_titanium
        //    Насадка: FREZA | 500 RPM | 500 Нм | 160 тиков (8 сек)
        // ─────────────────────────────────────────────────────────────────
        StanokRecipeRegistry.register(new StanokRecipe(
                new ResourceLocation("trd", "freza_titanium_shaft"),
                CarriageType.FREZA,
                List.of(new ItemStack(ResourceRegistry.getMainUnit("titanium"), 4)),
                List.of(new ItemStack(ModBlocks.SHAFT_LIGHT_TITANIUM.get().asItem(), 1)),
                500L,
                500L,
                160 // 8 секунд
        ));
    }
}
