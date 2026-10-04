package com.trd.network.packet.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент сообщает серверу, открыт ли сейчас прицел.
 * <p>
 * Замедление скорости нужно применять на сервере, а не только на клиенте: у
 * клиентского игрока атрибуты перезаписываются пакетом
 * {@code ClientboundUpdateAttributesPacket}, где ванильный обработчик делает
 * {@code removeModifiers()} и добавляет модификаторы заново. Свой временный
 * модификатор там стирался, и замедление то появлялось, то пропадало. На
 * сервере такой пакет тоже уходит игроку обратно, но уже с нашим модификатором
 * в составе — поэтому состояние синхронизируется в обе стороны само.
 */
public record PacketScopeState(boolean scoped) implements CustomPacketPayload {

    public static final Type<PacketScopeState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "scope_state"));

    public static final StreamCodec<FriendlyByteBuf, PacketScopeState> STREAM_CODEC =
            StreamCodec.ofMember(PacketScopeState::write, PacketScopeState::new);

    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "scope_slowdown");

    /** Доля скорости в прицеле. */
    private static final double MOVE_MULTIPLIER = 0.5D;

    public PacketScopeState(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.scoped);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Без @Override: в 1.21.1 у CustomPacketPayload есть только type(),
    // handle() — соглашение проекта, а не метод интерфейса.
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Пакет приходит от любого клиента, поэтому проверяем, что
                // пушка действительно в руке: иначе игрок мог бы держать
                // замедление, не имея никакого оружия.
                if (player.getMainHandItem().getItem() instanceof MachineGunItem) {
                    apply(player, this.scoped);
                } else {
                    apply(player, false);
                }
            }
        });
    }

    private static void apply(ServerPlayer player, boolean active) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        AttributeModifier existing = attribute.getModifier(MOVEMENT_MODIFIER_ID);

        if (!active) {
            if (existing != null) {
                attribute.removeModifier(existing);
            }
            return;
        }

        if (existing != null) return;

        // Сумма равна множитель - 1: у ADD_MULTIPLIED_BASE база уже умножается
        // на amount, поэтому домножать ещё и на базу нельзя.
        attribute.addTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                MOVE_MULTIPLIER - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
