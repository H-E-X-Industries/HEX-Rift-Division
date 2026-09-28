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
 * Клиент сообщает серверу, что игрок нажал огонь. Решение о выстреле принимает сервер.
 */
public record PacketShoot() implements CustomPacketPayload {

    public static final Type<PacketShoot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "shoot"));

    public static final StreamCodec<FriendlyByteBuf, PacketShoot> STREAM_CODEC =
            StreamCodec.ofMember(PacketShoot::write, PacketShoot::new);

    public PacketShoot(FriendlyByteBuf buffer) {
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
                    gun.performShooting(player.serverLevel(), player, stack);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
