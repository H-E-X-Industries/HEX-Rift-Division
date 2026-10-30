package com.trd.climate.client;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.block.entity.InsulationControllerBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Отображение состояния герметичности на HUD при наведении прицела на контроллер термоизоляции.
 * Выводит актуальную информацию в Actionbar: герметично, разгерметизация в [X, Y, Z] или открытая дверь в [X, Y, Z].
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class InsulationControllerHudHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            net.minecraft.world.level.block.state.BlockState hitState = mc.level.getBlockState(pos);
            if (hitState.is(ModBlocks.INSULATION_CONTROLLER.get())) {
                BlockEntity be = mc.level.getBlockEntity(pos);
                if (be instanceof InsulationControllerBlockEntity controller) {
                    Component status = controller.getStatusComponent(mc.level);
                    if (status != null) {
                        mc.player.displayClientMessage(status, true);
                    }
                }
            } else if (hitState.is(ModBlocks.ATMOSPHERIC_SCANNER.get())) {
                BlockEntity be = mc.level.getBlockEntity(pos);
                if (be instanceof com.trd.climate.block.entity.AtmosphericScannerBlockEntity scanner) {
                    Component status = scanner.getStatusComponent(mc.level);
                    if (status != null) {
                        mc.player.displayClientMessage(status, true);
                    }
                }
            }
        }
    }
}
