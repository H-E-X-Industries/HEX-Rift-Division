package com.trd.network.packet.guns;

import com.trd.main.MainRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: сервер сообщает клиенту, какую анимацию пушки надо проиграть.
 * <p>
 * Раньше это делал геколибовский {@code GeoItem.triggerAnim} — но он работал
 * только пока анимацию тикал {@code GeoItemRenderer}. С переходом на glTF
 * рендерером стал GemRender, а он не тикает контроллеры геколиба, поэтому
 * синхронизация анимаций переехала в отдельный пакет.
 * <p>
 * Пакет — это ещё и подтверждение выстрела: его сервер шлёт только когда пуля
 * действительно появилась. Вспышка ставится по нему, а не по нажатию, иначе
 * при пустом магазине или в воде вспышка была бы без пули, а при очереди
 * вспышки копились бы и висели после последнего выстрела.
 */
public record PacketMachineGunAnim(String anim) implements CustomPacketPayload {

    public static final Type<PacketMachineGunAnim> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "machinegun_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketMachineGunAnim> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PacketMachineGunAnim::anim,
                    PacketMachineGunAnim::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            boolean started = com.trd.item.weapons.guns.MachineGunClientAnim.trigger(anim);

            // Выстрел мог и не начаться: клиент держит ровно один клип, и если
            // игрок лупит чаще, чем длится анимация, запрос отбрасывается.
            // Вспышка ставится только когда клип действительно пошёл — иначе
            // она появлялась бы чаще, чем летят пули.
            if (started && com.trd.item.weapons.guns.MachineGunClientAnim.SHOT.equals(anim)) {
                com.trd.item.weapons.guns.MachineGunClientAnim.spawnShotFlash();
            }
        });
    }
}
