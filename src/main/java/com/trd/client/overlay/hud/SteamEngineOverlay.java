package com.trd.client.overlay.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.steam_engine.SteamEngineBlockEntity;
import com.trd.multiblock.system.MultiblockPartEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class SteamEngineOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            SteamEngineBlockEntity engine = null;

            if (be instanceof SteamEngineBlockEntity e) {
                engine = e;
            } else if (be instanceof MultiblockPartEntity part) {
                if (part.getControllerPos() != null) {
                    BlockEntity controllerBe = mc.level.getBlockEntity(part.getControllerPos());
                    if (controllerBe instanceof SteamEngineBlockEntity e) {
                        engine = e;
                    }
                }
            }

            if (engine != null) {
                GuiGraphics guiGraphics = event.getGuiGraphics();
                renderEngineHUD(guiGraphics, engine, guiGraphics.guiWidth(), guiGraphics.guiHeight(), mc.font);
            }
        }
    }

    private static void renderEngineHUD(GuiGraphics guiGraphics, SteamEngineBlockEntity engine, int width, int height, Font font) {
        int x = width / 2 + 12;
        int y = height / 2 + 4;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int steamAmount = engine.steamTank.getFluidAmount();
        int steamCapacity = engine.steamTank.getCapacity();
        String steamPrefix = Component.translatable("hud.trd.engine.steam").getString() + " ";
        String steamSuffix = Component.translatable("hud.trd.engine.arrow_in").getString() + steamAmount + "/" + steamCapacity + Component.translatable("hud.trd.engine.amount_suffix").getString();

        int lowPressureAmount = engine.lowPressureSteamTank.getFluidAmount();
        int lowPressureCapacity = engine.lowPressureSteamTank.getCapacity();
        String lowPressurePrefix = Component.translatable("hud.trd.engine.lp_steam").getString() + " ";
        String lowPressureSuffix = Component.translatable("hud.trd.engine.arrow_out").getString() + lowPressureAmount + "/" + lowPressureCapacity + Component.translatable("hud.trd.engine.amount_suffix").getString();

        int steamColor;
        try {
            steamColor = IClientFluidTypeExtensions.of(com.trd.api.fluids.ModFluids.STEAM_SOURCE.get()).getTintColor() | 0xFF000000;
        } catch (Exception e) {
            steamColor = 0xFFE0E0E0;
        }

        int lowPressureColor = 0xFF636A7C;

        int steamPrefixWidth = font.width(steamPrefix);
        int steamSuffixWidth = font.width(steamSuffix);
        int steamTextWidth = steamPrefixWidth + steamSuffixWidth;

        int lowPressurePrefixWidth = font.width(lowPressurePrefix);
        int lowPressureSuffixWidth = font.width(lowPressureSuffix);
        int lowPressureTextWidth = lowPressurePrefixWidth + lowPressureSuffixWidth;

        int maxWidth = Math.max(steamTextWidth, lowPressureTextWidth);

        if (x + maxWidth + 4 > width) {
            x = width / 2 - maxWidth - 12;
        }

        guiGraphics.fill(x - 4, y - 2, x + maxWidth + 4, y + 22, 0x90000000);

        int steamX = x;
        guiGraphics.drawString(font, steamPrefix, steamX, y, steamColor, true);
        guiGraphics.drawString(font, steamSuffix, steamX + steamPrefixWidth, y, 0xFFFFFF, true);

        int lowPressureX = x;
        guiGraphics.drawString(font, lowPressurePrefix, lowPressureX, y + 10, lowPressureColor, true);
        guiGraphics.drawString(font, lowPressureSuffix, lowPressureX + lowPressurePrefixWidth, y + 10, 0xFFFFFF, true);
    }
}
