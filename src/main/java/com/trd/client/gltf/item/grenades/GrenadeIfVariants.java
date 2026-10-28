package com.trd.client.gltf.item.grenades;

import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.core.ModelRegistry;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Выбор модели ударной гранаты по её виду.
 */
public final class GrenadeIfVariants {

    /** Модель на каждый вид. Геометрия общая, различается только зашитая текстура. */
    private static final Map<GrenadeIfType, ResourceLocation> MODELS = new EnumMap<>(GrenadeIfType.class);

    public static ResourceLocation modelId(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/" + name + ".gltf");
    }

    static {
        for (GrenadeIfType type : GrenadeIfType.values()) {
            MODELS.put(type, modelId(textureName(type)));
        }
    }

    private GrenadeIfVariants() {
    }

    private static String textureName(GrenadeIfType type) {
        return switch (type) {
            case GRENADE_IF -> "grenade_if";
            case GRENADE_IF_HE -> "grenade_if_he";
            case GRENADE_IF_SLIME -> "grenade_if_slime";
            case GRENADE_IF_FIRE -> "grenade_if_fire";
        };
    }

    /** Файл модели вида, либо {@code null}-тип — на ударной гранате. */
    public static ResourceLocation modelIdOf(@Nullable GrenadeIfType type) {
        return MODELS.get(type != null ? type : GrenadeIfType.GRENADE_IF);
    }

    /** Модель вида, либо {@code null}, пока она грузится. */
    @Nullable
    public static GltfModelAsset model(@Nullable GrenadeIfType type) {
        return ModelRegistry.fetch(modelIdOf(type));
    }

    /** Тип снаряда по сущности — на клиенте тип сущности известен наверняка. */
    public static GrenadeIfType typeOf(Entity entity) {
        return GrenadeIfType.typeOf(entity.getType());
    }
}
