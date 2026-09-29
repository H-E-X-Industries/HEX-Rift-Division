package com.trd.network.packet.turrets;

import com.trd.client.overlay.gui.GUITurretAmmo;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: ответ на попытку отредактировать чип (добавить/снять владельца).
 * GUI показывает подтверждение или отказ.
 */
public record PacketChipFeedback(boolean success) implements CustomPacketPayload {

    public static final Type<PacketChipFeedback> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "chip_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketChipFeedback> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PacketChipFeedback::success,
                    PacketChipFeedback::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof GUITurretAmmo gui) {
                gui.handleFeedback(success);
            }
        });
    }
}
