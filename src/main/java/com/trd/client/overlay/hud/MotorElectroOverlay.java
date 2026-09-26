package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.rotation.MotorElectroBlock;
import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class MotorElectroOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof MotorElectroBlock)) return;
        if (!(mc.level.getBlockEntity(pos) instanceof MotorElectroBlockEntity motor)) return;

        long rpm         = Math.abs(motor.getVisualSpeed());
        long torque      = motor.getTorqueNm();
        int  consumption = motor.getConsumptionPerSecond();
        long energy      = motor.getEnergyStored();
        long maxEnergy   = motor.getMaxEnergyStored();
        boolean running  = motor.isRunning();

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int lineH  = font.lineHeight + 2;
        int lines  = 5;
        int panelH = lineH * lines + 4;

        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        String statusKey = running ? "hud.trd.motor.status.on" : "hud.trd.motor.status.off";
        String status   = Component.translatable(statusKey).getString();
        String titleStr = Component.translatable("hud.trd.motor.title", status).getString();
        String speedStr = Component.translatable("hud.trd.motor.speed", rpm).getString();
        String torqueStr = Component.translatable("hud.trd.motor.torque", torque).getString();
        String consumStr = Component.translatable("hud.trd.motor.consumption", consumption).getString();
        String chargeColor = energy > maxEnergy / 4 ? "§a" : "§c";
        String chargeStr = Component.translatable("hud.trd.motor.charge", chargeColor, energy, maxEnergy).getString();

        int maxW = Math.max(font.width(titleStr),
                   Math.max(font.width(speedStr),
                   Math.max(font.width(torqueStr),
                   Math.max(font.width(consumStr), font.width(chargeStr)))));
        int panelW = Math.max(160, maxW + 8);

        if (x + panelW > screenW - 4) {
            x = screenW / 2 - panelW - 12;
        }

        graphics.fill(x - 4, y - 4, x + panelW, y + panelH, 0x88000000);

        graphics.drawString(font, titleStr, x, y, 0xFFFFFF, false);
        y += lineH;
        graphics.drawString(font, speedStr, x, y, 0xFFFFFF, false);
        y += lineH;
        graphics.drawString(font, torqueStr, x, y, 0xFFFFFF, false);
        y += lineH;
        graphics.drawString(font, consumStr, x, y, 0xFFFFFF, false);
        y += lineH;
        graphics.drawString(font, chargeStr, x, y, 0xFFFFFF, false);
    }
}
