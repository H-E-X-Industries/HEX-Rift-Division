package com.trd.item.nature.oil;

import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilFraction;
import com.trd.api.nature.ore.oil.OilVeinManager;
import com.trd.api.nature.ore.oil.ReservoirType;
import com.trd.item.ModItems;
import net.minecraft.ChatFormatting;
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
        return createFromComposition(vein.composition, biome, vein.remainingUnits, vein.maxUnits);
    }

    public static ItemStack createFromComposition(OilComposition comp, Holder<Biome> biome) {
        return createFromComposition(comp, biome, -1L, -1L);
    }

    public static ItemStack createFromComposition(OilComposition comp, Holder<Biome> biome, long remainingMb, long maxMb) {
        ItemStack stack = new ItemStack(ModItems.OIL_ROCK_CHUNK.get());
        CompoundTag tag = comp.save();
        tag.putBoolean("Analyzed", false);

        ResourceLocation biomeKey = comp.getBiomeKey();
        if (biomeKey == null && biome != null) {
            biomeKey = biome.unwrapKey().map(k -> k.location()).orElse(null);
        }
        if (biomeKey != null) {
            tag.putString("SampleBiome", biomeKey.toString());
        }
        if (maxMb > 0L) {
            tag.putLong("VeinRemainingMb", Math.max(0L, remainingMb));
            tag.putLong("VeinMaxMb", maxMb);
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

    public static boolean isField(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return false;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("IsField");
    }

    public static String getFieldName(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return "";
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("FieldName");
    }

    public static int getTotalPockets(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 1;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("TotalPockets");
    }

    public static int getPocketIndex(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return 1;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("PocketIndex");
    }

    public static Map<OilFraction, Integer> getFractions(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return Collections.emptyMap();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("Fractions", CompoundTag.TAG_COMPOUND)) return Collections.emptyMap();

        CompoundTag fracTag = tag.getCompound("Fractions");
        Map<OilFraction, Integer> map = new EnumMap<>(OilFraction.class);
        for (String k : fracTag.getAllKeys()) {
            if ("BITUMEN".equals(k)) {
                map.merge(OilFraction.FUEL_OIL, fracTag.getInt(k), Integer::sum);
                continue;
            }
            try {
                map.put(OilFraction.valueOf(k), fracTag.getInt(k));
            } catch (IllegalArgumentException ignored) {}
        }
        if (!map.isEmpty()) {
            for (OilFraction f : OilFraction.values()) {
                map.putIfAbsent(f, 0);
            }
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

    public static long getVeinRemainingMb(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return -1L;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("VeinMaxMb") ? tag.getLong("VeinRemainingMb") : -1L;
    }

    public static long getVeinMaxMb(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return -1L;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("VeinMaxMb") ? tag.getLong("VeinMaxMb") : -1L;
    }

    private static String formatGroupedLong(long value) {
        String s = Long.toString(Math.max(0L, value));
        if (s.length() <= 3) return s;
        StringBuilder sb = new StringBuilder(s.length() + s.length() / 3);
        int mod = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - mod) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isAnalyzed(stack)) {
            tooltip.add(Component.literal("§c[!] Требуется анализ в оптическом микроскопе (серная кислота)"));
            return;
        }

        tooltip.add(Component.literal("§6=== [Результаты геохимического анализа] ==="));

        // Месторождение / Одиночный карман
        if (isField(stack)) {
            String fName = getFieldName(stack);
            int total = getTotalPockets(stack);
            int idx = getPocketIndex(stack);
            tooltip.add(Component.literal("§a★ Месторождение: §f\"" + fName + "\""));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§a  Является частью нефтяного месторождения состоящего из %d карманов поблизости", total)));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§2  [Карман %d из %d]", idx, total)));
        } else {
            tooltip.add(Component.literal("§7Залежь: §fОдиночный нефтяной карман"));
        }

        long maxMb = getVeinMaxMb(stack);
        if (maxMb > 0L) {
            long remainingMb = Math.max(0L, getVeinRemainingMb(stack));
            long maxBuckets = maxMb / 1000L;
            if (remainingMb <= 0L) {
                tooltip.add(Component.literal(String.format(Locale.ROOT,
                        "§7Запасы пласта: §cИстощён (0 / %s B)",
                        formatGroupedLong(maxBuckets))));
            } else if (remainingMb < maxMb) {
                long remBuckets = remainingMb / 1000L;
                double pct = (remainingMb * 100.0) / maxMb;
                tooltip.add(Component.literal(String.format(Locale.ROOT,
                        "§7Запасы пласта: §b%s / %s B §8(%.1f%% | %s mB)",
                        formatGroupedLong(remBuckets),
                        formatGroupedLong(maxBuckets),
                        pct,
                        formatGroupedLong(remainingMb))));
            } else {
                tooltip.add(Component.literal(String.format(Locale.ROOT,
                        "§7Запасы пласта: §b%s B §8(%s mB)",
                        formatGroupedLong(maxBuckets),
                        formatGroupedLong(maxMb))));
            }
        }

        Map<OilFraction, Integer> fractions = getFractions(stack);
        com.trd.api.industiral.fluids.system.FluidInfoHelper.appendOilGeochemistryTooltip(
                tooltip,
                getReservoirType(stack),
                !fractions.isEmpty(),
                f -> fractions.getOrDefault(f, 0),
                getGasRatio(stack),
                getPressure(stack),
                getSulfur(stack)
        );
    }
}
