package com.trd.network.packet.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент сообщает серверу, что игрок нажал огонь.
 *
 * <p><b>Угол.</b> Присылается по двум осям — {@code yaw} и {@code pitch}.
 * Направление выстрела считает сервер, а его копия поворота игрока приходит
 * отдельным ванильным пакетом движения, и между клиентским кадром с нажатием и
 * обработкой пакета проходит до целого тика. Угол из пакета — это ровно то, что
 * было видно в момент выстрела, поэтому он применяется до расчёта направления.
 *
 * <p><b>Точка вылета.</b> Не присылается: её целиком считает сервер по
 * {@link MachineGunItem#shotOrigin}, и раньше клиент слал кончик ствола из
 * модели, но он совпадал с нарисованным дулом только на глаз и то не всегда:
 * при первом взгляде он выходил из остатка предыдущего кадра, а смена оружия и
 * переход между первым и третьим лицом вовсе не анимируются. Единственное, за
 * что отвечает клиент, — сам факт выстрела.
 */
public record PacketShoot(float yaw, float pitch, boolean scoped) implements CustomPacketPayload {

    public static final Type<PacketShoot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "shoot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketShoot> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, PacketShoot::yaw,
                    ByteBufCodecs.FLOAT, PacketShoot::pitch,
                    ByteBufCodecs.BOOL, PacketShoot::scoped,
                    PacketShoot::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static PacketShoot of(float yaw, float pitch, boolean scoped) {
        return new PacketShoot(yaw, pitch, scoped);
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

                    gun.performShooting(player.serverLevel(), player, stack, shotYaw, shotPitch, scoped);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }
}
