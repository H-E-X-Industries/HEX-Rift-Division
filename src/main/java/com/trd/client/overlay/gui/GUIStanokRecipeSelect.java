package com.trd.client.overlay.gui;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import com.trd.multiblock.industrial.stanok.StanokRecipeRegistry;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.rotation.ClearStanokRecipePacket;
import com.trd.network.packet.rotation.SelectStanokRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель выбора рецепта станка.
 * Рендерится поверх GUIStanok, использует ту же текстуру что и химическая реакционная камера.
 * Полностью самодостаточна: хранит состояние поиска/скролла.
 */
public class GUIStanokRecipeSelect {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            MainRegistry.MOD_ID,
            "textures/gui/machine/chemical_plant_reaction_chamber_gui.png");

    // Размер панели
    private static final int PANEL_W = 153;
    private static final int PANEL_H = 184;

    // Поиск
    private static final int SEARCH_X = 39;
    private static final int SEARCH_Y = 8;
    private static final int SEARCH_W = 64;
    private static final int SEARCH_H = 15;

    // Кнопка сброса
    private static final int RESET_X  = 123;
    private static final int RESET_Y  = 6;
    private static final int RESET_W  = 9;
    private static final int RESET_H  = 19;
    private static final int RESET_PRESSED_U = 163;
    private static final int RESET_PRESSED_V = 46;

    // Скроллбар
    private static final int SCROLLBAR_TRACK_X = 123;
    private static final int SCROLLBAR_TRACK_Y = 33;
    private static final int SCROLLBAR_TRACK_W = 8;
    private static final int SCROLLBAR_TRACK_H = 141;
    private static final int THUMB_H = 15;
    private static final int THUMB_U = 154;
    private static final int THUMB_V = 46;

    // Список рецептов
    private static final int LIST_X  = 22;
    private static final int LIST_Y  = 33;
    private static final int LIST_W  = 99;
    private static final int LIST_H  = 141;
    private static final int ENTRY_H = 22;
    private static final int ENTRY_U = 154;
    private static final int ENTRY_V = 0;
    private static final int ENTRY_SELECTED_U = 154;
    private static final int ENTRY_SELECTED_V = 23;

    private static final int ICON_X    = 3;
    private static final int ICON_Y    = 3;

    // ─── Состояние ───
    private final GUIStanok parent;
    private final StanokBlockEntity blockEntity;
    @Nullable private final CarriageType filterCarriage;

    private final Font font;
    private final EditBox searchBox;
    private float scrollAmount = 0f;
    private boolean isDraggingScrollBar = false;
    private int resetPressTimer = 0;
    private int cursorTimer     = 0;
    private int tickCount       = 0;

    private final List<StanokRecipe> displayList = new ArrayList<>();

    // Позиция панели (выровненная по центру относительно родительского GUI)
    private final int panelX;
    private final int panelY;

    public GUIStanokRecipeSelect(GUIStanok parent, StanokBlockEntity be,
                                  @Nullable CarriageType filterCarriage) {
        this.parent = parent;
        this.blockEntity = be;
        this.filterCarriage = filterCarriage;
        this.font = Minecraft.getInstance().font;

        // Центрируем панель относительно экрана
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.panelX = (screenW - PANEL_W) / 2;
        this.panelY = (screenH - PANEL_H) / 2;

        this.searchBox = new EditBox(font,
                panelX + SEARCH_X, panelY + SEARCH_Y,
                SEARCH_W, SEARCH_H, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setTextColor(0x00FFFFFF);
        this.searchBox.setFocused(true);
        this.searchBox.setResponder(text -> {
            scrollAmount = 0;
            updateList();
        });

        updateList();
    }

    private void updateList() {
        displayList.clear();
        String search = searchBox.getValue().toLowerCase().trim();

        List<StanokRecipe> source = filterCarriage != null
                ? StanokRecipeRegistry.getForCarriage(filterCarriage)
                : StanokRecipeRegistry.getAll();

        for (StanokRecipe recipe : source) {
            String name = recipe.getId().toString().toLowerCase();
            String loc  = Component.translatable("recipe.trd." + recipe.getId().getPath())
                    .getString().toLowerCase();
            if (search.isEmpty() || name.contains(search) || loc.contains(search)) {
                displayList.add(recipe);
            }
        }
    }

    // ─── Рендер ───

    public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickCount++;
        if (tickCount % 3 == 0) {
            if (resetPressTimer > 0) resetPressTimer--;
            cursorTimer++;
        }

        // Полупрозрачный фон
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + PANEL_H + 2, 0xAA000000);

        // Текстура панели
        graphics.blit(TEXTURE, panelX, panelY, 0, 0, PANEL_W, PANEL_H);

        // Кнопка сброса (нажатое состояние)
        if (resetPressTimer > 0) {
            graphics.blit(TEXTURE, panelX + RESET_X, panelY + RESET_Y,
                    RESET_PRESSED_U, RESET_PRESSED_V, RESET_W, RESET_H);
        }

        // Список рецептов
        renderList(graphics, mouseX, mouseY);

        // Скроллбар
        renderScrollBar(graphics);

        // Строка поиска
        String content = searchBox.getValue();
        boolean focused = searchBox.isFocused();
        String cursor = (focused && (cursorTimer / 10 % 2 == 0)) ? "_" : "";
        String full = content + cursor;
        if (font.width(full) > SEARCH_W - 4) {
            full = font.plainSubstrByWidth(full, SEARCH_W - 4, true);
        }
        graphics.drawString(font, full,
                panelX + SEARCH_X + 2, panelY + SEARCH_Y + 4, 0xAEC6CF, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int lx = panelX + LIST_X;
        int ly = panelY + LIST_Y;

        graphics.enableScissor(lx, ly, lx + LIST_W, ly + LIST_H);

        int maxScroll    = Math.max(0, displayList.size() * ENTRY_H - LIST_H);
        int scrollOffset = (int)(scrollAmount * maxScroll);
        ResourceLocation selId = blockEntity.getCurrentRecipeId();

        List<Component> tooltipToRender = null;
        int ttX = 0, ttY = 0;

        for (int i = 0; i < displayList.size(); i++) {
            StanokRecipe recipe = displayList.get(i);
            int ey = ly + i * ENTRY_H - scrollOffset;
            if (ey + ENTRY_H < ly || ey > ly + LIST_H) continue;

            boolean selected = recipe.getId().equals(selId);
            int u = selected ? ENTRY_SELECTED_U : ENTRY_U;
            int v = selected ? ENTRY_SELECTED_V : ENTRY_V;
            graphics.blit(TEXTURE, lx, ey, u, v, LIST_W, ENTRY_H);

            // Иконка — первый выход рецепта
            if (!recipe.getOutputs().isEmpty()) {
                ItemStack icon = recipe.getOutputs().get(0);
                graphics.pose().pushPose();
                graphics.pose().translate(lx + ICON_X, ey + ICON_Y, 0);
                graphics.pose().scale(0.875f, 0.875f, 1f);
                graphics.renderItem(icon, 0, 0);
                graphics.pose().popPose();
            }

            // Название
            Component name = Component.translatable("recipe.trd." + recipe.getId().getPath());
            graphics.drawString(font, name, lx + ICON_X + 23, ey + ICON_Y + 4, 0xFFFFFF, false);

            // Тултип
            if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ey && mouseY < ey + ENTRY_H) {
                tooltipToRender = buildTooltip(recipe);
                ttX = mouseX;
                ttY = mouseY;
            }
        }

        graphics.disableScissor();

        if (tooltipToRender != null) {
            graphics.renderComponentTooltip(font, tooltipToRender, ttX, ttY);
        }
    }

    private List<Component> buildTooltip(StanokRecipe recipe) {
        List<Component> tt = new ArrayList<>();
        tt.add(Component.translatable("recipe.trd." + recipe.getId().getPath())
                .withStyle(Style.EMPTY.withColor(0xAEC6CF)));
        tt.add(Component.literal(""));

        // Входы
        tt.add(Component.translatable("gui.trd.stanok.tooltip.inputs")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        for (ItemStack is : recipe.getInputs()) {
            tt.add(Component.literal("  ").append(is.getDisplayName())
                    .append(Component.literal(" ×" + is.getCount())));
        }

        // Выходы
        tt.add(Component.translatable("gui.trd.stanok.tooltip.outputs")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        for (ItemStack is : recipe.getOutputs()) {
            tt.add(Component.literal("  ").append(is.getDisplayName())
                    .append(Component.literal(" ×" + is.getCount())));
        }

        tt.add(Component.literal(""));
        // Время
        tt.add(Component.translatable("gui.trd.stanok.tooltip.time",
                        String.format("%.1f", recipe.getProcessTicks() / 20.0f))
                .withStyle(Style.EMPTY.withColor(0x888888)));
        // Скорость
        tt.add(Component.translatable("gui.trd.stanok.tooltip.rpm",
                        recipe.getRequiredRpm(), (int)(recipe.getRequiredRpm() * 0.25))
                .withStyle(Style.EMPTY.withColor(0x88AAFF)));
        // Момент
        tt.add(Component.translatable("gui.trd.stanok.tooltip.torque",
                        recipe.getConsumedTorque())
                .withStyle(Style.EMPTY.withColor(0xFF8844)));
        // Насадка
        String carriageKey = "item.trd." + recipe.getCarriageType().getId() + "_carriage";
        tt.add(Component.translatable("gui.trd.stanok.tooltip.carriage")
                .append(Component.literal(" "))
                .append(Component.translatable(carriageKey))
                .withStyle(Style.EMPTY.withColor(0xFFFF44)));

        return tt;
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int trackX = panelX + SCROLLBAR_TRACK_X;
        int trackY = panelY + SCROLLBAR_TRACK_Y;
        int thumbY = trackY + (int)(scrollAmount * (SCROLLBAR_TRACK_H - THUMB_H));
        graphics.blit(TEXTURE, trackX, thumbY, THUMB_U, THUMB_V, SCROLLBAR_TRACK_W, THUMB_H);
    }

    // ─── Обработчики событий (вызываются из GUIStanok) ───

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Кнопка сброса
        int rx = panelX + RESET_X, ry = panelY + RESET_Y;
        if (mouseX >= rx && mouseX <= rx + RESET_W && mouseY >= ry && mouseY <= ry + RESET_H) {
            resetPressTimer = 10;
            playClick();
            ModPacketHandler.INSTANCE.sendToServer(
                    new ClearStanokRecipePacket(blockEntity.getBlockPos()));
            return true;
        }

        // Скроллбар
        int trackX = panelX + SCROLLBAR_TRACK_X;
        int trackY = panelY + SCROLLBAR_TRACK_Y;
        int thumbY = trackY + (int)(scrollAmount * (SCROLLBAR_TRACK_H - THUMB_H));
        if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_TRACK_W) {
            if (mouseY >= thumbY && mouseY <= thumbY + THUMB_H) {
                isDraggingScrollBar = true;
                return true;
            }
            if (mouseY >= trackY && mouseY <= trackY + SCROLLBAR_TRACK_H) {
                float ratio = (float)((mouseY - trackY) / (double)(SCROLLBAR_TRACK_H - THUMB_H));
                scrollAmount = Math.max(0f, Math.min(1f, ratio));
                return true;
            }
        }

        // Список
        int lx = panelX + LIST_X, ly = panelY + LIST_Y;
        if (mouseX >= lx && mouseX <= lx + LIST_W && mouseY >= ly && mouseY <= ly + LIST_H) {
            int maxScroll    = Math.max(0, displayList.size() * ENTRY_H - LIST_H);
            int scrollOffset = (int)(scrollAmount * maxScroll);
            int idx = (int)((mouseY - ly + scrollOffset) / ENTRY_H);
            if (idx >= 0 && idx < displayList.size()) {
                StanokRecipe recipe = displayList.get(idx);
                playClick();
                ModPacketHandler.INSTANCE.sendToServer(
                        new SelectStanokRecipePacket(blockEntity.getBlockPos(), recipe.getId()));
                return true;
            }
        }

        // Поиск
        if (mouseX >= panelX + SEARCH_X && mouseX <= panelX + SEARCH_X + SEARCH_W
                && mouseY >= panelY + SEARCH_Y && mouseY <= panelY + SEARCH_Y + SEARCH_H) {
            searchBox.setFocused(true);
            return true;
        }

        return false;
    }

    public boolean handleMouseScroll(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, displayList.size() * ENTRY_H - LIST_H);
        if (maxScroll > 0) {
            scrollAmount = Math.max(0f, Math.min(1f,
                    scrollAmount - (float)(delta * ENTRY_H / maxScroll)));
            return true;
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isDraggingScrollBar && button == 0) {
            int trackY = panelY + SCROLLBAR_TRACK_Y;
            float ratio = (float)((mouseY - trackY) / (double)(SCROLLBAR_TRACK_H - THUMB_H));
            scrollAmount = Math.max(0f, Math.min(1f, ratio));
            return true;
        }
        return false;
    }

    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDraggingScrollBar = false;
    }

    public boolean handleCharTyped(char c, int modifiers) {
        return searchBox.charTyped(c, modifiers);
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Проверяет, находится ли координата внутри панели.
     * Используется GUIStanok, чтобы закрыть панель при клике вне её.
     */
    public boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + PANEL_H;
    }

    private void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
