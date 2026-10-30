package com.trd.client.gltf.item.grenades;

import com.hex.hexrender.client.GltfItemRenderer;

/**
 * Рендерер ударной гранаты в руке на glTF через HexRender.
 * <p>
 * Вся начинка — в {@link GrenadeIfGltfAppearance}: какие клипы показывать, какая
 * текстура у текущего вида и почему у предмета не видно чеки. Здесь нужен только
 * сам рендерер, который NeoForge и достаёт из {@code getCustomRenderer}.
 */
public class GrenadeIfRenderer extends GltfItemRenderer {

    public GrenadeIfRenderer() {
        super(new GrenadeIfGltfAppearance());
    }
}
