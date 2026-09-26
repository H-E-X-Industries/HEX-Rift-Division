package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.CCMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUICCMachine extends AbstractContainerScreen<CCMachineMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MainRegistry.MOD_ID, "textures/gui/machine/cc_machine_gui.png");

    private static final int METAL_X = 40, METAL_Y = 9, METAL_W = 96, METAL_H = 22;
    private static final int METAL_U = 0, METAL_V = 234;

    private static final int WATER_X = 40, WATER_Y = 42, TANK_W = 15, TANK_H = 51;
    private static final int STEAM_X = 121, STEAM_Y = 42;

    public GUICCMachine(CCMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        renderFluidTank(gui, x + WATER_X, y + WATER_Y, TANK_W, TANK_H,
                new FluidStack(Fluids.WATER, menu.getWaterAmount()), menu.getWaterCapacity());
        renderFluidTank(gui, x + STEAM_X, y + STEAM_Y, TANK_W, TANK_H,
                new FluidStack(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), menu.getSteamAmount()), menu.getSteamCapacity());

        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        renderMetalBar(gui, x + METAL_X, y + METAL_Y);
    }

    private void renderMetalBar(GuiGraphics gui, int x, int y) {
        int units = menu.getMetalUnits();
        int capacity = menu.getMetalCapacity();
        if (capacity <= 0) return;

        int fillHeight = (int) ((units / (float) capacity) * METAL_H);
        if (fillHeight <= 0) return;

        int color = menu.getMetalColor();
        if (color < 0) color = 0xAAAAAA;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        gui.setColor(r, g, b, 1.0f);
        int top = y + METAL_H - fillHeight;
        int vOffset = METAL_V + (METAL_H - fillHeight);
        gui.blit(TEXTURE, x, top, METAL_U, vOffset, METAL_W, fillHeight);
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        gui.fill(x, top, x + METAL_W, top + 1, 0x40FFFFFF);
    }

    private void renderFluidTank(GuiGraphics gui, int x, int y, int w, int h, FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) return;

        int amount = fluid.getAmount();
        int fillH = (int) ((amount * h) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());

        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);

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
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui, mouseX, mouseY, delta);
        super.render(gui, mouseX, mouseY, delta);
        this.renderTooltip(gui, mouseX, mouseY);
        this.renderCustomTooltips(gui, mouseX, mouseY);
    }

    private void renderCustomTooltips(GuiGraphics gui, int mouseX, int mouseY) {
        if (isHovering(METAL_X, METAL_Y, METAL_W, METAL_H, mouseX, mouseY)) {
            renderMetalTooltip(gui, mouseX, mouseY);
        } else if (isHovering(WATER_X, WATER_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY,
                    new FluidStack(Fluids.WATER, menu.getWaterAmount()),
                    menu.getWaterCapacity(), "gui.trd.cc_machine.water");
        } else if (isHovering(STEAM_X, STEAM_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY,
                    new FluidStack(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), menu.getSteamAmount()),
                    menu.getSteamCapacity(), "gui.trd.cc_machine.steam");
        }
    }

    private void renderMetalTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.trd.cc_machine.metal_title"));

        Metal metal = menu.getBlockEntity().getStoredMetal();
        if (metal == null) {
            lines.add(Component.translatable("gui.trd.smelter.metal_tank.empty"));
        } else {
            boolean showExact = hasShiftDown();
            int units = menu.getMetalUnits();
            MetalUnits2.MetalStack converted = MetalUnits2.convertFromUnits(units);
            String name = Component.translatable(metal.getTranslationKey()).getString();

            if (showExact) {
                lines.add(Component.literal(name + ": " + units + " ед.")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(metal.getColor()))));
            } else {
                StringBuilder sb = new StringBuilder();
                if (converted.blocks() > 0) sb.append(converted.blocks())
                        .append(Component.translatable("gui.trd.smelter.metal_tank.block_abbr").getString()).append(" ");
                if (converted.ingots() > 0) sb.append(converted.ingots())
                        .append(Component.translatable("gui.trd.smelter.metal_tank.ingot_abbr").getString()).append(" ");
                if (converted.nuggets() > 0) sb.append(converted.nuggets())
                        .append(Component.translatable("gui.trd.smelter.metal_tank.nugget_abbr").getString()).append(" ");
                if (sb.length() == 0) sb.append("0");
                lines.add(Component.literal(name + ": " + sb.toString())
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(metal.getColor()))));
            }
            int capacity = menu.getMetalCapacity();
            if (showExact) {
                lines.add(Component.translatable("gui.trd.smelter.metal_tank.total_exact", units, capacity));
            } else {
                MetalUnits2.MetalStack totalConv = MetalUnits2.convertFromUnits(units);
                int maxBlocks = capacity / MetalUnits2.UNITS_PER_BLOCK;
                lines.add(Component.translatable("gui.trd.smelter.metal_tank.total_converted",
                        totalConv.blocks(), totalConv.ingots(), totalConv.nuggets(), maxBlocks));
            }
            lines.add(Component.translatable(showExact
                    ? "gui.trd.smelter.metal_tank.shift_hide"
                    : "gui.trd.smelter.metal_tank.shift_show"));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void renderFluidTooltip(GuiGraphics gui, int mx, int my, FluidStack fluid, int capacity, String key) {
        List<Component> lines = new ArrayList<>();

        if (fluid.isEmpty()) {
            lines.add(Component.translatable(key + ".name")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x888888))));
            lines.add(Component.translatable("gui.trd.cc_machine.fluid_amount", 0, capacity)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        } else {
            MutableComponent name = fluid.getHoverName().copy();
            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor() | 0xFF000000;
            name = name.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(name);

            lines.add(Component.translatable("gui.trd.cc_machine.fluid_amount",
                            fluid.getAmount(), capacity)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }
}
