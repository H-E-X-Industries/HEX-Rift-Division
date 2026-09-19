package com.trd.event;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class EnergyPlacementEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor level = event.getLevel();
        if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        BlockPos placedPos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = event.getPlacedBlock();
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(serverLevel, placedPos);
        
        EnergyNetworkManager manager = EnergyNetworkManager.get(serverLevel);
        
        boolean obstructed = false;
        /* if (false) {
            obstructed = controller.getStructureHelper().hasWireObstruction(serverLevel, placedPos, state);
        } else */ {
            obstructed = manager.isBlockObstructingAnyWire(shape, placedPos);
        }

        if (obstructed) {
            event.setCanceled(false);
            serverLevel.destroyBlock(placedPos, true);
            
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal("\u00A7c\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0430 \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u0430: \u0437\u0434\u0435\u0441\u044c \u043f\u0440\u043e\u0445\u043e\u0434\u0438\u0442 \u043f\u0440\u043e\u0432\u043e\u0434!"), true);
            }
        }
    }
}
