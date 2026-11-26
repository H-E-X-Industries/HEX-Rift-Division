package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.client.GltfBlockEntityRenderer;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Рендерер буровой крестовины (HEX-Render GPU Skinning через GltfBlockEntityRenderer).
 * Голограмма Этапа 1 рендерится отдельно в {@link com.trd.client.render.OilRigHologramRenderer} на стадии AFTER_TRANSLUCENT_BLOCKS.
 */
public class MudCrossRenderer extends GltfBlockEntityRenderer<MudCrossBlockEntity> {

    public MudCrossRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new MudCrossModelDescriptor());
    }
}
