package com.trd.client.overlay.gui;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GUICentrifuge extends AbstractContainerScreen<CentrifugeMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/centrifuge_conus_gui.png");

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 182;

    // Энергобар: рендер (154, 8), UV (217, 1), 16x52, снизу вверх
    private static final int ENERGY_X = 154;
    private static final int ENERGY_Y = 8;
    private static final int ENERGY_W = 16;
    private static final int ENERGY_H = 52;
    private static final int ENERGY_U = 217;
    private static final int ENERGY_V = 1;

    // Прогрессбар рецепта №1: рендер (63, 37), UV (187, 1), 14x27, справа налево
    private static final int BAR1_X = 63;
    private static final int BAR1_Y = 37;
    private static final int BAR1_U = 187;
    private static final int BAR_W = 14;
    private static final int BAR_H = 27;
    private static final int BAR_V = 1;

    // Прогрессбар рецепта №2: рендер (99, 37), UV (202, 1), 14x27, слева направо
    private static final int BAR2_X = 99;
    private static final int BAR2_Y = 37;
    private static final int BAR2_U = 202;

    public GUICentrifuge(CentrifugeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        gui.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();
        if (maxEnergy > 0 && energy > 0) {
            int barHeight = (int) ((double) energy * ENERGY_H / maxEnergy);
            if (barHeight < 1) barHeight = 1;
            if (barHeight > ENERGY_H) barHeight = ENERGY_H;
            gui.blit(TEXTURE,
                    x + ENERGY_X, y + ENERGY_Y + (ENERGY_H - barHeight),
                    ENERGY_U, ENERGY_V + (ENERGY_H - barHeight),
                    ENERGY_W, barHeight);
        }

        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int w = (int) ((double) progress * BAR_W / maxProgress);
            if (w < 1) w = 1;
            if (w > BAR_W) w = BAR_W;

            gui.blit(TEXTURE,
                    x + BAR1_X + (BAR_W - w), y + BAR1_Y,
                    BAR1_U + (BAR_W - w), BAR_V,
                    w, BAR_H);

            gui.blit(TEXTURE,
                    x + BAR2_X, y + BAR2_Y,
                    BAR2_U, BAR_V,
                    w, BAR_H);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderTooltip(gui, mouseX, mouseY);

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.centrifuge.energy_tooltip",
                            this.menu.getEnergy(), this.menu.getMaxEnergy()),
                    mouseX, mouseY);
        }

        if (isHovering(BAR1_X, BAR1_Y, BAR_W, BAR_H, mouseX, mouseY)
                || isHovering(BAR2_X, BAR2_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            int progress = this.menu.getProgress();
            int maxProgress = this.menu.getMaxProgress();
            if (maxProgress > 0 && progress > 0) {
                int seconds = (int) Math.ceil((maxProgress - progress) / 20.0);
                gui.renderTooltip(this.font,
                        Component.translatable("gui.trd.centrifuge.progress_tooltip", seconds),
                        mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
    }
}
