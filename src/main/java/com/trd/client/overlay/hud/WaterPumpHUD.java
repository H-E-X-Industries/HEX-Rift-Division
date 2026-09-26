package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.fluids.WaterPumpBlock;
import com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity;
import com.trd.main.MainRegistry;
import com.trd.multiblock.system.MultiblockPartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class WaterPumpHUD {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        BlockPos controllerPos = pos;
        if (state.getBlock() instanceof MultiblockPartBlock) {
            controllerPos = pos.above();
            state = mc.level.getBlockState(controllerPos);
        }

        if (state.getBlock() instanceof WaterPumpBlock) {
            BlockEntity be = mc.level.getBlockEntity(controllerPos);
            if (be instanceof WaterPumpBlockEntity pump) {
                GuiGraphics guiGraphics = event.getGuiGraphics();
                Font font = mc.font;

                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                int x = screenWidth / 2 + 15;
                int y = screenHeight / 2 + 15;

                String speedText = "Скорость накачки: " + pump.getLastPumpedVolume() + " mB/t";
                String volumeText = "Объем водоема: " + pump.getCachedWaterVolume() + " / 1000";

                int maxWidth = Math.max(font.width(speedText), font.width(volumeText));

                if (x + maxWidth + 4 > screenWidth) {
                    x = screenWidth / 2 - maxWidth - 15;
                }

                guiGraphics.fill(x - 3, y - 2, x + maxWidth + 3, y + font.lineHeight * 2 + 4, 0x90000000);
                guiGraphics.drawString(font, speedText, x, y, 0xFFFFFF, true);
                guiGraphics.drawString(font, volumeText, x, y + font.lineHeight + 2, 0x44AAFF, true);
            }
        }
    }
}
