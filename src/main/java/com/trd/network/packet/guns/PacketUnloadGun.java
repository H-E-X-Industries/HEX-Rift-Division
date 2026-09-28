package com.trd.network.packet.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент сообщает серверу, что игрок нажал клавишу разрядки.
 */
public record PacketUnloadGun() implements CustomPacketPayload {

    public static final Type<PacketUnloadGun> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "unload_gun"));

    public static final StreamCodec<FriendlyByteBuf, PacketUnloadGun> STREAM_CODEC =
            StreamCodec.ofMember(PacketUnloadGun::write, PacketUnloadGun::new);

    public PacketUnloadGun(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof MachineGunItem gun) {
                    gun.unloadGun(player, stack);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
