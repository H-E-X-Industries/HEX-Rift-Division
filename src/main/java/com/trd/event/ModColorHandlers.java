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

        // Затемнение твёрдых блоков к эпицентру/краю воронки: чем выше DARKNESS, тем темнее.
        // Максимум — общий уровень затемнения кратера (50%, см. CraterTints.MAX_DARKNESS_RATIO).
        event.register((state, level, pos, tintIndex) -> {
            int dark = state.getValue(CraterBasaltBlock.DARKNESS);
            float f = 1.0f - com.trd.client.render.CraterTints.MAX_DARKNESS_RATIO
                    * (dark / (float) CraterBasaltBlock.MAX_DARK);
            int c = (int) (255.0f * f);
            return 0xFF000000 | (c << 16) | (c << 8) | c;
        }, ModBlocks.BASALT_SOFT.get(), ModBlocks.BASALT_SOFT_2.get(), ModBlocks.BASALT_SOFT_3.get(), ModBlocks.WASTE_GRASS.get());

        // Затемнение ванильных твёрдых блоков в кольце у края воронки (копии моделей
        // с тинт-индексом подменяются в CraterTints.onModelBake, позицию знает только клиент).
        event.register((state, level, pos, tintIndex) ->
                com.trd.client.render.CraterTints.tintColor(level, pos),
                com.trd.client.render.CraterTints.TINTED_BLOCKS);
    }

    // Красим предмет в инвентаре
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            return FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());
    }
}
