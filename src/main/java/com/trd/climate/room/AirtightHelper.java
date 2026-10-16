package com.trd.climate.room;

import com.trd.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Проверка блоков на герметичность (airtight).
 * Герметичными считаются:
 * - Блоки с тегом #trd:airtight
 * - Контроллер термоизоляции
 * - Любые закрытые двери и люки (включая железные и стальные)
 * - Остекление (блоки стекла и стеклянные панели: обычные, цветные, тонированные, бронированные)
 * - Полнотелые непрозрачные блоки (камень, бетон, кирпичи, дерево, металлы и др.)
 */
public final class AirtightHelper {

    public static final TagKey<Block> AIRTIGHT_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("trd", "airtight"));

    private AirtightHelper() {
    }

    public static boolean isAirtight(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }

        // 1. Тег #trd:airtight
        if (state.is(AIRTIGHT_TAG)) {
            if (state.getBlock() instanceof DoorBlock) {
                return !state.getValue(DoorBlock.OPEN);
            }
            return true;
        }

        // 2. Контроллер термоизоляции всегда герметичен
        if (state.is(ModBlocks.INSULATION_CONTROLLER.get())) {
            return true;
        }

        // 3. Закрытые двери и люки изолируют помещение; открытые нарушают герметичность
        if (state.getBlock() instanceof DoorBlock) {
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.OPEN);
        }

        // 4. Остекление (блоки стекла и стеклянные панели)
        if (state.is(Tags.Blocks.GLASS_PANES)
                || state.is(Tags.Blocks.GLASS_BLOCKS)
                || state.is(Blocks.GLASS_PANE)
                || state.getBlock() instanceof StainedGlassPaneBlock
                || state.is(Blocks.GLASS)
                || state.is(Blocks.TINTED_GLASS)
                || state.getBlock() instanceof StainedGlassBlock
                || state.getBlock() instanceof TintedGlassBlock
                || state.is(ModBlocks.ARMORED_GLASS.get())
                || state.is(ModBlocks.CONCRETE_ARMED_GLASS.get())) {
            return true;
        }

        // 5. Полнотелые плотные блоки
        if (state.isSolidRender(level, pos) && state.isCollisionShapeFullBlock(level, pos)) {
            return true;
        }

        // 6. Обычная проверка непрозрачности
        return state.isSolidRender(level, pos);
    }
}
