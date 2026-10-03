package com.trd.network.packet.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

/**
 * Клиент сообщает серверу, что игрок нажал огонь.
 *
 * <p><b>Угол.</b> Присылается по двум осям — {@code yaw} и {@code pitch}.
 * Направление выстрела считает сервер, а его копия поворота игрока приходит
 * отдельным ванильным пакетом движения, и между клиентским кадром с нажатием и
 * обработкой пакета проходит до целого тика. Угол из пакета — это ровно то, что
 * было видно в момент выстрела, поэтому он применяется до расчёта направления.
 *
 * <p><b>Точка вылета.</b> Присылается кончиком ствола, посчитанным на клиенте
 * по локатору из модели: {@code hasMuzzle} и три координаты. Так пуля вылетает
 * именно из дула, а не из точки, угаданной формулой от позиции игрока, и то же
 * верно в прицеле, в третьем лице и на просадке кадров. Формула от игрока жила
 * отдельно от модели и разъезжалась с ней на всей длине ствола.
 *
 * <p>Точка влияет только на <em>положение</em> появления снаряда, но не на
 * траекторию: направление по-прежнему целиком считает сервер от присланного
 * угла. Позиция проверяется на разумность — клиент может прислать что угодно.
 */
public record PacketShoot(float yaw, float pitch, boolean scoped,
                           boolean hasMuzzle, double muzzleX, double muzzleY, double muzzleZ)
        implements CustomPacketPayload {

    /** Насколько далеко от игрока правдоподобна присланная точка вылета. */
    private static final double MUZZLE_MAX_DISTANCE = 8.0D;

    /** Границы мира плюс запас: за ними начинается оседлание координат. */
    private static final double ABS_COORD_LIMIT = 3.0E7D;

    public static final Type<PacketShoot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "shoot"));

    /**
     * Кодек собран вручную, а не через {@code StreamCodec.composite}: у того
     * перегрузки только на шесть пар, а полей здесь семь. Лишние три координаты
     * ради экономии пары не группируются в {@link Vec3} — с ними вместе с
     * флагом получается четыре пары, но ради этого пришлось бы менять форму
     * записи и все её фабрики.
     */
    public static final StreamCodec<FriendlyByteBuf, PacketShoot> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeFloat(packet.yaw);
                        buf.writeFloat(packet.pitch);
                        buf.writeBoolean(packet.scoped);
                        buf.writeBoolean(packet.hasMuzzle);
                        buf.writeDouble(packet.muzzleX);
                        buf.writeDouble(packet.muzzleY);
                        buf.writeDouble(packet.muzzleZ);
                    },
                    buf -> new PacketShoot(
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Пакет без точки вылета: сервер посчитает её своей формулой. */
    public static PacketShoot of(float yaw, float pitch, boolean scoped) {
        return new PacketShoot(yaw, pitch, scoped, false, 0.0D, 0.0D, 0.0D);
    }

    /** Пакет с точкой вылета из модели. */
    public static PacketShoot of(float yaw, float pitch, boolean scoped, Vec3 muzzle) {
        return new PacketShoot(yaw, pitch, scoped, true, muzzle.x, muzzle.y, muzzle.z);
    }

    @Nullable
    public Vec3 muzzle() {
        return hasMuzzle ? new Vec3(muzzleX, muzzleY, muzzleZ) : null;
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

                    gun.performShooting(player.serverLevel(), player, stack, shotYaw, shotPitch, scoped,
                            sanitize(player, muzzle()));
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
    }

    /**
     * Отсекает точку, которой сервер поверить не может: нечисловую, за пределами
     * мира или далёкую от игрока. Всё остальное отбрасывается в пользу
     * серверной формулы, чтобы подделанный пакет не телепортировал пулю.
     */
    @Nullable
    private static Vec3 sanitize(ServerPlayer player, @Nullable Vec3 muzzle) {
        if (muzzle == null) return null;
        if (!Double.isFinite(muzzle.x) || !Double.isFinite(muzzle.y) || !Double.isFinite(muzzle.z)) {
            return null;
        }
        if (Math.abs(muzzle.x) > ABS_COORD_LIMIT
                || Math.abs(muzzle.y) > ABS_COORD_LIMIT
                || Math.abs(muzzle.z) > ABS_COORD_LIMIT) {
            return null;
        }
        return muzzle.distanceToSqr(player.position()) > MUZZLE_MAX_DISTANCE * MUZZLE_MAX_DISTANCE
                ? null
                : muzzle;
    }
}
