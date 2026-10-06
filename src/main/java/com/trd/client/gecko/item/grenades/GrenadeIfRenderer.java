package com.trd.client.gecko.item.grenades;

import com.wf.gemrender.direct.GemRenderItemRenderer;

/**
 * Рендерер ударной гранаты в руке на glTF через GemRender.
 * <p>
 * Вся начинка — в {@link GrenadeIfGltfAppearance}: какие клипы показывать, какая
 * текстура у текущего вида и почему у предмета не видно чеки. Здесь нужен только
 * сам рендерер, который NeoForge и достаёт из {@code getCustomRenderer}.
 */
public class GrenadeIfRenderer extends GemRenderItemRenderer {

    public GrenadeIfRenderer() {
        super(new GrenadeIfGltfAppearance());
    }
}