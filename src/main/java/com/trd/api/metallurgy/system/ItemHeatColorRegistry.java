package com.trd.api.metallurgy.system;

import com.trd.event.HotItemHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ItemHeatColorRegistry {

    public enum HeatGradient {
        ORANGE_TO_WHITE(255, 100, 0),      // Ярко-оранжевый → белый (стандарт для металлов)
        RED_TO_WHITE(255, 50, 50),         // Ярко-красный → белый (высокотемпературные металлы)
        YELLOW_TO_WHITE(255, 220, 50),     // Ярко-жёлтый → белый (золото, медь)
        BLUE_TO_WHITE(100, 150, 255);      // Голубой → белый (магические/редкие металлы)

        public final int hotR, hotG, hotB;

        HeatGradient(int hotR, int hotG, int hotB) {
            this.hotR = hotR;
            this.hotG = hotG;
            this.hotB = hotB;
        }
    }

    private static final Map<Item, HeatGradient> itemGradients = new HashMap<>();
    private static final HeatGradient DEFAULT_GRADIENT = HeatGradient.ORANGE_TO_WHITE;

    public static void register(Item item, HeatGradient gradient) {
        if (item != null && item != Items.AIR) {
            itemGradients.put(item, gradient);
        }
    }

    public static void register(Block block, HeatGradient gradient) {
        if (block != null) {
            register(block.asItem(), gradient);
        }
    }

    public static void registerMixed(HeatGradient gradient, Object... itemsAndBlocks) {
        for (Object obj : itemsAndBlocks) {
            if (obj instanceof Item item) {
                register(item, gradient);
            } else if (obj instanceof Block block) {
                register(block, gradient);
            } else if (obj instanceof Supplier<?> supplier) {
                Object val = supplier.get();
                if (val instanceof Item it) register(it, gradient);
                else if (val instanceof Block bl) register(bl, gradient);
            }
        }
    }

    public static HeatGradient getGradient(ItemStack stack) {
        return itemGradients.getOrDefault(stack.getItem(), DEFAULT_GRADIENT);
    }

    public static int getHeatColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        if (!HotItemHandler.isHot(stack)) return -1;

        float ratio = HotItemHandler.getHeatRatio(stack);
        if (ratio < 0.05f) return -1;

        HeatGradient gradient = getGradient(stack);

        int r = (int) (gradient.hotR + (255 - gradient.hotR) * (1 - ratio));
        int g = (int) (gradient.hotG + (255 - gradient.hotG) * (1 - ratio));
        int b = (int) (gradient.hotB + (255 - gradient.hotB) * (1 - ratio));

        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    public static int getSlagHeatColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        int metalColor = tag.contains("Color") ? tag.getInt("Color") : 0x888888;
        int mr = (metalColor >> 16) & 0xFF;
        int mg = (metalColor >> 8) & 0xFF;
        int mb = metalColor & 0xFF;

        if (HotItemHandler.isHot(stack)) {
            float ratio = HotItemHandler.getHeatRatio(stack);

            int coldR = 0x33 + (mr / 4);
            int coldG = 0x33 + (mg / 4);
            int coldB = 0x33 + (mb / 4);

            int hotR = Math.min(255, mr + (int)(60 * ratio));
            int hotG = Math.min(255, mg + (int)(30 * ratio));
            int hotB = Math.min(255, mb + (int)(10 * ratio));

            int finalR = (int) (coldR + (hotR - coldR) * ratio);
            int finalG = (int) (coldG + (hotG - coldG) * ratio);
            int finalB = (int) (coldB + (hotB - coldB) * ratio);

            return (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
        } else {
            int dr = 0x33 + (mr / 4);
            int dg = 0x33 + (mg / 4);
            int db = 0x33 + (mb / 4);
            return (0xFF << 24) | (dr << 16) | (dg << 8) | db;
        }
    }
}
