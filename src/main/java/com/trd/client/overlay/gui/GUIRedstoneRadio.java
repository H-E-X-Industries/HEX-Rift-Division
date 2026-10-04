package com.trd.client.overlay.gui;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.RedstoneRadioMenu;
import com.trd.network.packet.redstone.RedstoneRadioChannelPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class GUIRedstoneRadio extends AbstractContainerScreen<RedstoneRadioMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/gui/machine/redstone_radio_gui.png");

    private static final int WIN_W = 126;
    private static final int WIN_H = 46;

    private static final int INPUT_X = 37;
    private static final int INPUT_Y = 18;
    private static final int INPUT_W = 82;
    private static final int INPUT_H = 12;

    private EditBox channelInput;
    private String lastChannelId = "";

    public GUIRedstoneRadio(RedstoneRadioMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
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
        channelInput.setValue(menu.getChannelId());
        channelInput.setBordered(false);
        channelInput.setTextColor(0xFFAEC6CF);
        channelInput.setTextColorUneditable(0xFFAEC6CF);
        channelInput.setEditable(true);
        channelInput.setFocused(true);
        channelInput.setCanLoseFocus(false);
        this.addRenderableWidget(channelInput);
        this.setFocused(channelInput);

        lastChannelId = menu.getChannelId();
    }

    @Override
    public void onClose() {
        super.onClose();
        if (channelInput != null) {
            String newChannelId = channelInput.getValue().trim();
            if (!newChannelId.equals(lastChannelId)) {
                // Отправляем пакет на сервер
                if (minecraft != null && minecraft.player != null) {
                    PacketDistributor.sendToServer(new RedstoneRadioChannelPacket(menu.getBlockPos(), newChannelId));
                }
                // Не обновляем локально – синхронизация с сервера обновит всё автоматически
            }
        }
    }

    // ВНИМАНИЕ: именно 9-арг перегрузка с размером текстуры 128, 128.
    // 7-арг вариант blit(texture, x, y, u, v, w, h) в 1.21.1 жёстко подставляет 256x256
    // (GuiGraphics: this.blit(atlas, x, y, 0, u, v, w, h, 256, 256)), а текстура у нас 128x128 —
    // из-за этого GUI рисуется вполовину размера и визуально "уезжает".
    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        gui.blit(TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 128, 128);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}
}
