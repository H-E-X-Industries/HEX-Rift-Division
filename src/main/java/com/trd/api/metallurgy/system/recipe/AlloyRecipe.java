package com.trd.api.metallurgy.system.recipe;

import com.trd.api.metallurgy.system.Metal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class AlloyRecipe {
    private final AlloySlot[] slots;
    private final Metal outputMetal;
    private final int outputUnits;
    private final float heatConsumptionPerTick;
    private final int smeltTimeTicks;

    public AlloyRecipe(AlloySlot[] slots, Metal outputMetal, int outputUnits,
                       float heatConsumptionPerTick, int smeltTimeTicks) {
        this.slots = slots.clone();
        this.outputMetal = outputMetal;
        this.outputUnits = outputUnits;
        this.heatConsumptionPerTick = heatConsumptionPerTick;
        this.smeltTimeTicks = smeltTimeTicks;
    }

    /**
     * Агрегатный матчинг: привязка ингредиентов к конкретным индексам слотов не важна.
     * Считается суммарное количество каждого требуемого предмета по всем слотам;
     * посторонние предметы в слотах не допускаются.
     */
    public boolean matches(ItemStack[] stacks) {
        if (stacks.length != 4) return false;

        Map<Item, Integer> required = getItemRequirements();
        if (required.isEmpty()) {
            // Рецепт без ингредиентов — все слоты должны быть пусты
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }

        Map<Item, Integer> available = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!required.containsKey(stack.getItem())) return false;
            available.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            Integer avail = available.get(entry.getKey());
            if (avail == null || avail < entry.getValue()) return false;
        }
        return true;
    }

    /** Суммарные требования рецепта по каждому предмету (позиция слота не важна). */
    public Map<Item, Integer> getItemRequirements() {
        Map<Item, Integer> required = new LinkedHashMap<>();
        for (AlloySlot req : slots) {
            if (req.item() == null || req.count() == 0) continue;
            for (Item it : req.items()) {
                required.merge(it, req.count(), Integer::sum);
            }
        }
        return required;
    }

    public AlloySlot[] getSlots() { return slots.clone(); }
    public Metal getOutputMetal() { return outputMetal; }
    public int getOutputUnits() { return outputUnits; }
    public float getHeatConsumptionPerTick() { return heatConsumptionPerTick; }
    public int getSmeltTimeTicks() { return smeltTimeTicks; }

    public float getTotalHeatConsumption() {
        return heatConsumptionPerTick * smeltTimeTicks;
    }

    public float heatConsumption() { return heatConsumptionPerTick; }
    public int smeltTime() { return smeltTimeTicks; }
    public int outputUnits() { return outputUnits; }
    public Metal output() { return outputMetal; }
}
