package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.multiblock.industrial.fueltanks.FuelTankBlock;
import com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;

public class FuelTankRenderer implements BlockEntityRenderer<FuelTankBlockEntity> {

    public FuelTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FuelTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.fluidFilter == null || be.fluidFilter.equals("none")) return;

        Fluid filterFluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(be.fluidFilter));
        if (filterFluid == null || filterFluid == Fluids.EMPTY) return;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(FuelTankBlock.FACING)) return;
        Direction facing = state.getValue(FuelTankBlock.FACING);

        Component fluidName = Component.translatable(filterFluid.getFluidType().getDescriptionId());
        int tintColor = IClientFluidTypeExtensions.of(filterFluid).getTintColor();
        
        if (filterFluid == Fluids.LAVA || filterFluid == Fluids.FLOWING_LAVA) {
            tintColor = 0xFF5500; // Огненно-оранжевый
        } else if (filterFluid == Fluids.WATER || filterFluid == Fluids.FLOWING_WATER) {
            tintColor = 0x3F76E4; // Синий
        }
        
        // Убираем прозрачность
        tintColor = 0xFF000000 | tintColor;

        poseStack.pushPose();

        // Центрируемся в контроллере
        poseStack.translate(0.5, 0.5, 0.5);

        Font font = Minecraft.getInstance().font;
        String str = fluidName.getString();

        // Рисуем с двух сторон (спереди и сзади)
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();

            // Поворачиваем текст в зависимости от направления мультиблока
            // i == 0 - спереди, i == 1 - сзади
            float rot = -facing.toYRot();
            if (i == 1) {
                rot += 180;
            }
            poseStack.mulPose(Axis.YP.rotationDegrees(rot));

            // Смещение:
            // Y: на 1 блок выше (Y = 1.0)
            // Z: 1.5 блока - 2 пикселя (0.125 блока) = 1.375. Добавляем 0.001 для z-fighting = 1.376f
            poseStack.translate(0, 1.0f, 1.376f); 
            
            // Максимальная ширина для текста - 26 пикселей блока (26 / 16 = 1.625 блока)
            // Максимальная высота - 12 пикселей блока (12 / 16 = 0.75 блока)
            float maxWidth = 26.0f / 16.0f; // 1.625f
            float maxHeight = 12.0f / 16.0f; // 0.75f
            float baseScale = 0.06f;
            
            // Разделяем составные названия (с пробелами) на 2 строки; одиночные слова НЕ делим
            java.util.List<String> textLines = splitTextIntoLines(str, font, maxWidth, baseScale);
            
            // Находим реальную максимальную ширину строки
            int maxLineW = 0;
            for (String line : textLines) {
                int w = font.width(line);
                if (w > maxLineW) maxLineW = w;
            }
            
            // Динамический масштаб (пропорционально ширине и высоте)
            float dynamicScale = baseScale;
            if (maxLineW > 0) {
                dynamicScale = Math.min(baseScale, maxWidth / (float) maxLineW);
            }
            
            // Ограничение по высоте (максимум 12 пикселей блока)
            float totalHeight = textLines.size() * font.lineHeight;
            float heightScale = maxHeight / totalHeight;
            dynamicScale = Math.min(dynamicScale, heightScale);

            // Масштабируем и переворачиваем текст
            poseStack.scale(dynamicScale, -dynamicScale, dynamicScale);

            // Координаты центров табличек:
            // Левая табличка: px [20..46], центр = 33 px. Относительно центра контроллера (56 px) = 33 - 56 = -23 px (-1.4375 блока)
            // Правая табличка: px [66..92], центр = 79 px. Относительно центра контроллера (56 px) = 79 - 56 = +23 px (+1.4375 блока)
            float leftCenterX = (-23.0f / 16.0f) / dynamicScale;
            float rightCenterX = (23.0f / 16.0f) / dynamicScale;
            
            // Вычисляем стартовый Y для вертикального центрирования блока текста
            float startY = -totalHeight / 2f;
            
            // Рисуем каждую строку
            for (int j = 0; j < textLines.size(); j++) {
                String line = textLines.get(j);
                float w = (float) font.width(line);
                float yOffset = startY + j * font.lineHeight;
                
                // Рисуем слева
                font.drawInBatch(line, leftCenterX - w / 2f, yOffset, tintColor, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
                // Рисуем справа
                font.drawInBatch(line, rightCenterX - w / 2f, yOffset, tintColor, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            }

            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private java.util.List<String> splitTextIntoLines(String str, Font font, float maxWidth, float baseScale) {
        java.util.List<String> textLines = new java.util.ArrayList<>();
        if (str == null || str.isEmpty()) return textLines;

        // Если текст в одну строку свободно помещается в базовый масштаб без превышения maxWidth — оставляем в 1 строку
        if (font.width(str) * baseScale <= maxWidth) {
            textLines.add(str);
            return textLines;
        }

        // Если в названии есть пробелы (составное название из нескольких слов),
        // разделяем на 2 строки по оптимальному пробелу, чтобы максимально сбалансировать ширину строк
        if (str.contains(" ")) {
            String bestLine1 = null;
            String bestLine2 = null;
            int minMaxW = Integer.MAX_VALUE;

            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == ' ') {
                    String line1 = str.substring(0, i).trim();
                    String line2 = str.substring(i + 1).trim();
                    if (!line1.isEmpty() && !line2.isEmpty()) {
                        int w1 = font.width(line1);
                        int w2 = font.width(line2);
                        int maxW = Math.max(w1, w2);
                        if (maxW < minMaxW) {
                            minMaxW = maxW;
                            bestLine1 = line1;
                            bestLine2 = line2;
                        }
                    }
                }
            }

            if (bestLine1 != null && bestLine2 != null) {
                textLines.add(bestLine1);
                textLines.add(bestLine2);
                return textLines;
            }
        }

        // Одиночные слова (без пробелов, например "Хлороводород") НЕ разделяем — остаются в 1 строку
        textLines.add(str);
        return textLines;
    }
    
    @Override
    public boolean shouldRenderOffScreen(FuelTankBlockEntity pBlockEntity) {
        return true;
    }
}
