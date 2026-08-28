package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
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
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUICCMachine extends AbstractContainerScreen<CCMachineMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/cc_machine_gui.png");

    // === Металл-бар (координаты как в оригинале, стиль как в плавильне) ===
    private static final int METAL_X = 40, METAL_Y = 9, METAL_W = 96, METAL_H = 22;
    private static final int METAL_U = 0, METAL_V = 234;

    // === Жидкостные баки (1 в 1 координаты из бочки) ===
    private static final int WATER_X = 40, WATER_Y = 42, TANK_W = 15, TANK_H = 51;
    private static final int STEAM_X = 121, STEAM_Y = 42;

    // === Слоты ===
    private static final int MOLD_X = 80, MOLD_Y = 38, SLOT_SIZE = 16;
    private static final int OUT_X = 62, OUT_Y = 60;

    public GUICCMachine(CCMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        // как в GUIFluidBarrel — убираем стандартные лейблы, если они закрывают кастомный фон
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // фон GUI
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // металл (стиль плавильни — цветная заливка по маске)
        renderMetalBar(gui, x + METAL_X, y + METAL_Y);

        // жидкости (стиль бочки — сегменты, сброс цвета, линия поверхности)
        renderFluidTank(gui, x + WATER_X, y + WATER_Y, TANK_W, TANK_H,
                new FluidStack(Fluids.WATER, menu.getWaterAmount()), menu.getWaterCapacity());
        renderFluidTank(gui, x + STEAM_X, y + STEAM_Y, TANK_W, TANK_H,
                new FluidStack(ModFluids.LOW_PRESSURE_STEAM_SOURCE.get(), menu.getSteamAmount()), menu.getSteamCapacity());
    }

    /** Рендер металла. Логика из GUISmelter: цвет из data, заливка снизу вверх по маске-текстуре. */
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
    }

    /** Рендер жидкостного бака. Полностью скопирован из GUIFluidBarrel. */
    private void renderFluidTank(GuiGraphics gui, int x, int y, int w, int h, FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) return;

        int amount = fluid.getAmount();
        int fillH = (int) ((amount * h) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());

        // 1. Сбрасываем цвет, чтобы не подхватить тинт от металла
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

        // линия поверхности (meniscus)
        int surfaceY = y + h - fillH;
        gui.fill(x, surfaceY, x + w, surfaceY + 1, 0x40FFFFFF);

        // сброс цвета обратно
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // пусто, как в оригинале
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);
        this.renderTooltip(gui, mouseX, mouseY);
        // кастомные тултипы в отдельном методе, как в GUIFluidBarrel
        this.renderCustomTooltips(gui, mouseX, mouseY);
    }

    /** Кастомные тултипы. Жидкости — стиль бочки, металл — стиль плавильни. */
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
            }
        }
    }

    /** Тултип металла в стиле GUISmelter: список строк, заголовок цветом металла, прогресс. */
    private void renderMetalTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();

        int color = menu.getMetalColor();
        if (color < 0) color = 0xAAAAAA;

        // Заголовок цветом текущего металла (как "§6§lMolten Metals:" в плавильне)
        lines.add(Component.translatable("gui.trd.cc_machine.metal_title")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));

        // Количество единиц
        lines.add(Component.translatable("gui.trd.cc_machine.metal_amount",
                        menu.getMetalUnits(), menu.getMetalCapacity())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));

        // Прогресс литья + оставшееся время (как remaining в плавильне)
        int progress = menu.getCastProgress();
        int required = menu.getCastRequired();
        if (required > 0) {
            int remaining = required - progress;
            float seconds = remaining / 20.0f;
            lines.add(Component.translatable("gui.trd.cc_machine.cast_remaining",
                            String.format("%.1f", Math.max(0, seconds)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00))));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    /** Тултип жидкости в стиле GUIFluidBarrel: цветное имя + количество. */
    private void renderFluidTooltip(GuiGraphics gui, int mx, int my, FluidStack fluid, int capacity, String key) {
        List<Component> lines = new ArrayList<>();

        if (fluid.isEmpty()) {
            // пустой бак — серое название + 0 / capacity
            lines.add(Component.translatable(key + ".name")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x888888))));
            lines.add(Component.translatable("gui.trd.cc_machine.fluid_amount", 0, capacity)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        } else {
            // имя жидкости окрашено в её тинт (как в бочке)
            MutableComponent name = fluid.getDisplayName().copy();
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