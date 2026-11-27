package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.PipeDrumBlockEntity;
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
 * HUD-оверлей барабана буровых труб (PipeDrum):
 * отображает запас энергии (JE) и количество загруженных труб (N / 6).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class PipeDrumHudOverlay {

    private PipeDrumHudOverlay() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        PipeDrumBlockEntity drum = null;
        if (be instanceof PipeDrumBlockEntity direct) {
            drum = direct;
        } else if (be instanceof MultiblockPartEntity part && part.getControllerPos() != null) {
            if (mc.level.getBlockEntity(part.getControllerPos()) instanceof PipeDrumBlockEntity ctrl) {
                drum = ctrl;
            }
        }
        if (drum == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 12;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 4;

        long energy = drum.getEnergyStored();
        int loaded = drum.getLoadedPipeCount();

        Component title = Component.translatable("hud.trd.pipe_drum.title");
        Component energyLine = Component.translatable("hud.trd.pipe_drum.energy", energy, PipeDrumBlockEntity.MAX_ENERGY);
        Component pipesLine = Component.translatable("hud.trd.pipe_drum.pipes", loaded, PipeDrumBlockEntity.SLOT_COUNT);

        int w = Math.max(mc.font.width(title), Math.max(mc.font.width(energyLine), mc.font.width(pipesLine))) + 10;
        gui.fill(x - 4, y - 4, x + w, y + 32, 0x90000000);

        int energyColor = energy >= PipeDrumBlockEntity.INDEX_COST_JE ? 0xFF55FF55 : 0xFFFF5555;
        int pipesColor = loaded > 0 ? 0xFF55FFFF : 0xFFFFAA00;

        gui.drawString(mc.font, title, x, y, 0xFFFFAA00, true);
        gui.drawString(mc.font, energyLine, x, y + 11, energyColor, true);
        gui.drawString(mc.font, pipesLine, x, y + 21, pipesColor, true);
    }
}
