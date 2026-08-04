package com.trd.client.overlay.gui;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.DrobitelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DrobitelScreen extends AbstractContainerScreen<DrobitelMenu> {

    private static final ResourceLocation GUI = new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/drobitel_gui.png");

    public DrobitelScreen(DrobitelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 173;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 44;
        this.inventoryLabelY = 81;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Прогрессбар: рендер 62,53 | текстура 1,195 | 52x6
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int w = (int) ((progress / (float) maxProgress) * 52);
            guiGraphics.blit(GUI, x + 62, y + 53, 1, 195, w, 6);
        }

        // Индикатор лезвия 1: 67,36 | текстура 0,181 | 42x10
        if (menu.hasBlade1()) {
            guiGraphics.blit(GUI, x + 67, y + 36, 0, 181, 42, 10);
        }

        // Индикатор лезвия 2: 67,64 | текстура 0,181 | 42x10
        if (menu.hasBlade2()) {
            guiGraphics.blit(GUI, x + 67, y + 64, 0, 181, 42, 10);
        }

        // Полоска прочности 1: рендер 66,29 | текстура 0,192 | уменьшается справа налево
        if (menu.hasBlade1()) {
            int dur = menu.getBlade1Durability();
            int w = (int) ((dur / 256f) * 42);
            if (w > 0) {
                guiGraphics.blit(GUI, x + 66 + (42 - w), y + 29, 0, 192, w, 4);
            }
        }

        // Полоска прочности 2: рендер 66,79 | текстура 0,192 | уменьшается справа налево
        if (menu.hasBlade2()) {
            int dur = menu.getBlade2Durability();
            int w = (int) ((dur / 256f) * 42);
            if (w > 0) {
                guiGraphics.blit(GUI, x + 66 + (42 - w), y + 79, 0, 192, w, 4);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}