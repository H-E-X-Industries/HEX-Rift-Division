package com.trd.network.packet.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент сообщает серверу, что игрок нажал огонь, и вместе с намерением
 * присылает свой угол обзора по двум осям — {@code yaw} и {@code pitch}.
 * <p>
 * Направление выстрела считает сервер, а его копия поворота игрока приходит
 * отдельным ванильным пакетом движения. Между клиентским кадром, в котором
 * игрок нажал огонь, и обработкой пакета на сервере проходит до целого тика,
 * поэтому пуля уходила не туда, куда показывал прицел. Угол из пакета — это
 * ровно то, что было видно в момент выстрела, поэтому он применяется до
 * расчёта направления.
 */
public record PacketShoot(float yaw, float pitch) implements CustomPacketPayload {

    public static final Type<PacketShoot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "shoot"));

    public static final StreamCodec<FriendlyByteBuf, PacketShoot> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, PacketShoot::yaw,
                    ByteBufCodecs.FLOAT, PacketShoot::pitch,
                    PacketShoot::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof MachineGunItem gun) {
                    // Мусорные значения от modified-клиента игнорируем и стреляем
                    // по серверному углу обзора, как в 1.20.1.
                    float shotYaw = Float.isFinite(yaw) ? yaw : player.getYRot();
                    float shotPitch = Float.isFinite(pitch) ? Mth.clamp(pitch, -90.0F, 90.0F) : player.getXRot();

                    gun.performShooting(player.serverLevel(), player, stack, shotYaw, shotPitch);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
