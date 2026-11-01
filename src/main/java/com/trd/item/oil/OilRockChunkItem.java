package com.trd.item.oil;

import com.trd.api.oil.OilComposition;
import com.trd.api.oil.OilFraction;
import com.trd.api.oil.OilVeinManager;
import com.trd.api.oil.ReservoirType;
import com.trd.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OilRockChunkItem extends Item {

    public OilRockChunkItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack createFromVein(OilVeinManager.OilVeinData vein, Holder<Biome> biome) {
        return createFromComposition(vein.composition, biome);
    }

    public static ItemStack createFromComposition(OilComposition comp, Holder<Biome> biome) {
        ItemStack stack = new ItemStack(ModItems.OIL_ROCK_CHUNK.get());
        CompoundTag tag = comp.save();
        tag.putBoolean("Analyzed", false);

        ResourceLocation biomeKey = biome.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeKey != null) {
            tag.putString("SampleBiome", biomeKey.toString());
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isAnalyzed(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean("Analyzed");
    }

    public static void setAnalyzed(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("Analyzed", true));
    }

    public static Map<OilFraction, Integer> getFractions(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return Collections.emptyMap();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("Fractions", CompoundTag.TAG_COMPOUND)) return Collections.emptyMap();

        CompoundTag fracTag = tag.getCompound("Fractions");
        Map<OilFraction, Integer> map = new EnumMap<>(OilFraction.class);
        for (String k : fracTag.getAllKeys()) {
            try {
                map.put(OilFraction.valueOf(k), fracTag.getInt(k));
            } catch (IllegalArgumentException ignored) {}
        }
        return map;
    }

    public static ReservoirType getReservoirType(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return ReservoirType.POROUS_STONE;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("ReservoirType")) {
            try {
                return ReservoirType.valueOf(tag.getString("ReservoirType"));
            } catch (IllegalArgumentException ignored) {}
        }
        return ReservoirType.POROUS_STONE;
    }

    public static float getGasRatio(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 0.0f;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("GasRatio");
    }

    public static float getPressure(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 0.0f;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("Pressure");
    }

    public static float getSulfur(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 0.0f;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("Sulfur");
    }

    public static float getCorrosiveness(ItemStack stack) {
        float sulfur = getSulfur(stack);
        return Math.min(1.0f, sulfur * 20.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isAnalyzed(stack)) {
            tooltip.add(Component.literal("§c[!] Требуется анализ в оптическом микроскопе (серная кислота)"));
            return;
        }

        tooltip.add(Component.literal("§6=== [Результаты геохимического анализа] ==="));

        ReservoirType resType = getReservoirType(stack);
        tooltip.add(Component.literal("§7Коллектор: §f" + resType.getDisplayNameRu()));

        Map<OilFraction, Integer> fractions = getFractions(stack);
        if (!fractions.isEmpty()) {
            tooltip.add(Component.literal("§eУглеводородный спектр:"));
            for (OilFraction f : OilFraction.values()) {
                int pct = fractions.getOrDefault(f, 0);
                tooltip.add(Component.literal(String.format(Locale.ROOT, "  • %s: §f%d%%", f.getDisplayNameRu(), pct))
                        .withStyle(style -> style.withColor(f.getColor())));
            }
        }

        float gasRatio = getGasRatio(stack);
        float pressure = getPressure(stack);
        float sulfur = getSulfur(stack);
        float corrosiveness = getCorrosiveness(stack) * 100.0f;

        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Газовый фактор (GOR): §f%.1f%%", gasRatio * 100.0f)));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Пластовое давление: §f%.1f bar", pressure)));

        if (sulfur > 0.0f) {
            String corrSeverity = corrosiveness >= 65.0f ? "§4Критическая"
                    : (corrosiveness >= 35.0f ? "§cВысокая" : "§eУмеренная");

            tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Сернистость: §cКислая (%.1f%% S)", sulfur * 100.0f)));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Коррозийность сырья: %s (%.1f%%)", corrSeverity, corrosiveness)));
        } else {
            tooltip.add(Component.literal("§7Сернистость: §aСладкая (0.0% S)"));
            tooltip.add(Component.literal("§7Коррозийность сырья: §aИнертная (0.0%)"));
        }

        // Технологические требования и рекомендации по оборудованию
        tooltip.add(Component.literal("§dТехнологические требования:"));

        if (gasRatio >= 0.40f || pressure >= 150.0f) {
            tooltip.add(Component.literal(" §c⚠ ТРЕБУЕТСЯ ПРЕВЕНТОР (BOP): Критическое давление газа! Скважину сдетонирует без устьевого превентора."));
        }

        if (sulfur > 0.0f) {
            tooltip.add(Component.literal(String.format(Locale.ROOT,
                    " §e⚠ ТРЕБУЕТСЯ МОДУЛЬ ГИДРООЧИСТКИ: Высокое содержание H₂S (Коррозия %.1f%%). Без десульфуризации вызывает ускоренный износ бура и разрушение труб при выкачке.", corrosiveness)));
        }

        if (resType == ReservoirType.TIGHT_SHALE) {
            tooltip.add(Component.literal(" §6⚠ ТРЕБУЕТСЯ МОДУЛЬ ФРЕКИНГА: Низкопроницаемый сланец. Требуется гидроразрыв и подача бурового раствора."));
        } else if (resType == ReservoirType.TAR_SANDS) {
            tooltip.add(Component.literal(" §b⚠ ТРЕБУЕТСЯ ПАРОВОЙ ИНЖЕКТОР (EOR): Застывший битум. Требуется нагнетание пара для снижения вязкости."));
        }
    }
}
