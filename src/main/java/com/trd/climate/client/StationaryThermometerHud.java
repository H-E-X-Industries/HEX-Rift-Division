package com.trd.climate.client;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.block.entity.MercuryThermometerBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD стационарного ртутного термометра при наведении на блок.
 * <p>
 * По тому же арту, что предметный термометр ({@link MercuryThermometerHud}):
 * тело 24x71 со столбом ртути, но рисуется ПО ЦЕНТРУ экрана (на прицеле)
 * и только тогда, когда игрок наводит прицел на блок
 * {@code trd:thermometer_merc}. Значение берётся из синхронизированного
 * BlockEntity — сервер мерит его раз в секунду.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class StationaryThermometerHud {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        // Показываем только при наведении на стационарный термометр
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockEntity be = mc.level.getBlockEntity(blockHit.getBlockPos());
        if (!(be instanceof MercuryThermometerBlockEntity thermometer)) {
            return;
        }
        if (be.getBlockState().getBlock() != ModBlocks.THERMOMETER_MERC.get()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int x = (guiGraphics.guiWidth() - MercuryThermometerHud.BODY_WIDTH) / 2;
        int y = (guiGraphics.guiHeight() - MercuryThermometerHud.BODY_HEIGHT) / 2;

        MercuryThermometerHud.drawThermometer(guiGraphics, x, y, thermometer.getDisplayedTemp());
    }
}
