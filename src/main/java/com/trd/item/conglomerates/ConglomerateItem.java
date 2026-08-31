package com.trd.item.conglomerates;

import com.trd.api.metallurgy.system.Metal;
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

    public static ItemStack createFromVein(Map<FractionType, Integer> fractions, int ou, String typeName, VeinModifier modifier) {
        return createFromVein(fractions, ou, typeName, modifier, null, null);
    }

    /**
     * Как {@link #createFromVein(Map, int, String, VeinModifier)}, но дополнительно
     * запекает источник жилы для тултипа: ключ биома ({@code namespace:path}) и
     * базовую температуру биома. Биом/температура добываются в момент добычи куска.
     */
    public static ItemStack createFromVein(Map<FractionType, Integer> fractions, int ou, String typeName,
                                           VeinModifier modifier,
                                           @Nullable ResourceLocation biomeKey, @Nullable Float temperature) {
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

        if (biomeKey != null) tag.putString("Biome", biomeKey.toString());
        if (temperature != null) tag.putFloat("Temp", temperature);

        tag.putInt("OU", ou);
        tag.putString("VeinType", typeName);

        // Новые куски по умолчанию НЕ проанализированы: подробности состава станут
        // доступны только после анализа в оптическом микроскопе.
        tag.putBoolean("Analyzed", false);

        stack.setTag(tag);
        return stack;
    }

    /**
     * Проанализирован ли кусок в оптическом микроскопе.
     * Старые куски без тега «Analyzed» считаются проанализированными (совместимость с сейвами).
     */
    public static boolean isAnalyzed(ItemStack stack) {
        if (!stack.hasTag()) return true;
        CompoundTag tag = stack.getTag();
        if (!tag.contains("Analyzed", CompoundTag.TAG_BYTE)) return true;
        return tag.getBoolean("Analyzed");
    }

    /** Помечает кусок как проанализированный (после анализа в оптическом микроскопе). */
    public static void setAnalyzed(ItemStack stack) {
        stack.getOrCreateTag().putBoolean("Analyzed", true);
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

    /** Ключ биома добычи ({@code namespace:path}) или {@code null}, если кусок старый. */
    @Nullable
    public static String getBiomeKey(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        return tag.contains("Biome", CompoundTag.TAG_STRING) ? tag.getString("Biome") : null;
    }

    /** Базовая температура биома добычи. {@code null}, если кусок старый. */
    @Nullable
    public static Float getTemperature(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        return tag.contains("Temp", CompoundTag.TAG_FLOAT) ? tag.getFloat("Temp") : null;
    }

    // Цвета тултипа
    private static final int C_BIOME_NAME = 0x55FFFF;         // голубой (как металл-бусты)
    private static final int C_METAL_BOOST = 0x55FFFF;        // голубой
    private static final int C_TEMPERATURE = 0xFF5555;        // красный (как фракция-бусты)
    private static final int C_FRACTION_BOOST = 0xFF5555;     // красный
    private static final int C_DEPTH_SURFACE = 0x55FF55;      // зелёный
    private static final int C_DEPTH_MEDIUM = 0xFFFF55;       // жёлтый
    private static final int C_DEPTH_DEEP = 0xAA00AA;         // фиолетовый

    /** Флаг примерного (рандомного) куска для JEI — в тултипе показывается пометка «это пример». */
    public static final String TAG_EXAMPLE = "Example";

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean example = stack.getOrCreateTag().getBoolean(TAG_EXAMPLE);

        // Пример из JEI — ярко-красная пометка, что это лишь пример результата.
        if (example) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.example")
                    .withStyle(ChatFormatting.RED));
        }

        if (!isAnalyzed(stack)) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.requires_analysis")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Map<FractionType, Integer> fractions = getFractions(stack);
        if (fractions.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.empty"));
            return;
        }

        tooltip.add(Component.translatable("tooltip.trd.conglomerate.contains_fractions"));
        for (FractionType fraction : fractions.keySet()) {
            MutableComponent header = Component.translatable("vein.trd.fraction." + fraction.getName(),
                            example ? "X" : fractions.get(fraction))
                    .withStyle(style -> style.withColor(fraction.getColor()));
            if (!example) {
                float fractionBoost = getFractionBoost(stack, fraction);
                if (fractionBoost != 1.0f) {
                    header.append(Component.literal(" (" + formatBoost(fractionBoost) + ")")
                            .withStyle(style -> style.withColor(C_FRACTION_BOOST)));
                }
            }
            tooltip.add(header);

            FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
            for (FractionLayerMatrix.Layer layer : matrix.getLayers()) {
                if (layer.isEmpty()) continue;
                if (example) {
                    tooltip.add(Component.translatable("tooltip.trd.conglomerate.layer",
                            layer.index() + 1, Component.literal("X")));
                } else {
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
        }

        tooltip.add(Component.translatable("tooltip.trd.conglomerate.ou", example ? "X" : getOU(stack))
                .withStyle(ChatFormatting.WHITE));

        String type = getVeinType(stack);
        boolean showVein = example || !type.equals("unknown");
        if (showVein) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.vein_type")
                    .withStyle(ChatFormatting.WHITE)
                    .append(example ? Component.literal("X") : depthComponent(type)));
        }

        String biomeKey = getBiomeKey(stack);
        if (example || biomeKey != null) {
            tooltip.add(Component.translatable("tooltip.trd.conglomerate.biome")
                    .withStyle(ChatFormatting.WHITE)
                    .append(example ? Component.literal("X") : biomeComponent(biomeKey)));
        }

        Float temperature = getTemperature(stack);
        if (example || temperature != null) {
            MutableComponent tempLine = Component.translatable("tooltip.trd.conglomerate.temperature")
                    .withStyle(ChatFormatting.WHITE);
            if (example) {
                tempLine.append(Component.literal("X").withStyle(style -> style.withColor(C_TEMPERATURE)));
            } else {
                tempLine.append(Component.literal(String.format(Locale.ROOT, "%.1f", temperature))
                        .withStyle(style -> style.withColor(C_TEMPERATURE)));
            }
            tooltip.add(tempLine);
        }
    }

    /** Название металла из metal-реестра (цвет — цвет металла) + его буст слева в скобках. */
    private MutableComponent metalWithBoost(String metalId, float boost) {
        MutableComponent name = MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, metalId))
                .map(metal -> (MutableComponent) Component.translatable(metal.getTranslationKey())
                        .withStyle(style -> style.withColor(metalDisplayColor(metalId, metal.getColor()))))
                .orElseGet(() -> {
                    Item granule = MetalGranules.forMetal(metalId);
                    return granule != null ? granule.getDescription().copy() : Component.literal(metalId);
                });
        if (boost != 1.0f) {
            name.append(Component.literal(" (" + formatBoost(boost) + ")")
                    .withStyle(style -> style.withColor(C_METAL_BOOST)));
        }
        return name;
    }

    /**
     * Цвет названия металла в тултипе. Исключение: вольфрам слишком тёмный на фоне
     * тултипа, поэтому для него используется цвет титана.
     */
    private int metalDisplayColor(String metalId, int metalColor) {
        if (metalId.equals("tungsten")) {
            return MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, "titanium"))
                    .map(Metal::getColor)
                    .orElse(0x767676);
        }
        return metalColor;
    }

    /** Локализованное название биома добычи (голубой). */
    private Component biomeComponent(String biomeKey) {
        if (biomeKey.contains(":")) {
            return Component.translatable("biome." + biomeKey.replace(':', '.'))
                    .withStyle(style -> style.withColor(C_BIOME_NAME));
        }
        return Component.literal(biomeKey).withStyle(style -> style.withColor(C_BIOME_NAME));
    }

    /** Локализованное название глубины жилы с цветом: поверхностная — зелёная, средняя — жёлтая, глубинная — фиолетовая. */
    private Component depthComponent(String depth) {
        int color = switch (depth) {
            case "surface" -> C_DEPTH_SURFACE;
            case "medium" -> C_DEPTH_MEDIUM;
            case "deep" -> C_DEPTH_DEEP;
            default -> -1;
        };
        if (color == -1) {
            return Component.literal(depth);
        }
        return Component.translatable("vein.trd.depth." + depth)
                .withStyle(style -> style.withColor(color));
    }

    private static String formatBoost(float boost) {
        return String.format(Locale.ROOT, "%.1f", boost);
    }
}