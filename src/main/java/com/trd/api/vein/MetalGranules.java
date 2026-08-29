package com.trd.api.vein;

import com.trd.main.ResourceRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * Малая единица («самородок»/гранула) металла — выход выщелачивателя.
 * 1 OU = 1 самородок = 1 металл-юнит (см. MetalUnits2).
 *
 * <p>Базовые металлы берутся из ванильного Minecraft, модные — из
 * {@link ResourceRegistry} (там «кусочек» металла регистрируется как
 * {@code <name>_nugget}).
 */
public final class MetalGranules {

    private MetalGranules() {
    }

    /** Самородок металла по его id. {@code null}, если металл неизвестен. */
    @Nullable
    public static Item forMetal(String metalId) {
        return switch (metalId) {
            case "iron" -> Items.IRON_NUGGET;
            case "gold" -> Items.GOLD_NUGGET;
            // Ванильной «медной крошки» нет — используем малую единицу модной меди
            case "copper" -> ResourceRegistry.getSmallUnit("industrial_copper");
            default -> ResourceRegistry.getSmallUnit(metalId);
        };
    }
}