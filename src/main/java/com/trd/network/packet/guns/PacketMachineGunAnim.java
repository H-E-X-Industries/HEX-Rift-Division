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
        ctx.enqueueWork(() -> com.trd.item.weapons.guns.MachineGunClientAnim.trigger(anim));
    }
}