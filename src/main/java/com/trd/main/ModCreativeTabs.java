package com.trd.main;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MainRegistry.MOD_ID);

    public static final Supplier<CreativeModeTab> trd_RECOURSES_TAB = CREATIVE_MODE_TABS.register("trd_recourses_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_recourses_tab"))
                    .icon(() -> new ItemStack(ModItems.STEEL_PLATE.get()))
                    .displayItems((parameters, output) -> {
                        // Предметы (Ресурсы)
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.ALUMINUM_PLATE.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_PLATE.get());
                        output.accept(ModItems.SEQUESTRUM.get());
                        output.accept(ModItems.SALT.get());
                        output.accept(ModItems.SULFUR.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_NATURE_TAB = CREATIVE_MODE_TABS.register("trd_nature_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_nature_tab"))
                    .icon(() -> new ItemStack(ModBlocks.LIGNITE_ORE.get()))
                    .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "trd_recourses_tab"))
                    .displayItems((parameters, output) -> {
                        // Блоки (Природа / Руды)
                        output.accept(ModBlocks.ASBESOTS_ORE.get());
                        output.accept(ModBlocks.LIGNITE_ORE.get());
                        output.accept(ModBlocks.SALT_ORE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
