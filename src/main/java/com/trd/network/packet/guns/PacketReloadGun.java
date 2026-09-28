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
 * Клиент сообщает серверу, что игрок нажал клавишу перезарядки.
 */
public record PacketReloadGun() implements CustomPacketPayload {

    public static final Type<PacketReloadGun> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "reload_gun"));

    public static final StreamCodec<FriendlyByteBuf, PacketReloadGun> STREAM_CODEC =
            StreamCodec.ofMember(PacketReloadGun::write, PacketReloadGun::new);

    public PacketReloadGun(FriendlyByteBuf buffer) {
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
                    gun.reloadGun(player, stack);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
