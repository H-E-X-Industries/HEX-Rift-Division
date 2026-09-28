package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.fluids.PaintablePipeBlock;
import com.trd.block.entity.industrial.fluids.PaintablePipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class PaintablePipeOverlay {

    public static final IGuiOverlay HUD_PAINTABLE_PIPE = (ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Проверяем, что игрок смотрит на блок
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // Проверяем, что это окрашиваемая труба
        if (!(state.getBlock() instanceof PaintablePipeBlock)) return;

        // Получаем BlockEntity на клиенте
        if (!(mc.level.getBlockEntity(pos) instanceof PaintablePipeBlockEntity pipe)) return;

        // Позиция текста — справа снизу от перекрестия
        int centerX = screenWidth / 2 + 12;
        int centerY = screenHeight / 2 + 4;

        int lineHeight = 12;
        int bgColor = 0x80000000; // Полупрозрачный черный фон
        int headerColor = 0xFFFFAA00; // Оранжевый заголовок
        int valueColor = 0xFFFFFFFF; // Белый текст
        int emptyColor = 0xFFAAAAAA; // Серый для пустого состояния

        String header = Component.translatable("hud.trd.paintable_pipe.title").getString();

        Fluid fluid = pipe.getFilterFluid();
        boolean hasFluid = fluid != null && fluid != Fluids.EMPTY;

        MutableComponent fluidText;
        if (hasFluid) {
            String prefix = Component.translatable("hud.trd.paintable_pipe.fluid").getString();
            int tint = IClientFluidTypeExtensions.of(fluid).getTintColor() | 0xFF000000;
            fluidText = Component.literal(prefix)
                    .append(Component.translatable(fluid.getFluidType().getDescriptionId())
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint))));
        } else {
            fluidText = Component.literal(Component.translatable("hud.trd.paintable_pipe.empty").getString());
        }

        // Вычисляем максимальную ширину для фона
        int maxWidth = Math.max(mc.font.width(header), mc.font.width(fluidText));

        if (centerX + maxWidth + 8 > screenWidth) {
            centerX = screenWidth / 2 - maxWidth - 12;
        }

        // Фон
        int bgX1 = centerX - 4;
        int bgY1 = centerY - 4;
        int bgX2 = centerX + maxWidth + 8;
        int bgY2 = centerY + lineHeight * 2 + 4;
        guiGraphics.fill(bgX1, bgY1, bgX2, bgY2, bgColor);

        // Заголовок
        guiGraphics.drawString(mc.font, header, centerX, centerY, headerColor, true);

        // Текущая жидкость
        guiGraphics.drawString(mc.font, fluidText, centerX, centerY + lineHeight, hasFluid ? valueColor : emptyColor, true);
    };
}