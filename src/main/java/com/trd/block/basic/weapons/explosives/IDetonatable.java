package com.trd.block.basic.weapons.explosives;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Интерфейс для блоков, которые можно активировать детонатором.
 */
public interface IDetonatable {
    /**
     * Вызывается, когда детонатор отправляет сигнал на этот блок.
     *
     * @param level  мир
     * @param pos    позиция блока
     * @param state  состояние блока
     * @param player игрок, использовавший детонатор (может быть null)
     * @return true, если блок успешно отреагировал на сигнал
     */
    boolean onDetonate(Level level, BlockPos pos, BlockState state, Player player);
}
