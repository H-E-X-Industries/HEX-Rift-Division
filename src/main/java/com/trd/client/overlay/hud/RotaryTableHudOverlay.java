package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.RotaryTableBlockEntity;
import com.trd.multiblock.system.MultiblockPartEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD-оверлей роторного стола (RotaryTable):
 * отображает скорость вращения (RPM), количество труб в скважине и статус бурения (вкл/выкл).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class RotaryTableHudOverlay {

    private RotaryTableHudOverlay() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        RotaryTableBlockEntity rotary = null;
        if (be instanceof RotaryTableBlockEntity direct) {
            rotary = direct;
        } else if (be instanceof MultiblockPartEntity part && part.getControllerPos() != null) {
            if (mc.level.getBlockEntity(part.getControllerPos()) instanceof RotaryTableBlockEntity ctrl) {
                rotary = ctrl;
            }
        }
        if (rotary == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 12;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 4;

        long rpm = Math.abs(rotary.getCurrentRpm());
        boolean optimal = rotary.isOptimalRpm();
        boolean active = rotary.isRedstoneEnabled() && rotary.isDerrickAssembled() && !rotary.isExtractingStopped();

        Component title = Component.translatable("hud.trd.rotary_table.title");
        Component rpmLine = Component.translatable("hud.trd.rotary_table.rpm", rpm);
        Component pipesLine = Component.translatable("hud.trd.rotary_table.pipes", rotary.getPipeCountInWell());
        Component statusLine = Component.translatable(active ? "hud.trd.rotary_table.status.on" : "hud.trd.rotary_table.status.off");

        int w = Math.max(mc.font.width(title),
                Math.max(mc.font.width(rpmLine),
                        Math.max(mc.font.width(pipesLine), mc.font.width(statusLine)))) + 10;

        gui.fill(x - 4, y - 4, x + w, y + 43, 0x90000000);

        int rpmColor = optimal ? 0xFF55FF55 : (rpm == 0 ? 0xFFFF5555 : 0xFFFFAA00);
        int statusColor = active ? 0xFF55FF55 : (rotary.isExtractingStopped() ? 0xFF55FFFF : 0xFFFF5555);

        gui.drawString(mc.font, title, x, y, 0xFFFFAA00, true);
        gui.drawString(mc.font, rpmLine, x, y + 11, rpmColor, true);
        gui.drawString(mc.font, pipesLine, x, y + 21, 0xFFCCCCCC, true);
        gui.drawString(mc.font, statusLine, x, y + 31, statusColor, true);
    }
}
