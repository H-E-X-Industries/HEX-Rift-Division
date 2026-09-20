package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.OpticMicroscopeMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

public class GUIOpticMicroscope extends AbstractContainerScreen<OpticMicroscopeMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/optic_microscope_gui.png");

    private static final int TANK_X = 90;
    private static final int TANK_Y = 52;
    private static final int TANK_W = 34;
    private static final int TANK_H = 16;

    private static final int BAR_X = 63;
    private static final int BAR_Y = 40;
    private static final int BAR_W = 50;
    private static final int BAR_H = 2;
    private static final int BAR_UV_X = 177;
    private static final int BAR_UV_Y = 1;

    public GUIOpticMicroscope(OpticMicroscopeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 174;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.renderCustomTooltips(graphics, mouseX, mouseY);
    }

    private void renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int relX = mouseX - this.leftPos;
        int relY = mouseY - this.topPos;

        if (relX >= TANK_X && relX < TANK_X + TANK_W
                && relY >= TANK_Y && relY < TANK_Y + TANK_H) {
            List<Component> tooltip = new ArrayList<>();
            int amount = menu.getFluidAmount();
            int tintColor = IClientFluidTypeExtensions.of(com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get())
                    .getTintColor() | 0xFF000000;
            // Всегда пишем название реактива и его количество, даже при 0 мБ.
            tooltip.add(Component.translatable("gui.trd.optic_microscope.tooltip", amount)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(tintColor))));
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Тултип прогресса анализа
        if (relX >= BAR_X && relX < BAR_X + BAR_W
                && relY >= BAR_Y && relY < BAR_Y + BAR_H) {
            int progress = menu.getProgress();
            int maxProgress = menu.getMaxProgress();
            if (maxProgress > 0) {
                int remaining = maxProgress - progress;
                int seconds = (int) Math.ceil(remaining / 20.0);
                graphics.renderComponentTooltip(this.font, List.of(
                        Component.translatable("gui.trd.optic_microscope.progress_tooltip", seconds)), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        renderFluid(graphics, x + TANK_X, y + TANK_Y, TANK_W, TANK_H);

        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int barWidth = (int) (BAR_W * ((double) progress / maxProgress));
            if (barWidth > 0) {
                graphics.blit(TEXTURE, x + BAR_X, y + BAR_Y, BAR_UV_X, BAR_UV_Y, barWidth, BAR_H);
            }
        }
    }

    /** Рендер жидкости 1-в-1 как у бочки (заполнение снизу вверх). */
    private void renderFluid(GuiGraphics gui, int x, int y, int width, int height) {
        FluidStack fluid = menu.getFluid();
        if (fluid.isEmpty()) return;

        int capacity = menu.getCapacity();
        int fluidHeight = (int) (height * ((float) fluid.getAmount() / capacity));
        if (fluidHeight <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());

        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int currentY = y + height - fluidHeight;

        for (int j = 0; j < fluidHeight; j += 16) {
            int segmentHeight = Math.min(fluidHeight - j, 16);
            int drawY = currentY + j;

            for (int i = 0; i < width; i += 16) {
                int segmentWidth = Math.min(width - i, 16);
                int drawX = x + i;

                gui.blit(guiTexture, drawX, drawY, 0, 0, segmentWidth, segmentHeight, 16, 16);
            }
        }

        int surfaceY = y + height - fluidHeight;
        gui.fill(x, surfaceY, x + width, surfaceY + 1, 0x40FFFFFF);

        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // Убираем стандартные надписи, если мешают
    }
}
