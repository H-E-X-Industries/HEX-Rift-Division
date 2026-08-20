package com.trd.api.tooltip;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class ExplosionTooltipRegistry {
    private static final List<RegistryObject<? extends Block>> BLOCKS = new ArrayList<>();

    /** Добавь сюда RegistryObject своего блока — и у него появится золотая строка в тултипе */
    public static void register(RegistryObject<? extends Block> block) {
        BLOCKS.add(block);
    }

    public static boolean contains(Block block) {
        return BLOCKS.stream().anyMatch(ro -> ro.get() == block);
    }
}