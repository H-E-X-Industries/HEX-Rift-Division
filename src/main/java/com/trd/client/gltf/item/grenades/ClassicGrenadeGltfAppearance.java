package com.trd.client.gltf.item.grenades;

/**
 * Облик обычной гранаты в руке на glTF.
 *
 * <p>От {@link GrenadeIfGltfAppearance} отличается ровно выбором модели: у обычных
 * гранат вид берётся не из {@code GrenadeIfItem}, а по тому же предмету через
 * таблицу {@link ClassicGrenadeVariants}. Всё остальное — какие клипы показывать,
 * почему у предмета не видно чеки, куда сдвигать жест взятия — общее и лежит в
 * {@link GrenadeGltfAppearance}.
 */
public class ClassicGrenadeGltfAppearance extends GrenadeGltfAppearance {

    public ClassicGrenadeGltfAppearance() {
        super(ClassicGrenadeVariants::modelIdOf);
    }
}