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
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Проверка блоков на герметичность (airtight).
 * Герметичными считаются:
 * - Блоки с тегом #trd:airtight
 * - Контроллер термоизоляции
 * - Любые закрытые двери (включая железные и стальные)
 * - Стекла (ванильное, цветное, тонированное, бронированное)
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

        // 4. Остекление
        if (state.is(Blocks.GLASS) || state.is(Blocks.TINTED_GLASS)
                || state.getBlock() instanceof StainedGlassBlock
                || state.is(ModBlocks.ARMORED_GLASS.get())) {
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
