package com.trd.client.gltf.item.tools;

import com.hex.hexrender.client.GltfItemRenderer;

/**
 * Рендерер литой кирки на glTF через HexRender.
 * <p>
 * Обе кирки (железная и стальная) рисуются одной моделью и различаются только текстурой,
 * которая подменяется на лету через {@link CastPickaxeGltfAppearance#textureOverrides}.
 */
public class CastPickaxeRenderer extends GltfItemRenderer {

    public CastPickaxeRenderer() {
        super(CastPickaxeGltfAppearance.create());
    }
}
