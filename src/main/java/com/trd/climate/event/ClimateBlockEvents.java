package com.trd.climate.event;

import com.trd.climate.room.AirtightHelper;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Обработчик разрушения, установки блоков и взрывов вблизи герметичных помещений.
 * 
 * Реализует механизм дебаунса (Throttle/Debounce):
 * - При ломании блоков стен, расширении пещеры буром 5х5 или взрывах
 *   комната помечается флагом dirty.
 * - Флаг выставляется ровно один раз за 3 секунды.
 * - Сканер не сбрасывает герметизацию мгновенно, а ждет 3 секунды и выполняет
 *   ровно один пересчет BFS под новую геометрию.
 */
@EventBusSubscriber(modid = "trd")
public class ClimateBlockEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof Level level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // Пропускаємо дроти, патрони та лампи для запобігання зайвих сканувань кімнат при маніпуляціях з освітленням
        if (state.getBlock() instanceof com.trd.block.basic.industrial.energy.WireBlock
                || state.getBlock() instanceof com.trd.block.basic.industrial.lighting.ElectricLampBlock
                || state.getBlock() instanceof com.trd.block.basic.industrial.lighting.EmptySocketBlock
                || state.getBlock() instanceof com.trd.block.basic.industrial.lighting.FloodlightBlock) {
            return;
        }

        // Игнорируем блоки без коллизии (трава, факелы и т.д.)
        if (!state.isSolidRender(level, pos) && !AirtightHelper.isAirtight(state, level, pos)) {
            return;
        }

        SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
        if (room != null) {
            room.markDirty();
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof Level level)) {
            return;
        }

        BlockPos pos = event.getPos();
        SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
        if (room != null) {
            room.markDirty();
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        for (BlockPos pos : event.getAffectedBlocks()) {
            SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
            if (room != null) {
                room.markDirty();
                // Достаточно пометить комнату один раз за взрыв
                break;
            }
        }
    }
}
