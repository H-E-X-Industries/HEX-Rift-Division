package com.trd.api.tooltip;

import com.trd.main.MainRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class ExplosionTooltipRegistry {
    private static final List<RegistryObject<? extends Block>> BLOCKS = new ArrayList<>();

    /**
     * Порог автодобавления: блок с такой взрывоустойчивостью показывает строку в тултипе
     * без ручной регистрации в {@link #register}. Действует только на блоки нашего мода —
     * ванильные обсидиан/незерит/бедрок в список не попадают.
     */
    public static final float AUTO_RESISTANCE = 50.0f;

    /** Добавь сюда RegistryObject своего блока — и у него появится золотая строка в тултипе */
    public static void register(RegistryObject<? extends Block> block) {
        BLOCKS.add(block);
    }

    public static boolean contains(Block block) {
        // Ручной список нужен для наших блоков с устойчивостью ниже порога, все остальные
        // блоки мода подхватываются автоматически по самой устойчивости.
        if (isAutoResistant(block)) return true;
        return BLOCKS.stream().anyMatch(ro -> ro.get() == block);
    }

    /** Автодобавление: устойчивость >= порога и блок зарегистрирован в нашем неймспейсе. */
    private static boolean isAutoResistant(Block block) {
        if (block.getExplosionResistance() < AUTO_RESISTANCE) return false;
        ResourceLocation name = BuiltInRegistries.BLOCK.getKey(block);
        return name != null && MainRegistry.MOD_ID.equals(name.getNamespace());
    }
}