package com.trd.client.gltf.item.drilling;

import com.hex.hexrender.client.GltfItemRenderer;

/**
 * Рендерер предмета буровой крестовины (Mud Cross) на glTF через HexRender.
 */
public class MudCrossItemRenderer extends GltfItemRenderer {

    public MudCrossItemRenderer() {
        super(new MudCrossItemAppearance());
    }
}
