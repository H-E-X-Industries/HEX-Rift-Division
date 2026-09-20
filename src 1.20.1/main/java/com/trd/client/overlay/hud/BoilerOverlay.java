package com.trd.client.overlay.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
import com.trd.multiblock.system.MultiblockPartEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class BoilerOverlay implements IGuiOverlay {

    public static final BoilerOverlay INSTANCE = new BoilerOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            BoilerBlockEntity boiler = null;

            if (be instanceof BoilerBlockEntity) {
                boiler = (BoilerBlockEntity) be;
            } else if (be instanceof MultiblockPartEntity part) {
                if (part.getControllerPos() != null) {
                    BlockEntity controllerBe = mc.level.getBlockEntity(part.getControllerPos());
                    if (controllerBe instanceof BoilerBlockEntity) {
                        boiler = (BoilerBlockEntity) controllerBe;
                    }
                }
            }

            if (boiler != null) {
                renderBoilerHUD(guiGraphics, boiler, width, height, mc.font);
            }
        }
    }

    private void renderBoilerHUD(GuiGraphics guiGraphics, BoilerBlockEntity boiler, int width, int height, Font font) {
        int x = width / 2 + 12;
        int y = height / 2 + 4;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int waterAmount = boiler.getWaterTank().getFluidAmount();
        int waterCapacity = boiler.getWaterTank().getCapacity();
        String waterPrefix = Component.translatable("hud.trd.boiler.water").getString() + " ";
        String waterSuffix = Component.translatable("hud.trd.boiler.arrow_in").getString() + waterAmount + "/" + waterCapacity + Component.translatable("hud.trd.boiler.amount_suffix").getString();

        int steamAmount = boiler.getSteamTank().getFluidAmount();
        int steamCapacity = boiler.getSteamTank().getCapacity();
        String steamPrefix = Component.translatable("hud.trd.boiler.steam").getString() + " ";
        String steamSuffix = Component.translatable("hud.trd.boiler.arrow_out").getString() + steamAmount + "/" + steamCapacity + Component.translatable("hud.trd.boiler.amount_suffix").getString();

        float temp = boiler.getTemperature();
        String tempText = String.format("%.1f / 600°C", temp);

        int waterColor = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(net.minecraft.world.level.material.Fluids.WATER).getTintColor() | 0xFF000000;
        int steamColor = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(com.trd.api.fluids.ModFluids.STEAM_SOURCE.get()).getTintColor() | 0xFF000000;

        int waterPrefixWidth = font.width(waterPrefix);
        int waterSuffixWidth = font.width(waterSuffix);
        int waterTextWidth = waterPrefixWidth + waterSuffixWidth;

        int steamPrefixWidth = font.width(steamPrefix);
        int steamSuffixWidth = font.width(steamSuffix);
        int steamTextWidth = steamPrefixWidth + steamSuffixWidth;

        int tempWidth = font.width(tempText);

        int maxWidth = Math.max(Math.max(waterTextWidth, steamTextWidth), tempWidth);

        if (x + maxWidth + 4 > width) {
            x = width / 2 - maxWidth - 12;
        }

        guiGraphics.fill(x - 4, y - 2, x + maxWidth + 4, y + 32, 0x90000000);

        int waterX = x;
        guiGraphics.drawString(font, waterPrefix, waterX, y, waterColor, true);
        guiGraphics.drawString(font, waterSuffix, waterX + waterPrefixWidth, y, 0xFFFFFF, true);

        int steamX = x;
        guiGraphics.drawString(font, steamPrefix, steamX, y + 10, steamColor, true);
        guiGraphics.drawString(font, steamSuffix, steamX + steamPrefixWidth, y + 10, 0xFFFFFF, true);

        // ═══ ТЕМПЕРАТУРА С ГРАДИЕНТОМ И МИГАНИЕМ ═══
        int tempColor;
        if (temp >= 500f) {
            // При критической температуре мигаем тёмно-серым / красным
            boolean darkPhase = (System.currentTimeMillis() / 250) % 2 == 0;
            tempColor = darkPhase ? 0x444444 : 0xFF2222;
        } else {
            // Плавный нагрев: серый → оранжевый → красный
            float percent = temp / BoilerBlockEntity.MAX_TEMP;
            tempColor = getSmoothTemperatureColor(percent);
        }

        guiGraphics.drawString(font, tempText, x, y + 20, tempColor, true);

        guiGraphics.drawString(font, tempText, x, y + 20, tempColor, true);
    }

    private static int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int colorGrey  = 0xAAAAAA;
        int colorOrange = 0xFFAA00;
        int colorRed   = 0xFF2222;

        if (percent <= 0.3f) {
            return lerpColor(colorGrey, colorOrange, percent / 0.3f);
        } else if (percent <= 0.7f) {
            return lerpColor(colorOrange, colorRed, (percent - 0.3f) / 0.4f);
        } else {
            return colorRed;
        }
    }

    private static int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8)  & 0xFF;
        int b1 =  color1        & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8)  & 0xFF;
        int b2 =  color2        & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}