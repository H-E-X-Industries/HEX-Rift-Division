package com.trd.item.conglomerates;

import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Кусочек металла — выход выщелащивателя из куска фракции.
 * Текстура {@code ore_chunk.png}, окрашенная в цвет металла из металлургии.
 * 1 кусочек = 1 самородок металла (переплавляется в плавильне).
 *
 * <p>NBT:
 * <ul>
 *   <li>{@code Metal} — id металла (напр. "iron", "tungsten").</li>
 *   <li>{@code Roast} — обжарен ли (доп. красный шлам в коксовой печи).</li>
 * </ul>
 */
public class MetalPieceItem extends Item {

    public static final String TAG_METAL = "Metal";
    public static final String TAG_ROAST = "Roast";

    public MetalPieceItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String metalId) {
        ItemStack stack = new ItemStack(com.trd.item.ModItems.METAL_PIECE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_METAL, metalId));
        return stack;
    }

    @Nullable
    public static String getMetal(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return null;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TAG_METAL);
    }

    public static boolean isRoasted(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_ROAST, CompoundTag.TAG_BYTE) && tag.getBoolean(TAG_ROAST);
    }

    public static void setRoasted(ItemStack stack, boolean roasted) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_ROAST, roasted));
    }

    /**
     * Возвращаем полный NBT как «значимый» для сравнения стаков: разные состояния
     * (металл, обжаренность) должны оставаться разными предметами в JEI, а не
     * сливаться в один по {@code item}. Без этого JEI 1.20.1 может считать
     * обжаренный и необжаренный кусочек одним и тем же.
     */
    public static int getDisplayColor(ItemStack stack) {
        String metalId = getMetal(stack);
        if (metalId == null) return 0xFFFFFF;
        int base = 0x767676;
        if (!isRoasted(stack)) return base;
        return heatColor(base);
    }

    private static int heatColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return ((r / 2) << 16) | ((g / 4) << 8) | (b / 4);
    }

    @Override
    public Component getName(ItemStack stack) {
        String metalId = getMetal(stack);
        MutableComponent metalName = metalId != null
                ? Component.literal(metalId).withStyle(style -> style.withColor(0x767676))
                : Component.translatable("item.trd.metal_piece.unknown");

        if (isRoasted(stack)) {
            return Component.translatable("item.trd.metal_piece.roasted", metalName);
        } else {
            return Component.translatable("item.trd.metal_piece.raw", metalName);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isRoasted(stack)) {
            tooltip.add(Component.translatable("tooltip.trd.metal_piece.roasted").withStyle(ChatFormatting.GOLD));
        }
    }
}
