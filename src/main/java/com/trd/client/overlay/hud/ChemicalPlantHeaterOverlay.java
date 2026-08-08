package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.chemistry.ChemicalPlantHeaterBlock;
import com.trd.block.entity.industrial.chemistry.ChemicalPlantHeaterBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChemicalPlantHeaterOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit)) return;
        if (hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof ChemicalPlantHeaterBlock)) return;
        if (!(mc.level.getBlockEntity(pos) instanceof ChemicalPlantHeaterBlockEntity heater)) return;

        int mode = heater.getMode();
        int activeTemp = heater.getActiveTemperature();
        long energy = heater.getEnergyStored();
        long maxEnergy = heater.getMaxEnergyStored();
        
        int consumption = mode == 1 ? 50 : (mode == 2 ? 100 : 0);
        int targetTemp = mode == 1 ? 50 : (mode == 2 ? 100 : 0);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();

        int panelW = 160;
        int lineH  = font.lineHeight + 2;
        int lines  = 5;
        int panelH = lineH * lines + 4;

        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        if (x + panelW > screenW - 4) {
            x = screenW / 2 - panelW - 12;
        }

        graphics.fill(x - 4, y - 4, x + panelW, y + panelH, 0x88000000);

        String title = Component.translatable("hud.trd.chem_heater.title").getString();
        graphics.drawString(font, title, x, y, 0xFFFFFF, false);
        y += lineH;

        String modeColor = mode > 0 ? "§a" : "§c";
        String modeStr = mode == 0 ? Component.translatable("hud.trd.chem_heater.mode.off").getString()
                                   : mode == 1 ? "50°C" : "100°C";
        String modeLabel = Component.translatable("hud.trd.chem_heater.mode").getString();
        graphics.drawString(font, modeLabel + ": " + modeColor + modeStr, x, y, 0xFFFFFF, false);
        y += lineH;

        String consumptionLabel = Component.translatable("hud.trd.chem_heater.consumption").getString();
        graphics.drawString(font, consumptionLabel + ": §e" + consumption + " JE/s", x, y, 0xFFFFFF, false);
        y += lineH;

        String chargeColor = energy > maxEnergy / 4 ? "§a" : "§c";
        String chargeLabel = Component.translatable("hud.trd.chem_heater.charge").getString();
        graphics.drawString(font, chargeLabel + ": " + chargeColor + energy + " / " + maxEnergy + " JE", x, y, 0xFFFFFF, false);
    }
}
