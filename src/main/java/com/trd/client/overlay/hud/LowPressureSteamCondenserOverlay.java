package com.trd.client.overlay.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trd.api.fluids.ModFluids;
import com.trd.block.basic.industrial.fluids.LowPressureSteamCondenserBlock;
import com.trd.block.entity.industrial.fluids.LowPressureSteamCondenserBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class LowPressureSteamCondenserOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof LowPressureSteamCondenserBlockEntity condenser)) return;

        BlockState state = mc.level.getBlockState(pos);
        GuiGraphics guiGraphics = event.getGuiGraphics();
        renderHUD(guiGraphics, condenser, state, guiGraphics.guiWidth(), guiGraphics.guiHeight(), mc.font);
    }

    private static void renderHUD(GuiGraphics guiGraphics, LowPressureSteamCondenserBlockEntity be,
                                  BlockState state, int screenWidth, int screenHeight, Font font) {
        int x = screenWidth / 2 + 12;
        int y = screenHeight / 2 + 4;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int steamAmount = be.getSteamTank().getFluidAmount();
        int steamCapacity = be.getSteamTank().getCapacity();
        int waterAmount = be.getWaterTank().getFluidAmount();
        int waterCapacity = be.getWaterTank().getCapacity();

        int steamColor;
        try {
            steamColor = IClientFluidTypeExtensions.of(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get()).getTintColor() | 0xFF000000;
        } catch (Exception e) {
            steamColor = 0xFF636A7C;
        }

        int waterColor;
        try {
            waterColor = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor() | 0xFF000000;
        } catch (Exception e) {
            waterColor = 0xFF3F76E4;
        }

        // Строка пара: цветное название + зелёная стрелка + белые цифры
        String steamPrefix = Component.translatable("hud.trd.condenser.steam_name").getString() + " ";
        String steamArrow  = Component.translatable("hud.trd.condenser.arrow_in").getString();
        String steamNums   = Component.translatable("hud.trd.condenser.amount", steamAmount, steamCapacity).getString();
        int steamPrefixW = font.width(steamPrefix);
        int steamArrowW  = font.width(steamArrow);
        int steamNumsW   = font.width(steamNums);
        int steamTotalW  = steamPrefixW + steamArrowW + steamNumsW;

        // Строка воды: цветное название + красная стрелка + белые цифры
        String waterPrefix = Component.translatable("hud.trd.condenser.water_name").getString() + " ";
        String waterArrow  = Component.translatable("hud.trd.condenser.arrow_out").getString();
        String waterNums   = Component.translatable("hud.trd.condenser.amount", waterAmount, waterCapacity).getString();
        int waterPrefixW = font.width(waterPrefix);
        int waterArrowW  = font.width(waterArrow);
        int waterNumsW   = font.width(waterNums);
        int waterTotalW  = waterPrefixW + waterArrowW + waterNumsW;

        // Третья строка — статус (без воды / коэффициент)
        boolean isWaterlogged = state.hasProperty(LowPressureSteamCondenserBlock.WATERLOGGED) && state.getValue(LowPressureSteamCondenserBlock.WATERLOGGED);
        String statusText;
        if (!isWaterlogged) {
            statusText = Component.translatable("hud.trd.condenser.status.no_water").getString();
        } else {
            statusText = String.format(Component.translatable("hud.trd.condenser.status.cooling").getString(), be.getCoolingMultiplier());
        }
        int statusW = font.width(statusText);

        int maxWidth = Math.max(steamTotalW, Math.max(waterTotalW, statusW));

        if (x + maxWidth + 4 > screenWidth) {
            x = screenWidth / 2 - maxWidth - 12;
        }

        int lineHeight = font.lineHeight + 2;
        int totalHeight = lineHeight * 3;
        guiGraphics.fill(x - 3, y - 2, x + maxWidth + 3, y + totalHeight + 2, 0x90000000);

        // --- Пар (вход) ---
        int cx = x;
        guiGraphics.drawString(font, steamPrefix, cx, y, steamColor, true);
        guiGraphics.drawString(font, steamArrow,  cx + steamPrefixW, y, 0xFFFFFF, true);
        guiGraphics.drawString(font, steamNums,   cx + steamPrefixW + steamArrowW, y, 0xFFFFFF, true);

        // --- Вода (выход) ---
        cx = x;
        guiGraphics.drawString(font, waterPrefix, cx, y + lineHeight, waterColor, true);
        guiGraphics.drawString(font, waterArrow,  cx + waterPrefixW, y + lineHeight, 0xFFFFFF, true);
        guiGraphics.drawString(font, waterNums,   cx + waterPrefixW + waterArrowW, y + lineHeight, 0xFFFFFF, true);

        // --- Статус ---
        guiGraphics.drawString(font, statusText, x, y + lineHeight * 2, 0xFFFFFF, true);
    }
}
