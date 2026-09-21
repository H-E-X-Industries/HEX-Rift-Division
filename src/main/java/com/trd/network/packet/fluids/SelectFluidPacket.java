package com.trd.network.packet.fluids;

import com.trd.item.industrial.fluids.FluidIdentifierItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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

public record SelectFluidPacket(String fluidId) implements CustomPacketPayload {
    public static final Type<SelectFluidPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "select_fluid"));

    public static final StreamCodec<FriendlyByteBuf, SelectFluidPacket> STREAM_CODEC = StreamCodec.ofMember(
        SelectFluidPacket::write, SelectFluidPacket::new
    );

    public SelectFluidPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(fluidId);
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
                    tag.putString("SelectedFluid", fluidId);

                    if (!fluidId.equals("none")) {
                        ListTag recents = tag.getList("RecentFluids", Tag.TAG_STRING);
                        for (int i = 0; i < recents.size(); i++) {
                            if (recents.getString(i).equals(fluidId)) {
                                recents.remove(i);
                                break;
                            }
                        }
                        recents.add(0, StringTag.valueOf(fluidId));
                        while (recents.size() > 10) {
                            recents.remove(recents.size() - 1);
                        }
                        tag.put("RecentFluids", recents);
                    }
                    
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
            }
        });
    }
}
