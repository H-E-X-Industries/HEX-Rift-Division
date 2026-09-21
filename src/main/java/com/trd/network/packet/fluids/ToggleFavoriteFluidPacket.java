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

public record ToggleFavoriteFluidPacket(String fluidId) implements CustomPacketPayload {
    public static final Type<ToggleFavoriteFluidPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "toggle_favorite_fluid"));

    public static final StreamCodec<FriendlyByteBuf, ToggleFavoriteFluidPacket> STREAM_CODEC = StreamCodec.ofMember(
        ToggleFavoriteFluidPacket::write, ToggleFavoriteFluidPacket::new
    );

    public ToggleFavoriteFluidPacket(FriendlyByteBuf buffer) {
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
                    ListTag favorites = tag.getList("Favorites", Tag.TAG_STRING);
                    boolean found = false;

                    for (int i = 0; i < favorites.size(); i++) {
                        if (favorites.getString(i).equals(fluidId)) {
                            favorites.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        favorites.add(StringTag.valueOf(fluidId));
                    }
                    tag.put("Favorites", favorites);
                    
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
            }
        });
    }
}
