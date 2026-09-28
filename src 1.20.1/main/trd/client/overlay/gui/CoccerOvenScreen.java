package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.CoccerOvenMenu;
import com.trd.multiblock.industrial.coccer.CoccerOvenBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class CoccerOvenScreen extends AbstractContainerScreen<CoccerOvenMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/coccer_oven_gui.png");

    public CoccerOvenScreen(CoccerOvenMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        float temp = menu.getTemperature();
        int filledHeight = (int) ((temp / CoccerOvenBlockEntity.MAX_TEMP) * 51);
        if (filledHeight > 0) {
            gui.blit(TEXTURE, x + 55, y + 9 + (51 - filledHeight),
                    178, 1 + (51 - filledHeight), 15, filledHeight);
        }

        if (menu.getMaxProgress() > 0) {
            int fillWidth = (int) ((menu.getProgress() / (float) menu.getMaxProgress()) * 16);
            if (fillWidth > 0) {
                gui.blit(TEXTURE, x + 80, y + 27, 178, 53, fillWidth, 3);
            }
        }

        renderFluidTank(gui, x + 106, y + 9, 15, 51);
    }

    private void renderFluidTank(GuiGraphics gui, int x, int y, int w, int h) {
        CoccerOvenBlockEntity be = menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getFluidTank().getFluid();
        if (fluid.isEmpty()) return;

        int amount = fluid.getAmount();
        int capacity = be.getFluidTank().getCapacity();
        int fillH = (int) ((amount * h) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int top = y + h - fillH;
        for (int j = 0; j < fillH; j += 16) {
            int segH = Math.min(fillH - j, 16);
            int drawY = top + j;
            for (int i = 0; i < w; i += 16) {
                int segW = Math.min(w - i, 16);
                int drawX = x + i;
                gui.blit(guiTexture, drawX, drawY, 0, 0, segW, segH, 16, 16);
            }
        }

        int surfaceY = y + h - fillH;
        gui.fill(x, surfaceY, x + w, surfaceY + 1, 0x40FFFFFF);
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        if (isHovering(55, 9, 15, 51, mouseX, mouseY)) {
            renderTemperatureTooltip(gui, mouseX, mouseY);
        } else if (isHovering(80, 27, 16, 3, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY);
        } else if (isHovering(106, 9, 15, 51, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY);
        } else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    private void renderTemperatureTooltip(GuiGraphics gui, int mx, int my) {
        int temp = menu.getTemperature();
        int color = getSmoothTemperatureColor(temp / (float) CoccerOvenBlockEntity.MAX_TEMP);
        MutableComponent text = Component.translatable("gui.trd.coccer_oven.temperature", temp, CoccerOvenBlockEntity.MAX_TEMP)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        gui.renderTooltip(this.font, text, mx, my);
    }

    private void renderProgressTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();

        if (!menu.hasRecipe()) {
            lines.add(Component.translatable("gui.trd.coccer_oven.progress.temperature", 0, 0)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            int currentTemp = menu.getTemperature();
            int reqTemp = menu.getRequiredTemp();

            MutableComponent tempLine = Component.translatable("gui.trd.coccer_oven.progress.temperature", currentTemp, reqTemp);
            if (currentTemp < reqTemp) {
                int blinkColor = (System.currentTimeMillis() / 500 % 2 == 0) ? 0xAAAAAA : 0xFF2222;
                tempLine = tempLine.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(blinkColor)));
            } else {
                int color = getSmoothTemperatureColor(currentTemp / (float) CoccerOvenBlockEntity.MAX_TEMP);
                tempLine = tempLine.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
            }
            lines.add(tempLine);

            if (menu.isProcessing()) {
                float remaining = menu.getMaxProgress() - menu.getProgress();
                float multiplier = Math.min(2.0f, menu.getTemperature() / (float) menu.getRequiredTemp());
                if (multiplier > 0) {
                    float seconds = remaining / (multiplier * 20.0f);
                    lines.add(Component.translatable("gui.trd.coccer_oven.progress.remaining",
                                    String.format("%.1f", Math.max(0, seconds)))
                            .withStyle(ChatFormatting.GRAY));
                }
                int bonus = (int) ((multiplier - 1.0f) * 100);
                int bonusColor = lerpColor(0xAAAAAA, 0xFF2222, Math.min(1.0f, bonus / 100f));
                lines.add(Component.translatable("gui.trd.coccer_oven.bonus", bonus)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(bonusColor))));
            } else if (menu.getTemperature() < menu.getRequiredTemp()) {
                int gray = (System.currentTimeMillis() / 500 % 2 == 0) ? 0x404040 : 0x808080;
                lines.add(Component.translatable("gui.trd.coccer_oven.too_cold")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(gray))));
            }
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void renderFluidTooltip(GuiGraphics gui, int mx, int my) {
        CoccerOvenBlockEntity be = menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getFluidTank().getFluid();

        if (fluid.isEmpty()) {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.coccer_oven.empty_tank").withStyle(ChatFormatting.GRAY), mx, my);
        } else {
            List<Component> lines = new ArrayList<>();
            MutableComponent fluidName = fluid.getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor() | 0xFF000000;
            fluidName = fluidName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(fluidName);
            lines.add(Component.translatable("gui.trd.coccer_oven.fluid_amount",
                            fluid.getAmount(), be.getFluidTank().getCapacity())
                    .withStyle(ChatFormatting.GRAY));
            gui.renderComponentTooltip(this.font, lines, mx, my);
        }
    }

    private int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int grey = 0xAAAAAA, orange = 0xFFAA00, red = 0xFF2222;
        if (percent <= 0.3f) return lerpColor(grey, orange, percent / 0.3f);
        else if (percent <= 0.7f) return lerpColor(orange, red, (percent - 0.3f) / 0.4f);
        else return red;
    }

    private int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}