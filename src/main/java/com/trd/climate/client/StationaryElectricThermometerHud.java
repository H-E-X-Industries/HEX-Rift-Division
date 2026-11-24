package com.trd.climate.client;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.block.entity.ElectricThermometerBlockEntity;
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
 * HUD стационарного электронного термометра при наведении на блок.
 * <p>
 * По тому же арту, что и предметный термометр ({@link ElectricThermometerHud}):
 * корпус прибора 40x22 с горящими экранами, показанием температуры и графиком,
 * но рисуется ПО ЦЕНТРУ экрана (на прицеле) и только тогда, когда игрок наводит
 * прицел на блок {@code trd:electric_thermometer_block}. Значение и график берутся
 * из синхронизированного BlockEntity — сервер меряет температуру в точке установки
 * раз в секунду.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class StationaryElectricThermometerHud {

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
        if (!(be instanceof ElectricThermometerBlockEntity thermometer)) {
            return;
        }
        if (be.getBlockState().getBlock() != ModBlocks.ELECTRIC_THERMOMETER_BLOCK.get()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int x = (guiGraphics.guiWidth() - ElectricThermometerHud.BODY_WIDTH) / 2;
        int y = (guiGraphics.guiHeight() - ElectricThermometerHud.BODY_HEIGHT) / 2;

        ElectricThermometerHud.drawDisplay(guiGraphics, x, y,
                thermometer.getDisplayedTemp(), thermometer.getGraphRows());
    }
}
