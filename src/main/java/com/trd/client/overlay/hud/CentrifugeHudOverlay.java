package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class CentrifugeHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof CentrifugeMotorBlockEntity motor)) return;
        if (motor.getAttachedConus() != null || motor.getAttachedCylinder() != null) return;

        Font font = mc.font;
        int screenW = event.getGuiGraphics().guiWidth();
        int screenH = event.getGuiGraphics().guiHeight();

        String txt = Component.translatable("hud.trd.centrifuge.no_attachment").getString();
        int x = screenW / 2 - font.width(txt) / 2;
        int y = screenH / 2 + 12;
        event.getGuiGraphics().fill(x - 3, y - 2, x + font.width(txt) + 3, y + font.lineHeight + 2, 0x90000000);
        event.getGuiGraphics().drawString(font, txt, x, y, 0xFF5555, true);
    }
}
