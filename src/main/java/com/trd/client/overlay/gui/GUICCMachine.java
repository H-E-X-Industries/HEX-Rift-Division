package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.CCMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public class GUICCMachine extends AbstractContainerScreen<CCMachineMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/cc_machine_gui.png");

    // прогрессбар металла на экране (40,9), размер 96x22; текстура на атласе (0,234), размер 96x22
    private static final int METAL_X = 40, METAL_Y = 9, METAL_W = 96, METAL_H = 22;
    private static final int METAL_U = 0, METAL_V = 234;
    // буфер воды (40,42) 15x51
    private static final int WATER_X = 40, WATER_Y = 42, TANK_W = 15, TANK_H = 51;
    // буфер пара н.д. (121,42) 15x51
    private static final int STEAM_X = 121, STEAM_Y = 42;
    // слот формы (80,38)
    private static final int MOLD_X = 80, MOLD_Y = 38, SLOT_SIZE = 16;
    // выходные слоты (62,60 ... 98,78)
    private static final int OUT_X = 62, OUT_Y = 60;

    public GUICCMachine(CCMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        renderMetalBar(gui, x + METAL_X, y + METAL_Y);
        renderFluidTank(gui, x + WATER_X, y + WATER_Y, TANK_W, TANK_H,
                new FluidStack(Fluids.WATER, menu.getWaterAmount()), menu.getWaterCapacity());
        renderFluidTank(gui, x + STEAM_X, y + STEAM_Y, TANK_W, TANK_H,
                new FluidStack(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), menu.getSteamAmount()), menu.getSteamCapacity());
    }

    private void renderMetalBar(GuiGraphics gui, int x, int y) {
        int units = menu.getMetalUnits();
        int capacity = menu.getMetalCapacity();
        if (capacity <= 0) return;
        int fillWidth = (int) ((units / (float) capacity) * METAL_W);
        if (fillWidth <= 0) return;

        int color = menu.getMetalColor();
        if (color < 0) color = 0xAAAAAA;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        gui.setColor(r, g, b, 1.0f);
        gui.blit(TEXTURE, x, y, METAL_U, METAL_V, fillWidth, METAL_H);
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderFluidTank(GuiGraphics gui, int x, int y, int w, int h, FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) return;
        int amount = fluid.getAmount();
        int fillH = (int) ((amount * h) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());
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
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (isHovering(METAL_X, METAL_Y, METAL_W, METAL_H, mouseX, mouseY)) {
            renderMetalTooltip(gui, mouseX, mouseY);
        } else if (isHovering(WATER_X, WATER_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY, "gui.trd.cc_machine.water_tooltip",
                    menu.getWaterAmount(), menu.getWaterCapacity());
        } else if (isHovering(STEAM_X, STEAM_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY, "gui.trd.cc_machine.steam_tooltip",
                    menu.getSteamAmount(), menu.getSteamCapacity());
        } else if (isHovering(MOLD_X, MOLD_Y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
            gui.renderTooltip(this.font, Component.translatable("gui.trd.cc_machine.mold_slot"), mouseX, mouseY);
        } else {
            boolean hoveringOutput = false;
            for (int i = 0; i < 6; i++) {
                int col = i % 3;
                int row = i / 3;
                if (isHovering(OUT_X + col * 18, OUT_Y + row * 18, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                    hoveringOutput = true;
                    break;
                }
            }
            if (hoveringOutput) {
                gui.renderTooltip(this.font, Component.translatable("gui.trd.cc_machine.output_slot"), mouseX, mouseY);
            } else {
                this.renderTooltip(gui, mouseX, mouseY);
            }
        }
    }

    private void renderMetalTooltip(GuiGraphics gui, int mx, int my) {
        gui.renderTooltip(this.font, Component.translatable("gui.trd.cc_machine.metal_tooltip",
                menu.getMetalUnits(), menu.getMetalCapacity()), mx, my);
    }

    private void renderFluidTooltip(GuiGraphics gui, int mx, int my, String key, int amount, int capacity) {
        gui.renderTooltip(this.font, Component.translatable(key, amount, capacity), mx, my);
    }
}
