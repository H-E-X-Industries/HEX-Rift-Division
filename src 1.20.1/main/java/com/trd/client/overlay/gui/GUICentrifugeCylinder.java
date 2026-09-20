package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.CentrifugeCylinderMenu;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity;
import net.minecraft.ChatFormatting;
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

public class GUICentrifugeCylinder extends AbstractContainerScreen<CentrifugeCylinderMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/centrifuge_cylinder_gui.png");

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 198;

    // Входной жидкостный буфер: рендер (28, 26), 16x52
    private static final int INPUT_TANK_X = 28;
    private static final int TANK_Y = 26;
    private static final int TANK_W = 16;
    private static final int TANK_H = 52;
    // Выходные столбики: 4 буфера с шагом 20 px
    private static final int FIRST_OUTPUT_TANK_X = 58;
    private static final int OUTPUT_TANK_PITCH = 20;

    // Энергобар: рендер (146, 28), UV (177, 1), 16x52, снизу вверх
    private static final int ENERGY_X = 146;
    private static final int ENERGY_Y = 28;
    private static final int ENERGY_W = 16;
    private static final int ENERGY_H = 52;
    private static final int ENERGY_U = 177;
    private static final int ENERGY_V = 1;

    // Прогрессбар рецепта: рендер (32, 12), UV (0, 211), 98x11, слева направо
    private static final int PROGRESS_X = 31;
    private static final int PROGRESS_Y = 12;
    private static final int PROGRESS_W = 98;
    private static final int PROGRESS_H = 11;
    private static final int PROGRESS_U = 0;
    private static final int PROGRESS_V = 211;

    public GUICentrifugeCylinder(CentrifugeCylinderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        gui.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Энергобар — снизу вверх
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

        // Прогрессбар рецепта — слева направо
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int w = (int) ((double) progress * PROGRESS_W / maxProgress);
            if (w < 1) w = 1;
            if (w > PROGRESS_W) w = PROGRESS_W;
            gui.blit(TEXTURE,
                    x + PROGRESS_X, y + PROGRESS_Y,
                    PROGRESS_U, PROGRESS_V,
                    w, PROGRESS_H);
        }

        CentrifugeCylinderBlockEntity be = this.menu.getBlockEntity();
        if (be != null) {
            renderFluidTank(gui, x + INPUT_TANK_X, y + TANK_Y, be.getInputTank().getFluid(), be.getInputTank().getCapacity());
            for (int i = 0; i < CentrifugeCylinderMenu.OUTPUT_COUNT; i++) {
                renderFluidTank(gui, x + FIRST_OUTPUT_TANK_X + i * OUTPUT_TANK_PITCH, y + TANK_Y,
                        be.getOutputTank(i).getFluid(), be.getOutputTank(i).getCapacity());
            }
        }
    }

    /** Рендер жидкости — стиль выщелачивателя/бочки. */
    private void renderFluidTank(GuiGraphics gui, int x, int y, FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) return;

        int amount = fluid.getAmount();
        int fillH = (int) ((amount * TANK_H) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int top = y + TANK_H - fillH;
        for (int j = 0; j < fillH; j += 16) {
            int segH = Math.min(fillH - j, 16);
            int drawY = top + j;
            for (int i = 0; i < TANK_W; i += 16) {
                int segW = Math.min(TANK_W - i, 16);
                int drawX = x + i;
                gui.blit(guiTexture, drawX, drawY, 0, 0, segW, segH, 16, 16);
            }
        }

        int surfaceY = y + TANK_H - fillH;
        gui.fill(x, surfaceY, x + TANK_W, surfaceY + 1, 0x40FFFFFF);
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        if (isHovering(INPUT_TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderInputTankTooltip(gui, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < CentrifugeCylinderMenu.OUTPUT_COUNT; i++) {
            int tx = FIRST_OUTPUT_TANK_X + i * OUTPUT_TANK_PITCH;
            if (isHovering(tx, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
                renderOutputTankTooltip(gui, i, mouseX, mouseY);
                return;
            }
        }
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.centrifuge.energy_tooltip",
                            this.menu.getEnergy(), this.menu.getMaxEnergy()),
                    mouseX, mouseY);
            return;
        }
        if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            int progress = this.menu.getProgress();
            int maxProgress = this.menu.getMaxProgress();
            if (maxProgress > 0 && progress > 0) {
                int seconds = (int) Math.ceil((maxProgress - progress) / 20.0);
                gui.renderTooltip(this.font,
                        Component.translatable("gui.trd.centrifuge.progress_tooltip", seconds),
                        mouseX, mouseY);
            }
            return;
        }
        this.renderTooltip(gui, mouseX, mouseY);
    }

    /**
     * Тултип входного буфера — стиль выщелачивателя; пустой буфер
     * показывает запомненный тип жидкости.
     */
    private void renderInputTankTooltip(GuiGraphics gui, int mx, int my) {
        CentrifugeCylinderBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getInputTank().getFluid();

        List<Component> lines = new ArrayList<>();

        if (!fluid.isEmpty()) {
            MutableComponent fluidName = fluid.getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor() | 0xFF000000;
            fluidName = fluidName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(fluidName);
            lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_amount",
                            fluid.getAmount(), be.getInputTank().getCapacity())
                    .withStyle(ChatFormatting.GRAY));
        } else if (be.getTargetFluid() != Fluids.EMPTY) {
            var target = be.getTargetFluid();
            MutableComponent targetName = new FluidStack(target, 1000).getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(target).getTintColor() | 0xFF000000;
            targetName = targetName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(targetName);
            lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_amount",
                            0, be.getInputTank().getCapacity())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.centrifuge_cylinder.empty_tank").withStyle(ChatFormatting.GRAY), mx, my);
            return;
        }
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    /** Тултип выходного буфера. */
    private void renderOutputTankTooltip(GuiGraphics gui, int index, int mx, int my) {
        CentrifugeCylinderBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getOutputTank(index).getFluid();

        if (fluid.isEmpty()) {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.centrifuge_cylinder.empty_tank").withStyle(ChatFormatting.GRAY), mx, my);
            return;
        }

        List<Component> lines = new ArrayList<>();
        MutableComponent fluidName = fluid.getDisplayName().copy();
        int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor() | 0xFF000000;
        fluidName = fluidName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
        lines.add(fluidName);
        lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_amount",
                        fluid.getAmount(), be.getOutputTank(index).getCapacity())
                .withStyle(ChatFormatting.GRAY));
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }
}
