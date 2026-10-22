package com.trd.event;

import com.trd.block.basic.industrial.lighting.ElectricLampBlock;
import com.trd.block.basic.industrial.lighting.EmptySocketBlock;
import com.trd.block.basic.industrial.lighting.FloodlightBlock;
import com.trd.block.basic.industrial.lighting.PowerPortHelper;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class LightingInteractionEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!PowerPortHelper.isHoldingWire(player)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof FloodlightBlock || block instanceof EmptySocketBlock || block instanceof ElectricLampBlock) {
            // Скасовуємо руйнування блоку при кліку дротом
            event.setCanceled(true);
            Direction clickedFace = event.getFace();
            PowerPortHelper.handlePortSwitch(level, pos, state, player, clickedFace);
        }
    }
}
