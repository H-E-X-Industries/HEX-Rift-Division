package com.trd.network.packet.fluids;

import com.trd.item.industrial.fluids.FluidIdentifierItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.trd.main.MainRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;

public record ClearFluidHistoryPacket() implements CustomPacketPayload {
    public static final Type<ClearFluidHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "clear_fluid_history"));

    public static final StreamCodec<FriendlyByteBuf, ClearFluidHistoryPacket> STREAM_CODEC = StreamCodec.ofMember(
        ClearFluidHistoryPacket::write, ClearFluidHistoryPacket::new
    );

    public ClearFluidHistoryPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof FluidIdentifierItem)) {
                    stack = player.getOffhandItem();
                }
                if (stack.getItem() instanceof FluidIdentifierItem) {
                    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    tag.remove("RecentFluids");
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
            }
        });
    }
}
