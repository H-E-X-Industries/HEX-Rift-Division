package com.trd.item.conglomerates;

import com.trd.api.metallurgy.system.MetallurgyRegistry;
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
        stack.getOrCreateTag().putString(TAG_METAL, metalId);
        return stack;
    }

    @Nullable
    public static String getMetal(ItemStack stack) {
        if (!stack.hasTag()) return null;
        return stack.getTag().getString(TAG_METAL);
    }

    public static boolean isRoasted(ItemStack stack) {
        if (!stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        return tag.contains(TAG_ROAST, CompoundTag.TAG_BYTE) && tag.getBoolean(TAG_ROAST);
    }

    public static void setRoasted(ItemStack stack, boolean roasted) {
        stack.getOrCreateTag().putBoolean(TAG_ROAST, roasted);
    }

    /**
     * Возвращаем полный NBT как «значимый» для сравнения стаков: разные состояния
     * (металл, обжаренность) должны оставаться разными предметами в JEI, а не
     * сливаться в один по {@code item}. Без этого JEI 1.20.1 может считать
     * обжаренный и необжаренный кусочек одним и тем же.
     */
    @Nullable
    @Override
    public CompoundTag getShareTag(ItemStack stack) {
        return stack.getTag();
    }

    /** Цвет металла из металлургии (для тинта текстуры). Обжаренный — оранжевее. */
    public static int getDisplayColor(ItemStack stack) {
        String metalId = getMetal(stack);
        if (metalId == null) return 0xFFFFFF;
        int base = MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, metalId))
                .map(m -> m.getColor())
                .orElse(0xFFFFFF);
        if (!isRoasted(stack)) return base;
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int nr = Math.min(255, (int) (br * 0.7f) + (int) (255 * 0.3f));
        int ng = Math.min(255, (int) (bg * 0.7f) + (int) (255 * 0.5f));
        int nb = Math.min(255, (int) (bb * 0.7f) + (int) (255 * 0.2f));
        return (nr << 16) | (ng << 8) | nb;
    }

    @Override
    public Component getName(ItemStack stack) {
        String metalId = getMetal(stack);
        MutableComponent metalName = metalId != null
                ? MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, metalId))
                    .map(metal -> (MutableComponent) Component.translatable(metal.getTranslationKey())
                            .withStyle(style -> style.withColor(TextColor.fromRgb(metal.getColor()))))
                    .orElseGet(() -> Component.literal(metalId != null ? metalId : ""))
                : Component.literal("");
        if (isRoasted(stack)) {
            metalName.append(Component.literal(" (").append(
                    Component.translatable("tooltip.trd.metal_piece.roasted")).append(")"));
        }
        return metalName;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isRoasted(stack)) {
            tooltip.add(Component.translatable("tooltip.trd.metal_piece.roasted").withStyle(ChatFormatting.GOLD));
        }
    }
}
