package com.trd.client.gltf.item.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;

/**
 * Выбор модели ударной гранаты по её виду.
 *
 * <p>Сама механика — загрузка модели, снятие повтора смещения корня, спрятанная
 * чека — живёт в {@link GrenadeGltfModels} и общая со всеми остальными гранатами:
 * геометрия у них одна и та же, а различаются они только тем, какую текстуру
 * зашили в свой файл. Здесь только соответствие «вид ударной гранаты → файл
 * модели»: {@link GrenadeIfType} отвечает на обратный вопрос («вид по снаряду»),
 * а предмет своё перечис не знает.
 */
public final class GrenadeIfVariants {

    /** Модель на каждый вид. Геометрия общая, различается только зашитая текстура. */
    private static final Map<GrenadeIfType, ResourceLocation> MODELS = new EnumMap<>(GrenadeIfType.class);

    static {
        for (GrenadeIfType type : GrenadeIfType.values()) {
            MODELS.put(type, GrenadeGltfModels.modelId(textureName(type)));
        }
    }

    private GrenadeIfVariants() {
    }

    /**
     * Имя вида без префикса {@code grenade_if_}.
     * <p>
     * Оно же имя текстуры и имя файла модели, поэтому расходиться им негде:
     * текстуры приходят из этого же блока в Blockbench, и всё, что нужно поменять
     * при появлении своей — одну строку в этом методе.
     */
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
    public static GemRenderGltfModel model(@Nullable GrenadeIfType type) {
        return GrenadeGltfModels.model(modelIdOf(type));
    }

    /**
     * Клип из модели, с которого снято смещение корня.
     *
     * @param type вид гранаты
     * @param clip имя клипа, как в glTF
     */
    @Nullable
    public static GltfAnimation poseClip(@Nullable GrenadeIfType type, String clip) {
        return GrenadeGltfModels.poseClip(modelIdOf(type), clip);
    }

    /** Поза покоя модели с невидимой чекой — то, чем рисуется летящая граната. */
    @Nullable
    public static GltfAnimation hiddenPin(@Nullable GrenadeIfType type) {
        return GrenadeGltfModels.hiddenPin(modelIdOf(type),
                com.trd.item.weapons.grenades.GrenadeIfAnimation.PIN_PULL);
    }

    /** Секунда последнего кадра клипа. */
    public static float endSeconds(@Nullable GltfAnimation clip) {
        return GrenadeGltfModels.endSeconds(clip);
    }

    /** Тип снаряда по сущности — на клиенте тип сущности известен наверняка. */
    public static GrenadeIfType typeOf(Entity entity) {
        return GrenadeIfType.typeOf(entity.getType());
    }
}
