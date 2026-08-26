package com.trd.client.overlay.gui;

import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioMenu;
import com.trd.main.MainRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GUIRedstoneRadio extends AbstractContainerScreen<RedstoneRadioMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/redstone_radio_gui.png");

    // Видимая часть окна (как у электромотора)
    private static final int WIN_W = 126;
    private static final int WIN_H = 46;

    // Координаты из промпта (относительно leftPos/topPos)
    private static final int TITLE_X = 15;
    private static final int TITLE_Y = 6;
    private static final int INPUT_X = 37;
    private static final int INPUT_Y = 18;
    private static final int INPUT_W = 82;  // подогнано под ширину окна 126
    private static final int INPUT_H = 12;

    // Индикатор вкл/выкл (сдвинут ниже поля ввода, чтобы не налезать на название)
    private static final int INDICATOR_X = 15;
    private static final int INDICATOR_Y = 32;
    private static final int INDICATOR_W = 96;
    private static final int INDICATOR_H = 12;
    private static final int INDICATOR_U_ON = 0;
    private static final int INDICATOR_V_ON = 128;
    private static final int INDICATOR_U_OFF = 0;
    private static final int INDICATOR_V_OFF = 140;

    private EditBox channelInput;
    private final RedstoneRadioBlockEntity radioEntity;
    private String lastChannelId = "";

    public GUIRedstoneRadio(RedstoneRadioMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.radioEntity = menu.getRadioEntity();
        this.imageWidth = WIN_W;
        this.imageHeight = WIN_H;
        // Скрываем метку инвентаря игрока (его в GUI нет)
        this.inventoryLabelX = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // --- Поле ввода Channel ID (1 в 1 как в любом рабочем GUI) ---
        int x = this.leftPos + INPUT_X;
        int y = this.topPos + INPUT_Y;

        channelInput = new EditBox(this.font, x, y, INPUT_W, INPUT_H,
                Component.translatable("gui.trd.redstone_radio.channel"));
        channelInput.setMaxLength(32);
        channelInput.setValue(radioEntity.getChannelId());
        channelInput.setBordered(false);                 // рамка нарисована в текстуре
        channelInput.setTextColor(0xFFFFFFFF);
        channelInput.setTextColorUneditable(0xFFFFFFFF);
        channelInput.setEditable(true);
        channelInput.setFocused(true);                 // сразу активно
        channelInput.setCanLoseFocus(false);           // не теряет фокус по клику в сторону

        // ГЛАВНОЕ: addRenderableWidget вместо addWidget — тогда поле и рисуется, и печатает
        this.addRenderableWidget(channelInput);
        this.setFocused(channelInput);

        lastChannelId = radioEntity.getChannelId();
    }

    @Override
    public void onClose() {
        super.onClose();
        if (channelInput != null) {
            String newChannelId = channelInput.getValue().trim();
            if (!newChannelId.equals(lastChannelId)) {
                menu.setChannelId(newChannelId);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Фон GUI 126×46 из атласа 128×128
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 128, 128);

        // Индикатор состояния (ON/OFF)
        if (radioEntity.isPowered()) {
            gui.blit(TEXTURE, x + INDICATOR_X, y + INDICATOR_Y,
                    INDICATOR_U_ON, INDICATOR_V_ON, INDICATOR_W, INDICATOR_H, 128, 128);
        } else {
            gui.blit(TEXTURE, x + INDICATOR_X, y + INDICATOR_Y,
                    INDICATOR_U_OFF, INDICATOR_V_OFF, INDICATOR_W, INDICATOR_H, 128, 128);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // Название GUI по координатам из промпта: 15, 6
        gui.drawString(this.font, this.title, TITLE_X, TITLE_Y, 0xFFE0E0E0, false);
    }
}