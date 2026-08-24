package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CentrifugeHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof CentrifugeMotorBlockEntity motor)) return;
        if (motor.getAttachedConus() != null || motor.getAttachedCylinder() != null) return;

        Font font = mc.font;
        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();

        String txt = Component.translatable("hud.trd.centrifuge.no_attachment").getString();
        int x = screenW / 2 - font.width(txt) / 2;
        int y = screenH / 2 + 12;
        event.getGuiGraphics().fill(x - 3, y - 2, x + font.width(txt) + 3, y + font.lineHeight + 2, 0x90000000);
        event.getGuiGraphics().drawString(font, txt, x, y, 0xFF5555, true);
    }
}
