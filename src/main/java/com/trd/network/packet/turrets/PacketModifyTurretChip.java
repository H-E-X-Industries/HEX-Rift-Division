package com.trd.network.packet.turrets;

import com.trd.main.MainRegistry;
import com.trd.menu.turrets.TurretLightMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.trd.item.weapons.turrets.TurretChipItem;

import java.util.Optional;
import java.util.UUID;

/**
 * C2S: правка чипа из GUI — снять владельца по индексу (0) или добавить по нику (1).
 * В 1.21.1 данные чипа лежат в компоненте {@code CUSTOM_DATA}, а не в NBT стака.
 */
public record PacketModifyTurretChip(int action, String payload) implements CustomPacketPayload {

    public static final Type<PacketModifyTurretChip> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "modify_turret_chip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketModifyTurretChip> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PacketModifyTurretChip::action,
                    ByteBufCodecs.STRING_UTF8, PacketModifyTurretChip::payload,
                    PacketModifyTurretChip::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack stack = ItemStack.EMPTY;
            if (player.containerMenu instanceof TurretLightMenu menu) {
                stack = menu.getAmmoContainer().getStackInSlot(9);
            } else if (player.containerMenu instanceof com.trd.menu.turrets.TromboneMenu menu) {
                stack = menu.getMissileContainer().getStackInSlot(
                        com.trd.block.entity.weapons.MissileAmmoContainer.CHIP_SLOT_INDEX);
            }

            if (!(stack.getItem() instanceof TurretChipItem)) return;

            CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag list = nbt.getList("TurretOwners", Tag.TAG_STRING);

            if (action == 0) {
                try {
                    int index = Integer.parseInt(payload);
                    if (index >= 0 && index < list.size()) {
                        list.remove(index);
                        nbt.put("TurretOwners", list);
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    }
                } catch (Exception ignored) {
                }
            } else if (action == 1) {
                String targetName = payload;
                if (player.getServer() == null) {
                    sendFeedback(player, false);
                    return;
                }
                Optional<com.mojang.authlib.GameProfile> profile =
                        player.getServer().getProfileCache().get(targetName);

                if (profile.isPresent()) {
                    UUID id = profile.get().getId();
                    String name = profile.get().getName();
                    String entry = id + "|" + name;

                    boolean exists = false;
                    for (Tag t : list) {
                        if (t.getAsString().equals(entry)) exists = true;
                    }

                    if (!exists && list.size() < 5) {
                        list.add(StringTag.valueOf(entry));
                        nbt.put("TurretOwners", list);
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    }
                    sendFeedback(player, true);
                } else {
                    sendFeedback(player, false);
                }
            }
        });
    }

    private void sendFeedback(ServerPlayer player, boolean success) {
        PacketDistributor.sendToPlayer(player, new PacketChipFeedback(success));
    }
}
