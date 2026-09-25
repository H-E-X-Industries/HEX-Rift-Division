package com.trd.client.event;

import com.trd.block.basic.industrial.rotation.HandCrankBlock;
import com.trd.main.MainRegistry;
import com.trd.network.packet.rotation.ScrollHandCrankPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ClientScrollEvent {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);

            if (state.getBlock() instanceof HandCrankBlock) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    double delta = event.getScrollDeltaY();
                    if (delta != 0) {
                        int intDelta = delta > 0 ? 1 : -1;
                        PacketDistributor.sendToServer(new ScrollHandCrankPacket(pos, intDelta));
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
}
