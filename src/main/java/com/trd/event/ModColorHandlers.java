package com.trd.event; // Поменяй на свой пакет

import com.trd.main.MainRegistry;
import com.trd.block.basic.CraterBasaltBlock;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.trd.block.basic.ModBlocks;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    // Красим блок в мире
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            return level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());

        // Затемнение мягкого базальта к эпицентру: чем выше DARKNESS, тем темнее.
        // Максимум — 50% темноты (канал падает до 127) у самого центра кратера.
        event.register((state, level, pos, tintIndex) -> {
            int dark = state.getValue(CraterBasaltBlock.DARKNESS);
            float f = 1.0f - 0.5f * (dark / (float) CraterBasaltBlock.MAX_DARK);
            int c = (int) (255.0f * f);
            return 0xFF000000 | (c << 16) | (c << 8) | c;
        }, ModBlocks.BASALT_SOFT.get(), ModBlocks.BASALT_SOFT_2.get(), ModBlocks.BASALT_SOFT_3.get());
    }

    // Красим предмет в инвентаре
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            return FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());
    }
}
