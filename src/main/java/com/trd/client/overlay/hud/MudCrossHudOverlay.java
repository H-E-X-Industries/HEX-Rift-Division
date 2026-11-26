package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
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

import java.util.Locale;

/**
 * HUD-оверлей при наведении на буровую крестовину (MudCross):
 * отображает текущее давление (бар) и заполненность буфера флюида (mB / 16000 mB).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class MudCrossHudOverlay {

    private MudCrossHudOverlay() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        MudCrossBlockEntity mudCross = null;
        if (be instanceof MudCrossBlockEntity direct) {
            mudCross = direct;
        } else if (be instanceof MultiblockPartEntity part && part.getControllerPos() != null) {
            if (mc.level.getBlockEntity(part.getControllerPos()) instanceof MudCrossBlockEntity ctrl) {
                mudCross = ctrl;
            }
        }
        if (mudCross == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 12;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 4;

        float pressure = mudCross.getPressureBar();
        int fluidMb = mudCross.getFluidAmount();
        int capMb = mudCross.getFluidCapacity();

        Component title = Component.translatable("hud.trd.mud_cross.title");
        Component pressureLine = Component.translatable("hud.trd.mud_cross.pressure",
                String.format(Locale.ROOT, "%.1f", pressure));
        Component bufferLine = Component.translatable("hud.trd.mud_cross.buffer", fluidMb, capMb);

        int w = Math.max(mc.font.width(title), Math.max(mc.font.width(pressureLine), mc.font.width(bufferLine))) + 10;
        gui.fill(x - 4, y - 4, x + w, y + 32, 0x90000000);

        int pressureColor = pressure >= 225.0f ? 0xFFFF4444
                : (pressure >= 200.0f ? 0xFFFF8800 : (pressure >= 100.0f ? 0xFFFFFF55 : 0xFF55FF55));

        gui.drawString(mc.font, title, x, y, 0xFFFFAA00, true);
        gui.drawString(mc.font, pressureLine, x, y + 11, pressureColor, true);
        gui.drawString(mc.font, bufferLine, x, y + 21, 0xFFAAAAAA, true);
    }
}
