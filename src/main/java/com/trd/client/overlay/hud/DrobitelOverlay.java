package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-оверлей для дробителя. Показывается справа-снизу от прицела
 * при наведении на любой блок мультиблока дробителя (контроллер или парты).
 * Копирует оформление HUD станка.
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DrobitelOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);

        DrobitelBlockEntity drobitel = null;

        // Попадание напрямую в контроллер
        if (be instanceof DrobitelBlockEntity d) {
            drobitel = d;
        } else if (be instanceof MultiblockPartEntity part) {
            // Попадание в парт — ищем контроллер
            BlockPos ctrlPos = part.getControllerPos();
            if (ctrlPos != null) {
                BlockEntity ctrl = mc.level.getBlockEntity(ctrlPos);
                if (ctrl instanceof DrobitelBlockEntity d) drobitel = d;
            }
        }

        if (drobitel == null) return;

        renderHUD(event.getGuiGraphics(), drobitel,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                mc.font);
    }

    private static void renderHUD(GuiGraphics gui, DrobitelBlockEntity be,
                                   int screenW, int screenH, Font font) {
        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        List<String> lines  = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int maxW = 0;

        // Заголовок
        String title = Component.translatable("hud.trd.drobitel.title").getString();
        lines.add(title);
        colors.add(0xFFFFAA00);
        maxW = Math.max(maxW, font.width(title));

        boolean hasBlades = be.getHasBlade1() == 1 && be.getHasBlade2() == 1;

        // Если нет лезвий
        if (!hasBlades) {
            String noBladesTxt = Component.translatable("hud.trd.drobitel.no_blades").getString();
            lines.add(noBladesTxt);
            colors.add(0xFF4444);
            maxW = Math.max(maxW, font.width(noBladesTxt));
        }

        // Скорость (рабочий диапазон 60-120 RPM)
        long absSpeed = Math.abs(be.getSpeed());

        String speedLabel;
        int speedColor;
        if (absSpeed < 60) {
            speedLabel = Component.translatable("hud.trd.drobitel.speed_slow").getString();
            speedColor = 0xFF4444;
        } else if (absSpeed > 120) {
            speedLabel = Component.translatable("hud.trd.drobitel.speed_fast").getString();
            speedColor = 0xFFAA00;
        } else {
            speedLabel = Component.translatable("hud.trd.drobitel.speed_ok").getString();
            speedColor = 0x00FF00;
        }
        String speedLine = absSpeed + " / 60-120 RPM - " + speedLabel;
        lines.add(speedLine);
        colors.add(speedColor);
        maxW = Math.max(maxW, font.width(speedLine));

        // Момент (необходимый и доступный в сети)
        long requiredTorque = be.getConsumedTorque();
        long availableTorque = be.getNetworkTorque();
        String torqueLine = Component.translatable("hud.trd.drobitel.torque",
                availableTorque, requiredTorque).getString();
        int torqueColor;
        if (requiredTorque <= 0) {
            torqueColor = 0xAAAAAA;
        } else if (availableTorque >= requiredTorque) {
            torqueColor = 0x00FF00;
        } else if (availableTorque > 0) {
            torqueColor = 0xFFAA00;
        } else {
            torqueColor = 0xFF4444;
        }
        lines.add(torqueLine);
        colors.add(torqueColor);
        maxW = Math.max(maxW, font.width(torqueLine));

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

            String progressLine = Component.translatable("hud.trd.drobitel.progress",
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