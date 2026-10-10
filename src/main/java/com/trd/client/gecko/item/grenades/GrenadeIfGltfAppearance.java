package com.trd.client.gecko.item.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.item.weapons.grenades.GrenadeIfItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Облик ударной гранаты в руке на glTF вместо геколибовской geo-модели.
 *
 * <p>Вся начинка — в {@link GrenadeGltfAppearance}: какие клипы показывать и почему
 * у предмета не видно чеки. Здесь только выбор модели по виду ударной гранаты —
 * у четырёх видов она своя, с текстурой, зашитой в файл.
 */
public class GrenadeIfGltfAppearance extends GrenadeGltfAppearance {

    public GrenadeIfGltfAppearance() {
        super(GrenadeIfGltfAppearance::modelIdOf);
    }

    /** Модель ударной гранаты по предмету. */
    private static ResourceLocation modelIdOf(Item item) {
        GrenadeIfType type = item instanceof GrenadeIfItem grenade
                ? grenade.getGrenadeType()
                : GrenadeIfType.GRENADE_IF;
        return GrenadeIfVariants.modelIdOf(type);
    }
}