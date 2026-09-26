package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.rotation.StatorBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.block.entity.industrial.rotation.StatorBlockEntity;
import com.trd.item.industrial.energy.StatorCoilItem;
import com.trd.main.MainRegistry;
import com.trd.multiblock.system.MultiblockPartEntity;
import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class StatorOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            StatorBlockEntity stator = null;
            if (be instanceof StatorBlockEntity s) {
                stator = s;
            } else if (be instanceof MultiblockPartEntity part && part.getControllerPos() != null) {
                BlockEntity controllerBe = mc.level.getBlockEntity(part.getControllerPos());
                if (controllerBe instanceof StatorBlockEntity s) {
                    stator = s;
                }
            }

            if (stator != null) {
                GuiGraphics guiGraphics = event.getGuiGraphics();
                renderStatorHUD(guiGraphics, stator, guiGraphics.guiWidth(), guiGraphics.guiHeight(), mc.font, mc);
            }
        }
    }

    private static void renderStatorHUD(GuiGraphics guiGraphics, StatorBlockEntity stator, int screenWidth, int screenHeight, Font font, Minecraft mc) {
        BlockState ctrlState = stator.getBlockState();
        if (!ctrlState.hasProperty(StatorBlock.FACING) || !ctrlState.hasProperty(StatorBlock.AXIS)) return;

        Direction facing = ctrlState.getValue(StatorBlock.FACING);
        Direction.Axis axis = ctrlState.getValue(StatorBlock.AXIS);

        int coilCount = 0;
        long totalConversion = 0;
        for (int i = 0; i < 12; i++) {
            ItemStack stack = stator.getCoilsInventory().getStackInSlot(i);
            if (!stack.isEmpty()) {
                coilCount++;
                if (stack.getItem() instanceof StatorCoilItem coil) {
                    totalConversion += coil.getEnergyConversionRate();
                }
            }
        }

        long energyStored = stator.getEnergyStored();
        long maxEnergy = stator.getMaxEnergyDynamic();
        long load = stator.getConsumedTorque();

        long production = 0;
        if (energyStored < maxEnergy && mc.level != null) {
            BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
            BlockPos shaftPos = stator.getBlockPos().offset(holeOffset);
            if (mc.level.getBlockEntity(shaftPos) instanceof ShaftBlockEntity shaft) {
                if (shaft.hasRotor()) {
                    long speed = Math.abs(shaft.getSpeed());
                    float rotorEfficiency = 1.0f;
                    com.trd.api.rotation.RotorType rotorType = shaft.getRotorType();
                    if (rotorType != null) rotorEfficiency = rotorType.getEfficiency();
                    production = (long) ((speed * totalConversion * rotorEfficiency * 3.0f) / 40.0f);
                }
            }
        }

        int centerX = screenWidth / 2 + 12;
        int centerY = screenHeight / 2 + 4;

        int lineHeight = 12;
        int bgColor = 0x80000000;
        int headerColor = 0xFFFFAA00;
        int whiteColor = 0xFFFFFFFF;

        String gray = ChatFormatting.GRAY.toString();
        String white = ChatFormatting.WHITE.toString();
        String prodColStr = production > 0
                ? ChatFormatting.GREEN.toString()
                : ChatFormatting.RED.toString();

        String header = Component.translatable("hud.trd.stator.title").getString();
        String coilsLabel = Component.translatable("hud.trd.stator.coils_label").getString();
        String bufferLabel = Component.translatable("hud.trd.stator.buffer_label").getString();
        String loadLabel = Component.translatable("hud.trd.stator.load_label").getString();
        String prodLabel = Component.translatable("hud.trd.stator.production_label").getString();

        String coilsText = gray + coilsLabel + white + coilCount + " / 12";
        String bufferText = gray + bufferLabel + white + energyStored + " / " + maxEnergy + " JE";
        String loadText = gray + loadLabel + white + load + " Nm";
        String prodText = gray + prodLabel + prodColStr + production + " JE/t";

        int maxWidth = Math.max(font.width(header),
                Math.max(font.width(bufferText),
                        Math.max(font.width(loadText),
                                Math.max(font.width(coilsText), font.width(prodText)))));

        if (centerX + maxWidth + 8 > screenWidth) {
            centerX = screenWidth / 2 - maxWidth - 12;
        }

        int bgX1 = centerX - 4;
        int bgY1 = centerY - 4;
        int bgX2 = centerX + maxWidth + 8;
        int bgY2 = centerY + lineHeight * 5 + 4;
        guiGraphics.fill(bgX1, bgY1, bgX2, bgY2, bgColor);

        guiGraphics.drawString(font, header, centerX, centerY, headerColor, true);
        guiGraphics.drawString(font, coilsText, centerX, centerY + lineHeight, whiteColor, true);
        guiGraphics.drawString(font, bufferText, centerX, centerY + lineHeight * 2, whiteColor, true);
        guiGraphics.drawString(font, loadText, centerX, centerY + lineHeight * 3, whiteColor, true);
        guiGraphics.drawString(font, prodText, centerX, centerY + lineHeight * 4, whiteColor, true);
    }
}
