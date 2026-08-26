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
    
    private EditBox channelInput;
    private final RedstoneRadioBlockEntity radioEntity;
    private String lastChannelId = "";

    public GUIRedstoneRadio(RedstoneRadioMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.radioEntity = menu.getRadioEntity();
        this.imageWidth = 128;
        this.imageHeight = 128;
    }

    @Override
    protected void init() {
        super.init();
        
        int x = this.leftPos + 37;
        int y = this.topPos + 18;
        
        channelInput = new EditBox(this.font, x, y, 90, 12, Component.translatable("gui.trd.redstone_radio.channel"));
        channelInput.setMaxLength(32);
        channelInput.setValue(radioEntity.getChannelId());
        channelInput.setBordered(false);
        channelInput.setTextColor(0xFFFFFFFF);
        channelInput.setTextColorUneditable(0xFFFFFFFF);
        this.addWidget(channelInput);
        
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
        
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        
        if (radioEntity.isPowered()) {
            gui.blit(TEXTURE, x + 15, y + 6, 0, 128, 96, 12);
        } else {
            gui.blit(TEXTURE, x + 15, y + 6, 0, 140, 96, 12);
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
    }
}