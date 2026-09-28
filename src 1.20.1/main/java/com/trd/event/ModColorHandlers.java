package com.trd.event;

import com.trd.main.MainRegistry;
import com.trd.block.basic.CraterBasaltBlock;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Blocks;
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

        // Затемнение кратерных блоков свойством DARKNESS (мягкий базальт всех вариантов +
        // выжженная земля): ступень хранится прямо в BlockState, поэтому переживает перезаход
        // и сбрасывается, если блок сломали и поставили заново.
        event.register((state, level, pos, tintIndex) -> {
            int dark = state.getValue(CraterBasaltBlock.DARKNESS);
            float f = 1.0f - com.trd.client.render.CraterTints.MAX_DARKNESS_RATIO
                    * (dark / (float) CraterBasaltBlock.MAX_DARK);
            int c = (int) (255.0f * f);
            return 0xFF000000 | (c << 16) | (c << 8) | c;
        }, ModBlocks.BASALT_SOFT.get(), ModBlocks.BASALT_SOFT_2.get(),
                ModBlocks.BASALT_SOFT_3.get(), ModBlocks.BASALT_SOFT_4.get(), ModBlocks.WASTE_GRASS.get());

        // Дёрн: у него СВОЙ биомный тинт (зелень травы), поэтому в глобальном EXCLUDED
        // он сознательно пропущен, чтобы не перебить зелень. Вместо этого даём ему
        // композицию: биомный цвет травы × множитель затемнения кратера. Вне базы
        // затемнения множитель = 1.0 → обычный биомный цвет, внутри — тёмная зелень.
        event.register((state, level, pos, tintIndex) -> {
            int biome = level != null && pos != null
                    ? BiomeColors.getAverageGrassColor(level, pos)
                    : FoliageColor.getDefaultColor();
            float f = com.trd.client.render.CraterTints.darknessMultiplier(level, pos);
            if (f >= 1.0f) return biome;
            int r = (int) ((biome >> 16 & 0xFF) * f);
            int g = (int) ((biome >> 8 & 0xFF) * f);
            int b = (int) ((biome & 0xFF) * f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }, Blocks.GRASS_BLOCK);

        // Позиционный тинт ВСЕХ остальных твёрдых блоков регистрируется в
        // CraterTints.onModelBake после сборки моделей (см. TintableModel).
    }

    // Красим предмет в инвентаре
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            return FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());
    }
}