package com.trd.client.overlay.gui;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.StanokMenu;
import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import com.trd.multiblock.industrial.stanok.StanokRecipeRegistry;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.rotation.ClearStanokRecipePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Основной GUI станка.
 *
 * Текстура: textures/gui/machine/stanok_gui.png (176×147 px atlas)
 *
 * Светодиод:
 *   Зелёный (OK)  — u=6, v=177, 6×6
 *   Красный (bad) — u=0, v=177, 6×6
 *   Рендерим в позиции (x+10, y+11) GUI.
 *
 * Прогресс-бар: рендер x64,y26 размер 48×9, текстура u177,v25.
 * Кнопка рецепта: x79,y37 размер 18×18, нажата → u177,v7.
 */
public class GUIStanok extends AbstractContainerScreen<StanokMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/stanok_gui.png");

    private static final int IMAGE_WIDTH  = 176;
    private static final int IMAGE_HEIGHT = 147;

    // Светодиод
    private static final int LED_X        = 10;
    private static final int LED_Y        = 11;
    private static final int LED_SIZE     = 6;
    // Красный диод нарисован прямо на фоне GUI, зелёный — в атласной зоне (u=178, v=0)
    private static final int LED_GREEN_U  = 178;
    private static final int LED_GREEN_V  = 0;

    // Прогресс-бар
    private static final int BAR_X  = 64;
    private static final int BAR_Y  = 26;
    private static final int BAR_W  = 48;
    private static final int BAR_H  = 9;
    private static final int BAR_U  = 177;
    private static final int BAR_V  = 25;

    // Кнопка рецепта
    private static final int BTN_X        = 79;
    private static final int BTN_Y        = 37;
    private static final int BTN_W        = 18;
    private static final int BTN_H        = 18;
    private static final int BTN_PRESS_U  = 177;
    private static final int BTN_PRESS_V  = 7;

    private boolean recipeScreenOpen = false;
    private boolean buttonPressed    = false;
    private int     btnPressTimer    = 0;

    @Nullable private GUIStanokRecipeSelect recipeScreen = null;

    public GUIStanok(StanokMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width  - this.imageWidth)  / 2;
        this.topPos  = (this.height - this.imageHeight) / 2;
        this.recipeScreen = null;
        this.recipeScreenOpen = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int rMouseX = mouseX;
        int rMouseY = mouseY;
        
        // Если мышь поверх панели рецептов, обманываем стандартный рендер, чтобы не подсвечивать слоты
        if (recipeScreenOpen && recipeScreen != null && recipeScreen.isInsidePanel(mouseX, mouseY)) {
            rMouseX = -1000;
            rMouseY = -1000;
        }

        super.render(graphics, rMouseX, rMouseY, partialTick);
        this.renderTooltip(graphics, rMouseX, rMouseY);

        if (recipeScreenOpen && recipeScreen != null) {
            recipeScreen.renderOverlay(graphics, mouseX, mouseY, partialTick);
        }

        if (btnPressTimer > 0) btnPressTimer--;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Фон GUI
        graphics.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Светодиод: красный — часть фона GUI (нарисован на текстуре).
        // Зелёный рисуется поверх когда speedStatus == 0 (OK).
        int speedStatus = menu.getSpeedStatus();
        if (speedStatus == 0) {
            graphics.blit(TEXTURE, x + LED_X, y + LED_Y, LED_GREEN_U, LED_GREEN_V, LED_SIZE, LED_SIZE);
        }


        // Прогресс-бар
        int prog    = menu.getProgress();
        int maxProg = menu.getMaxProgress();
        if (maxProg > 0 && prog > 0) {
            int filled = (int)((float) prog / maxProg * BAR_W);
            graphics.blit(TEXTURE, x + BAR_X, y + BAR_Y, BAR_U, BAR_V, filled, BAR_H);
        }

        // Кнопка рецепта (нажатое состояние)
        if (btnPressTimer > 0) {
            graphics.blit(TEXTURE, x + BTN_X, y + BTN_Y, BTN_PRESS_U, BTN_PRESS_V, BTN_W, BTN_H);
        }

        // Иконка рецепта на кнопке
        renderRecipeButton(graphics, x, y);
    }

    private void renderRecipeButton(GuiGraphics graphics, int x, int y) {
        ItemStack icon = getRecipeButtonIcon();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + BTN_X + 1, y + BTN_Y + 1);
        }
    }

    private ItemStack getRecipeButtonIcon() {
        StanokBlockEntity be = getStanokBE();
        if (be == null) return new ItemStack(Items.PAPER);
        StanokRecipe recipe = be.getCurrentRecipe();
        if (recipe == null || recipe.getOutputs().isEmpty()) return new ItemStack(Items.PAPER);
        return recipe.getOutputs().get(0).copy();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Не рендерим стандартные надписи
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Если открыт экран рецептов — отдаём ему клики
        if (recipeScreenOpen && recipeScreen != null) {
            boolean handled = recipeScreen.handleMouseClick(mouseX, mouseY, button);
            if (handled) return true;
            // Клик вне панели рецептов закрывает её
            if (!recipeScreen.isInsidePanel(mouseX, mouseY)) {
                recipeScreenOpen = false;
                recipeScreen = null;
                return true;
            }
        }

        // Кнопка рецепта
        int bx = this.leftPos + BTN_X;
        int by = this.topPos  + BTN_Y;
        if (mouseX >= bx && mouseX <= bx + BTN_W && mouseY >= by && mouseY <= by + BTN_H) {
            playClickSound();
            btnPressTimer = 10;
            if (recipeScreenOpen) {
                recipeScreenOpen = false;
                recipeScreen = null;
            } else {
                openRecipeScreen();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openRecipeScreen() {
        StanokBlockEntity be = getStanokBE();
        if (be == null) return;
        CarriageType carriage = be.getCurrentCarriageType();
        recipeScreen = new GUIStanokRecipeSelect(this, be, carriage);
        recipeScreenOpen = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (recipeScreenOpen && recipeScreen != null) {
            return recipeScreen.handleMouseScroll(mouseX, mouseY, delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (recipeScreenOpen && recipeScreen != null) {
            return recipeScreen.handleMouseDragged(mouseX, mouseY, button, dx, dy);
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (recipeScreenOpen && recipeScreen != null) {
            recipeScreen.handleMouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (recipeScreenOpen && recipeScreen != null) {
            return recipeScreen.handleCharTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (recipeScreenOpen && recipeScreen != null) {
            if (recipeScreen.handleKeyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable
    public StanokBlockEntity getStanokBE() {
        if (menu.getBlockEntity() instanceof StanokBlockEntity be) return be;
        return null;
    }

    public int getGuiLeft() { return leftPos; }
    public int getGuiTop()  { return topPos; }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
