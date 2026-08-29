package com.trd.item.conglomerates;

import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.vein.FractionLayerMatrix;
import com.trd.api.vein.FractionType;
import com.trd.api.vein.MetalGranules;
import com.trd.api.vein.VeinModifier;
import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Кусок конгломерата. Содержит фракции (не готовые металлы),
 * которые нужно перерабатывать на станках.
 */
public class ConglomerateItem extends Item {
    public ConglomerateItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createFromVein(Map<FractionType, Integer> fractions, int ou, String typeName) {
        return createFromVein(fractions, ou, typeName, VeinModifier.NONE);
    }

    /**
     * Создаёт кусок и запекает в него веса металлов (с учётом биом-бустов).
     * Список весов читается в «Weights»: фракция → металл → вес обильности.
     * Используется на Этапах 2+ при разделении фракций и выщелачивании.
     * Множители фракций (температура) и металлов (биом-теги) сохраняются в
     * «FractionBoost»/«MetalBoost» для отображения в тултипе.
     */
    public static ItemStack createFromVein(Map<FractionType, Integer> fractions, int ou, String typeName, VeinModifier modifier) {
        ItemStack stack = new ItemStack(com.trd.item.ModItems.CONGLOMERATE_CHUNK.get());
        CompoundTag tag = new CompoundTag();

        CompoundTag fractionsTag = new CompoundTag();
        fractions.forEach((fraction, percent) -> fractionsTag.putInt(fraction.name(), percent));
        tag.put("Fractions", fractionsTag);

        CompoundTag weightsTag = new CompoundTag();
        for (FractionType fraction : fractions.keySet()) {
            CompoundTag metalWeights = new CompoundTag();
            FractionLayerMatrix.forFraction(fraction).bakeWeights(modifier)
                    .forEach((metal, weight) -> metalWeights.putInt(metal, weight));
            weightsTag.put(fraction.name(), metalWeights);
        }
        tag.put("Weights", weightsTag);

        CompoundTag fractionBoost = new CompoundTag();
        fractions.forEach((fraction, percent) -> {
            float mult = modifier.fractionMultiplier(fraction);
            if (mult != 1.0f) fractionBoost.putFloat(fraction.name(), mult);
        });
        if (!fractionBoost.isEmpty()) tag.put("FractionBoost", fractionBoost);

        CompoundTag metalBoost = new CompoundTag();
        for (FractionType fraction : fractions.keySet()) {
            for (String metal : FractionLayerMatrix.forFraction(fraction).getAllMetalWeights().keySet()) {
                float mult = modifier.metalMultiplier(metal);
                if (mult != 1.0f) metalBoost.putFloat(metal, mult);
            }
        }
        if (!metalBoost.isEmpty()) tag.put("MetalBoost", metalBoost);

        tag.putInt("OU", ou);
        tag.putString("VeinType", typeName);

        stack.setTag(tag);
        return stack;
    }

    public static Map<FractionType, Integer> getFractions(ItemStack stack) {
        if (!stack.hasTag()) return Collections.emptyMap();
        CompoundTag tag = stack.getTag();
        if (!tag.contains("Fractions", CompoundTag.TAG_COMPOUND)) return Collections.emptyMap();

        Map<FractionType, Integer> result = new HashMap<>();
        CompoundTag fractionsTag = tag.getCompound("Fractions");
        for (String key : fractionsTag.getAllKeys()) {
            try {
                FractionType fraction = FractionType.valueOf(key);
                result.put(fraction, fractionsTag.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Неизвестная фракция — пропускаем
            }
        }
        return result;
    }

    public static int getOU(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt("OU");
    }

    public static String getVeinType(ItemStack stack) {
        if (!stack.hasTag()) return "unknown";
        return stack.getTag().getString("VeinType");
    }

    /**
     * Запечённые веса металлов. Пустая карта, если кусок старый и без тега «Weights»
     * (тогда используются эталонные веса из {@link FractionLayerMatrix#getAllMetalWeights()}).
     */
    public static Map<FractionType, Map<String, Integer>> getBakedWeights(ItemStack stack) {
        if (!stack.hasTag()) return Collections.emptyMap();
        CompoundTag tag = stack.getTag();
        if (!tag.contains("Weights", CompoundTag.TAG_COMPOUND)) return Collections.emptyMap();

        Map<FractionType, Map<String, Integer>> result = new LinkedHashMap<>();
        CompoundTag weightsTag = tag.getCompound("Weights");
        for (String key : weightsTag.getAllKeys()) {
            try {
                FractionType fraction = FractionType.valueOf(key);
                Map<String, Integer> metalWeights = new LinkedHashMap<>();
                CompoundTag metalTag = weightsTag.getCompound(key);
                for (String metal : metalTag.getAllKeys()) {
                    metalWeights.put(metal, metalTag.getInt(metal));
                }
                result.put(fraction, metalWeights);
            } catch (IllegalArgumentException ignored) {
                // Неизвестная фракция — пропускаем
            }
        }
        return result;
    }

    /**
     * Множитель ФРАКЦИИ (температура биома), полученный куском.
     * 1.0, если буста не было или кусок старый (без тега).
     */
    public static float getFractionBoost(ItemStack stack, FractionType fraction) {
        if (!stack.hasTag()) return 1.0f;
        CompoundTag tag = stack.getTag();
        if (!tag.contains("FractionBoost", CompoundTag.TAG_COMPOUND)) return 1.0f;
        CompoundTag boosts = tag.getCompound("FractionBoost");
        return boosts.contains(fraction.name()) ? boosts.getFloat(fraction.name()) : 1.0f;
    }

    /**
     * Множитель МЕТАЛЛА (биом-теги), полученный куском.
     * 1.0, если буста не было или кусок старый (без тега).
     */
    public static float getMetalBoost(ItemStack stack, String metal) {
        if (!stack.hasTag()) return 1.0f;
        CompoundTag tag = stack.getTag();
        if (!tag.contains("MetalBoost", CompoundTag.TAG_COMPOUND)) return 1.0f;
        CompoundTag boosts = tag.getCompound("MetalBoost");
        return boosts.contains(metal) ? boosts.getFloat(metal) : 1.0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Map<FractionType, Integer> fractions = getFractions(stack);
        if (fractions.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.empty"));
            return;
        }

        tooltip.add(Component.translatable("tooltip.trd.conglomerate.contains_fractions"));
        for (FractionType fraction : fractions.keySet()) {
            Component header = Component.translatable("vein.trd.fraction." + fraction.getName(), fractions.get(fraction))
                    .withStyle(style -> style.withColor(fraction.getColor()))
                    .append(Component.literal(" (" + formatBoost(getFractionBoost(stack, fraction)) + ")")
                            .withStyle(style -> style.withColor(ChatFormatting.GRAY)));
            tooltip.add(header);

            FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
            for (FractionLayerMatrix.Layer layer : matrix.getLayers()) {
                if (layer.isEmpty()) continue;
                MutableComponent metals = Component.empty();
                boolean first = true;
                for (FractionLayerMatrix.MetalEntry entry : layer.metals()) {
                    if (!first) metals.append(", ");
                    first = false;
                    metals.append(metalWithBoost(entry.metal(), getMetalBoost(stack, entry.metal())));
                }
                tooltip.add(Component.translatable("tooltip.trd.conglomerate.layer", layer.index() + 1, metals));
            }
        }

        int ou = getOU(stack);
        tooltip.add(Component.translatable("tooltip.trd.conglomerate.ou", ou)
                .withStyle(ChatFormatting.WHITE));

        String type = getVeinType(stack);
        if (!type.equals("unknown")) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.vein_type")
                    .withStyle(ChatFormatting.WHITE)
                    .append(depthComponent(type)));
        }
    }

    /** Название металла из metal-реестра (цвет — цвет металла) + его буст в скобках. */
    private MutableComponent metalWithBoost(String metalId, float boost) {
        MutableComponent name = MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, metalId))
                .map(metal -> (MutableComponent) Component.translatable(metal.getTranslationKey())
                        .withStyle(style -> style.withColor(metal.getColor())))
                .orElseGet(() -> {
                    Item granule = MetalGranules.forMetal(metalId);
                    return granule != null ? granule.getDescription().copy() : Component.literal(metalId);
                });
        return name.append(Component.literal(" (" + formatBoost(boost) + ")")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)));
    }

    /** Локализованное название глубины жилы с цветом: поверхностная — зелёная, средняя — голубая, глубинная — фиолетовая. */
    private Component depthComponent(String depth) {
        int color = switch (depth) {
            case "surface" -> 0x55FF55;
            case "medium" -> 0x55FFFF;
            case "deep" -> 0xAA00AA;
            default -> ChatFormatting.WHITE.getColor();
        };
        if (color == ChatFormatting.WHITE.getColor()) {
            return Component.literal(depth);
        }
        return Component.translatable("vein.trd.depth." + depth)
                .withStyle(style -> style.withColor(color));
    }

    private static String formatBoost(float boost) {
        return String.format(Locale.ROOT, "%.1f", boost);
    }
}