package com.trd.client.gltf.item.drilling;

import com.trd.block.basic.ModBlocks;
import com.trd.main.MainRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Регистрация BEWLR-рендерера для предмета барабана буровых труб (PipeDrum).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class PipeDrumClientItem {

    private PipeDrumClientItem() {}

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private PipeDrumItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new PipeDrumItemRenderer();
                }
                return renderer;
            }
        }, ModBlocks.PIPE_DRUM.asItem());
    }
}
