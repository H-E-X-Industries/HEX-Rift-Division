package com.trd.client.gltf.item.grenades;

import com.hex.hexrender.direct.HexItemRenderer;

/**
 * Рендерер обычной гранаты в руке на glTF через HexRender.
 * <p>
 * Вся начинка — в {@link ClassicGrenadeGltfAppearance} и общем
 * {@link GrenadeGltfAppearance}: какие клипы показывать и какая модель у текущего
 * вида. Здесь нужен только сам рендерер, который NeoForge и достаёт из
 * {@code getCustomRenderer}.
 */
public class ClassicGrenadeRenderer extends HexItemRenderer {

    public ClassicGrenadeRenderer() {
        super(new ClassicGrenadeGltfAppearance());
    }
}
