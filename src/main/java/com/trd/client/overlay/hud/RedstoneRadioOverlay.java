package com.trd.client.overlay.hud;

import com.trd.block.basic.redstone.RedstoneRadioBlock;
import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioReceiverBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioTransmitterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class RedstoneRadioOverlay {
    public static final IGuiOverlay HUD_REDSTONE_RADIO = (ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (!(state.getBlock() instanceof RedstoneRadioBlock)) return;

        if (!(mc.level.getBlockEntity(pos) instanceof RedstoneRadioBlockEntity radio)) return;

        int centerX = screenWidth / 2 + 12;
        int centerY = screenHeight / 2 + 4;

        int lineHeight = 12;
        int bgColor = 0x80000000;
        int headerColor = 0xFFFFAA00;
        int valueColor = 0xFFFFFFFF;
        int onColor = 0xFF55FF55;
        int offColor = 0xFFFF5555;

        String title;
        if (radio instanceof RedstoneRadioTransmitterBlockEntity) {
            title = Component.translatable("hud.trd.redstone_radio.transmitter").getString();
        } else {
            title = Component.translatable("hud.trd.redstone_radio.receiver").getString();
        }

        String channelId = radio.getChannelId();
        String channelText = Component.translatable("hud.trd.redstone_radio.channel", 
            channelId.isEmpty() ? Component.translatable("hud.trd.redstone_radio.no_channel").getString() : channelId).getString();

        String stateText = Component.translatable("hud.trd.redstone_radio.state",
            radio.isPowered() ? Component.translatable("hud.trd.redstone_radio.on").getString() : 
                               Component.translatable("hud.trd.redstone_radio.off").getString()).getString();

        String signalText = "";
        if (radio instanceof RedstoneRadioTransmitterBlockEntity transmitter) {
            signalText = Component.translatable("hud.trd.redstone_radio.input_signal", transmitter.getLastSignalStrength()).getString();
        } else if (radio instanceof RedstoneRadioReceiverBlockEntity receiver) {
            signalText = Component.translatable("hud.trd.redstone_radio.output_signal", receiver.getOutputSignal()).getString();
        }

        int maxWidth = Math.max(mc.font.width(title),
            Math.max(mc.font.width(channelText),
                Math.max(mc.font.width(stateText), mc.font.width(signalText))));

        if (centerX + maxWidth + 8 > screenWidth) {
            centerX = screenWidth / 2 - maxWidth - 12;
        }

        int lines = 4;
        int bgX1 = centerX - 4;
        int bgY1 = centerY - 4;
        int bgX2 = centerX + maxWidth + 8;
        int bgY2 = centerY + lineHeight * lines + 4;
        guiGraphics.fill(bgX1, bgY1, bgX2, bgY2, bgColor);

        guiGraphics.drawString(mc.font, title, centerX, centerY, headerColor, true);
        guiGraphics.drawString(mc.font, channelText, centerX, centerY + lineHeight, valueColor, true);

        int stateColor = radio.isPowered() ? onColor : offColor;
        guiGraphics.drawString(mc.font, stateText, centerX, centerY + lineHeight * 2, stateColor, true);
        guiGraphics.drawString(mc.font, signalText, centerX, centerY + lineHeight * 3, valueColor, true);
    };
}