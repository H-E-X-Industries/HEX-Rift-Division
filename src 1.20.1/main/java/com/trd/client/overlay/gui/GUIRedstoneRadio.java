package com.trd.client.overlay.gui;

import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.menu.industrial.RedstoneRadioMenu;
import com.trd.main.MainRegistry;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.redstone.RedstoneRadioChannelPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GUIRedstoneRadio extends AbstractContainerScreen<RedstoneRadioMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/redstone_radio_gui.png");

    private static final int WIN_W = 126;
    private static final int WIN_H = 46;

    private static final int TITLE_X = 15;
    private static final int TITLE_Y = 3;
    private static final int INPUT_X = 37;
    private static final int INPUT_Y = 18;
    private static final int INPUT_W = 82;
    private static final int INPUT_H = 12;

    private EditBox channelInput;
    private final RedstoneRadioBlockEntity radioEntity;
    private String lastChannelId = "";

    public GUIRedstoneRadio(RedstoneRadioMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.radioEntity = menu.getRadioEntity();
        this.imageWidth = WIN_W;
        this.imageHeight = WIN_H;
        this.inventoryLabelX = -1000;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos + INPUT_X;
        int y = this.topPos + INPUT_Y;

        channelInput = new EditBox(this.font, x, y, INPUT_W, INPUT_H,
                Component.translatable("gui.trd.redstone_radio.channel"));
        channelInput.setMaxLength(32);
        channelInput.setValue(radioEntity.getChannelId());
        channelInput.setBordered(false);
        channelInput.setTextColor(0xFFAEC6CF);
        channelInput.setTextColorUneditable(0xFFAEC6CF);
        channelInput.setEditable(true);
        channelInput.setFocused(true);
        channelInput.setCanLoseFocus(false);
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
                // Отправляем пакет на сервер
                if (minecraft != null && minecraft.player != null) {
                    ModPacketHandler.INSTANCE.sendToServer(
                            new RedstoneRadioChannelPacket(radioEntity.getBlockPos(), newChannelId)
                    );
                }
                // Не обновляем локально – синхронизация с сервера обновит всё автоматически
                // lastChannelId обновится после получения пакета
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 128, 128);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}
}