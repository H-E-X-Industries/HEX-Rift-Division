package com.trd.item.conglomerates;

import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.vein.FractionLayerMatrix;
import com.trd.api.vein.FractionType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Кусок фракции («пирог») — один сорт фракции, отделённый от конгломерата в дробителе.
 * Хранит свою долю OU и слоистую структуру («пирог»). Состояние куска меняется по цепочке:
 * <pre>
 *   RAW (сырой, тёмный)  --[промывка водой в выщелачивателе]--> CLEAN (чистый, светлый)
 *   RAW|CLEAN --[обжарка в коксовой печи]--> ROASTED (оранжевее, даёт 50 мБ красного шлама)
 * </pre>
 * Обработка реагентом (модульная переработка) извлекает слои по одному и оставляет «ядро»
 *
 * <p>NBT:
 * <ul>
 *   <li>{@code Fraction} — тип фракции ({@link FractionType#name()}).</li>
 *   <li>{@code OU} — объём куска (целое число гранул по всем слоям).</li>
 *   <li>{@code Layers} — карта металл → OU слоя (запечённая при создании, из
 *       {@link FractionLayerMatrix#distributeLayerOus(int)}).</li>
 *   <li>{@code Weights} — запечённые веса металлов (для прокрутки).</li>
 *   <li>{@code State} — {@code raw} | {@code clean} | {@code roasted}.</li>
 *   <li>{@code ProcessedLayers} — сколько слоёв уже извлечено (ядро).</li>
 *   <li>{@code Analyzed} — была ли раскрыта информация о слоях (из анализа конгломерата).</li>
 * </ul>
 */
public class FractionChunkItem extends Item {

    public static final String STATE_RAW = "raw";
    public static final String STATE_CLEAN = "clean";
    public static final String STATE_ROASTED = "roasted";

    // NBT ключи
    public static final String TAG_FRACTION = "Fraction";
    public static final String TAG_OU = "OU";
    public static final String TAG_LAYERS = "Layers";
    public static final String TAG_WEIGHTS = "Weights";
    public static final String TAG_STATE = "State";
    public static final String TAG_PROCESSED = "ProcessedLayers";
    public static final String TAG_ANALYZED = "Analyzed";
    public static final String TAG_EXAMPLE = "Example";

    public FractionChunkItem(Properties properties) {
        super(properties);
    }

    // ══════════════════ СОЗДАНИЕ ══════════════════

    /**
     * Создаёт сырой кусок фракции с запечёнными слоями «пирога».
     * Слои заполняются из {@link FractionLayerMatrix#distributeLayerOus(int)}.
     *
     * @param fraction  тип фракции
     * @param ou        доля OU фракции (целое число гранул)
     * @param analyzed  был ли конгломерат-источник проанализирован (показывать слои в тултипе)
     */
    public static ItemStack create(FractionType fraction, int ou, boolean analyzed) {
        ItemStack stack = new ItemStack(com.trd.item.ModItems.FRACTION_CHUNK.get());
        CompoundTag tag = new CompoundTag();

        tag.putString(TAG_FRACTION, fraction.name());
        tag.putInt(TAG_OU, ou);

        FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
        Map<Integer, Integer> layerOus = matrix.distributeLayerOus(ou);
        CompoundTag layersTag = new CompoundTag();
        for (Map.Entry<Integer, Integer> e : layerOus.entrySet()) {
            if (e.getValue() == 0) continue; // пустые слои не храним
            CompoundTag layer = new CompoundTag();
            layer.putInt("OU", e.getValue());
            // Металлы слоя по весам фракции
            Map<String, Integer> metals = matrix.distributeMetals(e.getValue(), e.getKey());
            CompoundTag metalTag = new CompoundTag();
            metals.forEach(metalTag::putInt);
            layer.put("Metals", metalTag);
            layersTag.put(String.valueOf(e.getKey()), layer);
        }
        tag.put(TAG_LAYERS, layersTag);

        // Запечённые веса (для прокрутки уценённым выходом)
        CompoundTag weightsTag = new CompoundTag();
        matrix.bakeWeights(com.trd.api.vein.VeinModifier.NONE).forEach(weightsTag::putInt);
        tag.put(TAG_WEIGHTS, weightsTag);

        tag.putString(TAG_STATE, STATE_RAW);
        tag.putInt(TAG_PROCESSED, 0);
        tag.putBoolean(TAG_ANALYZED, analyzed);

        stack.setTag(tag);
        return stack;
    }

    // ══════════════════ ЧТЕНИЕ NBT ══════════════════

    @Nullable
    public static FractionType getFraction(ItemStack stack) {
        if (!stack.hasTag()) return null;
        String name = stack.getTag().getString(TAG_FRACTION);
        try {
            return FractionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int getOU(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt(TAG_OU);
    }

    public static String getState(ItemStack stack) {
        if (!stack.hasTag()) return STATE_RAW;
        return stack.getTag().getString(TAG_STATE);
    }

    public static void setState(ItemStack stack, String state) {
        stack.getOrCreateTag().putString(TAG_STATE, state);
    }

    public static boolean isClean(ItemStack stack) {
        String s = getState(stack);
        return STATE_CLEAN.equals(s) || STATE_ROASTED.equals(s);
    }

    public static boolean isRoasted(ItemStack stack) {
        return STATE_ROASTED.equals(getState(stack));
    }

    public static boolean isAnalyzed(ItemStack stack) {
        if (!stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        if (!tag.contains(TAG_ANALYZED, CompoundTag.TAG_BYTE)) return false;
        return tag.getBoolean(TAG_ANALYZED);
    }

    public static void setAnalyzed(ItemStack stack, boolean analyzed) {
        stack.getOrCreateTag().putBoolean(TAG_ANALYZED, analyzed);
    }

    /** Сколько слоёв уже извлечено (индекс следующего необработанного слоя). */
    public static int getProcessedLayers(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt(TAG_PROCESSED);
    }

    public static void setProcessedLayers(ItemStack stack, int count) {
        stack.getOrCreateTag().putInt(TAG_PROCESSED, count);
    }

    /** Карта индекс слоя → (металл → OU). Пустые/извлечённые слои не входит. */
    public static Map<Integer, Map<String, Integer>> getLayerMap(ItemStack stack) {
        Map<Integer, Map<String, Integer>> result = new LinkedHashMap<>();
        if (!stack.hasTag()) return result;
        CompoundTag tag = stack.getTag();
        if (!tag.contains(TAG_LAYERS, CompoundTag.TAG_COMPOUND)) return result;
        int processed = getProcessedLayers(stack);
        CompoundTag layersTag = tag.getCompound(TAG_LAYERS);
        for (String key : layersTag.getAllKeys()) {
            try {
                int index = Integer.parseInt(key);
                if (index < processed) continue; // уже извлечён
                CompoundTag layer = layersTag.getCompound(key);
                Map<String, Integer> metals = new LinkedHashMap<>();
                CompoundTag metalTag = layer.getCompound("Metals");
                for (String metal : metalTag.getAllKeys()) {
                    int ous = metalTag.getInt(metal);
                    if (ous > 0) metals.put(metal, ous);
                }
                if (!metals.isEmpty()) result.put(index, metals);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /** Запечённые веса металлов фракции (для прокрутки). */
    public static Map<String, Integer> getWeights(ItemStack stack) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!stack.hasTag()) return result;
        CompoundTag tag = stack.getTag();
        if (!tag.contains(TAG_WEIGHTS, CompoundTag.TAG_COMPOUND)) return result;
        CompoundTag weightsTag = tag.getCompound(TAG_WEIGHTS);
        for (String metal : weightsTag.getAllKeys()) {
            result.put(metal, weightsTag.getInt(metal));
        }
        return result;
    }

    // ══════════════════ ЦВЕТ (для тинта текстуры) ══════════════════

    /**
     * Цвет отображения куска. Сырой — тёмный оттенок цвета фракции, очищенный — светлый,
     * обжаренный — с оранжевым сдвигом.
     */
    public static int getDisplayColor(ItemStack stack) {
        FractionType fraction = getFraction(stack);
        int base = fraction != null ? fraction.getColor() : 0xFFFFFF;
        float factor = 0.55f; // сырой: тёмный
        String state = getState(stack);
        if (STATE_CLEAN.equals(state)) {
            factor = 1.0f;
        } else if (STATE_ROASTED.equals(state)) {
            // оранжевый сдвиг
            int r = (int) (255 * 0.9f);
            int g = (int) (255 * 0.5f);
            int bRel = (int) (255 * 0.2f);
            int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
            int nr = Math.min(255, (int) (br * 0.7f) + (int) (r * 0.3f));
            int ng = Math.min(255, (int) (bg * 0.7f) + g);
            int nb = Math.min(255, (int) (bb * 0.7f) + bRel);
            return (nr << 16) | (ng << 8) | nb;
        }
        int r = (int) (((base >> 16) & 0xFF) * factor);
        int g = (int) (((base >> 8) & 0xFF) * factor);
        int b = (int) ((base & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    // ══════════════════ ТУЛТИП ══════════════════

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean example = stack.getOrCreateTag().getBoolean(TAG_EXAMPLE);
        FractionType fraction = getFraction(stack);

        if (fraction != null) {
            MutableComponent header = (fraction != null
                    ? Component.translatable("vein.trd.fraction." + fraction.getName(), example ? "X" : "")
                    : Component.empty())
                    .withStyle(style -> style.withColor(fractionColor(fraction)));
            tooltip.add(header);
        }

        tooltip.add(Component.translatable("tooltip.trd.fraction_chunk.ou", example ? "X" : getOU(stack))
                .withStyle(ChatFormatting.WHITE));

        String state = getState(stack);
        tooltip.add(Component.translatable("tooltip.trd.fraction_chunk.state." + state)
                .withStyle(ChatFormatting.GRAY));

        if (isAnalyzed(stack) && !example) {
            Map<Integer, Map<String, Integer>> layers = getLayerMap(stack);
            if (layers.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.trd.fraction_chunk.depleted")
                        .withStyle(ChatFormatting.RED));
            } else {
                for (Map.Entry<Integer, Map<String, Integer>> e : layers.entrySet()) {
                    FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
                    FractionLayerMatrix.Layer layer = matrix.getLayer(e.getKey());
                    MutableComponent metals = Component.empty();
                    boolean first = true;
                    for (Map.Entry<String, Integer> m : e.getValue().entrySet()) {
                        if (!first) metals.append(", ");
                        first = false;
                        metals.append(metalComponent(m.getKey()));
                    }
                    tooltip.add(Component.translatable("tooltip.trd.fraction_chunk.layer",
                                    layer != null ? layer.index() + 1 : e.getKey() + 1, metals)
                            .withStyle(ChatFormatting.WHITE));
                }
            }
        } else if (!isAnalyzed(stack)) {
            tooltip.add(Component.translatable("tooltip.trd.fraction_chunk.requires_analysis")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private int fractionColor(FractionType fraction) {
        return fraction.getColor();
    }

    private MutableComponent metalComponent(String metalId) {
        return MetallurgyRegistry.get(new ResourceLocation(MainRegistry.MOD_ID, metalId))
                .map(metal -> (MutableComponent) Component.translatable(metal.getTranslationKey())
                        .withStyle(style -> style.withColor(TextColor.fromRgb(metal.getColor()))))
                .orElseGet(() -> Component.literal(metalId));
    }
}
