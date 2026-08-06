package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.ChemicalPlantPortMenu;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.chemistry.UpdatePortModePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

public class GUIChemicalPlantPort extends AbstractContainerScreen<ChemicalPlantPortMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/chemical_plant_port_gui.png");

    private static final int MODE_X = 74;
    private static final int MODE_Y = 8;
    private static final int MODE_SIZE = 15;

    private static final int FLUID_1_X = 17;
    private static final int FLUID_1_Y = 29;
    private static final int FLUID_2_X = 40;
    private static final int FLUID_2_Y = 29;
    private static final int FLUID_W = 16;
    private static final int FLUID_H = 52;

    public GUIChemicalPlantPort(ChemicalPlantPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 173;
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
        renderFluidTooltips(graphics, mouseX, mouseY);
    }

    private void renderFluidTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int relX = mouseX - this.leftPos;
        int relY = mouseY - this.topPos;

        if (relX >= FLUID_1_X && relX < FLUID_1_X + FLUID_W && relY >= FLUID_1_Y && relY < FLUID_1_Y + FLUID_H) {
            FluidStack fluid = menu.getFluidA();
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(this.font, fluid.getDisplayName(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font, Component.translatable("gui.trd.chemistry.empty"), mouseX, mouseY);
            }
        }
        if (relX >= FLUID_2_X && relX < FLUID_2_X + FLUID_W && relY >= FLUID_2_Y && relY < FLUID_2_Y + FLUID_H) {
            FluidStack fluid = menu.getFluidB();
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(this.font, fluid.getDisplayName(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font, Component.translatable("gui.trd.chemistry.empty"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Render mode button
        int mode = menu.getMode();
        int u = 177;
        int v = mode == 0 ? 1 : 17;
        graphics.blit(TEXTURE, x + MODE_X, y + MODE_Y, u, v, MODE_SIZE, MODE_SIZE);

        // Render fluids
        renderFluid(graphics, menu.getFluidA(), x + FLUID_1_X, y + FLUID_1_Y, FLUID_W, FLUID_H);
        renderFluid(graphics, menu.getFluidB(), x + FLUID_2_X, y + FLUID_2_Y, FLUID_W, FLUID_H);
    }

    private void renderFluid(GuiGraphics gui, FluidStack fluid, int x, int y, int width, int height) {
        if (fluid.isEmpty()) return;

        int capacity = 8000;
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

        // Surface line
        int surfaceY = y + height - fluidHeight;
        gui.fill(x, surfaceY, x + width, surfaceY + 1, 0x40FFFFFF);

        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isMouseOver(mouseX, mouseY, MODE_X, MODE_Y, MODE_SIZE, MODE_SIZE)) {
                playSound();
                int newMode = menu.getMode() == 0 ? 1 : 0;
                ModPacketHandler.INSTANCE.sendToServer(
                        new UpdatePortModePacket(menu.blockEntity.getBlockPos(), newMode)
                );
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= this.leftPos + x && mouseX <= this.leftPos + x + w &&
                mouseY >= this.topPos + y && mouseY <= this.topPos + y + h;
    }

    private void playSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}