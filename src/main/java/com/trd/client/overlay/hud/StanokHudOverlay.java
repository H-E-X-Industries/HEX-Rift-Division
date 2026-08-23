package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
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

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-оверлей для станка. Показывается справа-снизу от прицела
 * при наведении на любой блок мультиблока станка (контроллер или парты).
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StanokHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);

        StanokBlockEntity stanok = null;

        // Попадание напрямую в контроллер
        if (be instanceof StanokBlockEntity s) {
            stanok = s;
        } else if (be instanceof com.trd.multiblock.system.MultiblockPartEntity part) {
            // Попадание в парт — ищем контроллер
            BlockPos ctrlPos = part.getControllerPos();
            if (ctrlPos != null) {
                BlockEntity ctrl = mc.level.getBlockEntity(ctrlPos);
                if (ctrl instanceof StanokBlockEntity s) stanok = s;
            }
        }

        if (stanok == null) return;

        renderHUD(event.getGuiGraphics(), stanok,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                mc.font);
    }

    private static void renderHUD(GuiGraphics gui, StanokBlockEntity be,
                                   int screenW, int screenH, Font font) {
        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        List<String> lines  = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int maxW = 0;

        StanokRecipe recipe = be.getCurrentRecipe();

        if (recipe == null) {
            String txt = Component.translatable("hud.trd.stanok.no_recipe").getString();
            lines.add(txt);
            colors.add(0xAAAAAA);
            maxW = Math.max(maxW, font.width(txt));
            
            if (!be.hasCarriage()) {
                String carriageTxt = Component.translatable("hud.trd.stanok.no_carriage").getString();
                lines.add(carriageTxt);
                colors.add(0xFF4444);
                maxW = Math.max(maxW, font.width(carriageTxt));
            }

            if (x + maxW + 4 > screenW) x = screenW / 2 - maxW - 12;
            gui.fill(x - 3, y - 2, x + maxW + 3, y + lines.size() * font.lineHeight + 2, 0x90000000);
            for (int i = 0; i < lines.size(); i++) {
                gui.drawString(font, lines.get(i), x, y + (i * font.lineHeight), colors.get(i), true);
            }
            return;
        }

        // Название рецепта
        String recipeName = "§e" + Component.translatable(
                "recipe.trd." + recipe.getId().getPath()).getString();
        lines.add(recipeName);
        colors.add(0xFFFFFF);
        maxW = Math.max(maxW, font.width(recipeName));

        // Если нет насадки
        if (!be.hasCarriage()) {
            String noCarriageTxt = Component.translatable("hud.trd.stanok.no_carriage").getString();
            lines.add(noCarriageTxt);
            colors.add(0xFF4444);
            maxW = Math.max(maxW, font.width(noCarriageTxt));
        }

        // Скорость
        long absSpeed = Math.abs(be.getSpeed());
        long req      = recipe.getRequiredRpm();
        long tolerance = (long)(req * 0.25);
        
        int displaySpeedStatus;
        if (absSpeed < req - tolerance) {
            displaySpeedStatus = 1;
        } else if (absSpeed > req + tolerance) {
            displaySpeedStatus = 2;
        } else {
            displaySpeedStatus = 0;
        }

        // Определяем: есть ли материал
        boolean hasInput = be.hasRequiredInputsPublic(recipe);

        int speedColor;
        String speedLabel;
        if (displaySpeedStatus == 0) {
            speedColor = 0x00FF00;
            speedLabel = Component.translatable("hud.trd.stanok.speed_ok").getString();
        } else if (displaySpeedStatus == 1) {
            speedColor = 0xFF4444;
            speedLabel = Component.translatable("hud.trd.stanok.speed_slow").getString();
        } else {
            speedColor = 0xFFAA00;
            speedLabel = Component.translatable("hud.trd.stanok.speed_fast").getString();
        }
        String speedLine = absSpeed + " / " + req + " RPM — " + speedLabel;
        lines.add(speedLine);
        colors.add(speedColor);
        maxW = Math.max(maxW, font.width(speedLine));

        // Отдельная строка "Нет материала" — показываем только если скорость OK
        // но материала нет (или выходные слоты заполнены)
        if (!hasInput) {
            String noMatLine = Component.translatable("hud.trd.stanok.no_material").getString();
            lines.add(noMatLine);
            colors.add(0xFF4444); // красный
            maxW = Math.max(maxW, font.width(noMatLine));
        }

        // Прогресс
        int prog    = be.getData().get(0);
        int maxProg = be.getData().get(1);
        if (maxProg > 0 && prog > 0) {
            double pct = (double) prog / maxProg;
            int totalBars = 20;
            int green = (int)(pct * totalBars);
            int gray  = totalBars - green;

            StringBuilder bar = new StringBuilder();
            bar.append("§a");
            for (int i = 0; i < green; i++) bar.append("|");
            bar.append("§7");
            for (int i = 0; i < gray; i++) bar.append("|");

            String progressLine = Component.translatable("hud.trd.stanok.progress",
                    (int)(pct * 100)).getString() + " [" + bar + "§r]";
            lines.add(progressLine);
            colors.add(0xFFFFFF);
            maxW = Math.max(maxW, font.width(progressLine));
        }

        int lh = font.lineHeight + 2;
        int th = lines.size() * lh;
        if (x + maxW + 4 > screenW) x = screenW / 2 - maxW - 12;

        gui.fill(x - 3, y - 2, x + maxW + 3, y + th + 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            gui.drawString(font, lines.get(i), x, y + i * lh, colors.get(i), true);
        }
    }
}

