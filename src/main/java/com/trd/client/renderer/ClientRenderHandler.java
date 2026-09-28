package com.trd.client.renderer;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Пока в руке пушка, ванильный прицел заменяется на точку.
 * В NeoForge 1.21 слои GUI настраиваются через {@code RegisterGuiLayersEvent},
 * который публикуется на mod-шине, и заменяются методом
 * {@link RegisterGuiLayersEvent#replaceLayer}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRenderHandler {

    private static final int DOT_COLOR = 0x80FFFFFF;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.CROSSHAIR, (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!(mc.player.getMainHandItem().getItem() instanceof MachineGunItem)) return;

            int x = mc.getWindow().getGuiScaledWidth() / 2;
            int y = mc.getWindow().getGuiScaledHeight() / 2;

            // Точка 1x1 в центре экрана, полупрозрачная
            graphics.fill(x, y, x + 1, y + 1, DOT_COLOR);
        });
    }
}
