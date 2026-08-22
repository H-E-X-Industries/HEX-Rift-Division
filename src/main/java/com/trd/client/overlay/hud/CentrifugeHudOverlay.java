package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.centrifuge.CentrifugeConusBlockEntity;
import com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity;
import com.trd.multiblock.industrial.centrifuge.CentrifugeRecipe;
import com.trd.multiblock.system.MultiblockPartEntity;
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

        CentrifugeConusBlockEntity conus = null;
        boolean motorWithoutAttachment = false;

        if (be instanceof CentrifugeConusBlockEntity c) {
            conus = c;
        } else if (be instanceof MultiblockPartEntity part && part.getControllerPos() != null
                && mc.level.getBlockEntity(part.getControllerPos()) instanceof CentrifugeConusBlockEntity c2) {
            conus = c2;
        } else if (be instanceof CentrifugeMotorBlockEntity motor) {
            conus = motor.getAttachedConus();
            motorWithoutAttachment = conus == null;
        }

        Font font = mc.font;
        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();

        if (motorWithoutAttachment) {
            String txt = Component.translatable("hud.trd.centrifuge.no_attachment").getString();
            int x = screenW / 2 - font.width(txt) / 2;
            int y = screenH / 2 + 12;
            gui(event).fill(x - 3, y - 2, x + font.width(txt) + 3, y + font.lineHeight + 2, 0x90000000);
            gui(event).drawString(font, txt, x, y, 0xFF5555, true);
            return;
        }

        if (conus != null) {
            renderHud(gui(event), conus, screenW, screenH, font);
        }
    }

    private static void renderHud(GuiGraphics g, CentrifugeConusBlockEntity be, int screenW, int screenH, Font font) {
        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        var lines = new java.util.ArrayList<String>();
        var colors = new java.util.ArrayList<Integer>();
        int maxW = 0;

        String title = "§e" + be.getDisplayName().getString();
        lines.add(title);
        colors.add(0xFFFFFF);
        maxW = Math.max(maxW, font.width(title));

        long energy = be.getEnergyStored();
        long maxEnergy = be.getMaxEnergy();
        String energyLine = Component.translatable("hud.trd.centrifuge.energy",
                com.trd.util.EnergyFormatter.format(energy),
                com.trd.util.EnergyFormatter.format(maxEnergy)).getString();
        lines.add(energyLine);
        colors.add(energy > maxEnergy / 4 ? 0x55FF55 : (energy > 0 ? 0xFFFF55 : 0xFF5555));
        maxW = Math.max(maxW, font.width(energyLine));

        CentrifugeRecipe recipe = be.getCurrentRecipe();
        if (recipe == null) {
            String noRecipe = Component.translatable("hud.trd.centrifuge.no_recipe").getString();
            lines.add(noRecipe);
            colors.add(0xAAAAAA);
            maxW = Math.max(maxW, font.width(noRecipe));
        } else {
            String recipeName = Component.translatable("hud.trd.centrifuge.recipe",
                    Component.translatable("recipe.trd." + recipe.getId().getPath()).getString()).getString();
            lines.add(recipeName);
            colors.add(0xFFFFFF);
            maxW = Math.max(maxW, font.width(recipeName));

            int progress = be.getProgress();
            int maxProgress = be.getMaxProgress();
            double percent = maxProgress > 0 ? (double) progress / maxProgress : 0;

            int totalBars = 20;
            int greenBars = (int) (percent * totalBars);
            StringBuilder bar = new StringBuilder("§a");
            for (int i = 0; i < greenBars; i++) bar.append('|');
            bar.append("§7");
            for (int i = greenBars; i < totalBars; i++) bar.append('|');

            String progressText = Component.translatable("hud.trd.centrifuge.progress", (int) (percent * 100)).getString();
            String p = progressText + " [" + bar + "§r]";
            lines.add(p);
            colors.add(0xFFFFFF);
            maxW = Math.max(maxW, font.width(p));

            int secondsLeft = (int) Math.ceil((maxProgress - progress) / 20.0);
            String timeLeft = Component.translatable("gui.trd.centrifuge.progress_tooltip", secondsLeft).getString();
            lines.add(timeLeft);
            colors.add(0x888888);
            maxW = Math.max(maxW, font.width(timeLeft));
        }

        int lh = font.lineHeight + 2;
        int th = lines.size() * lh;
        if (x + maxW + 4 > screenW) x = screenW / 2 - maxW - 12;

        g.fill(x - 3, y - 2, x + maxW + 3, y + th + 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * lh, colors.get(i), true);
        }
    }

    private static GuiGraphics gui(RenderGuiOverlayEvent event) {
        return event.getGuiGraphics();
    }
}
