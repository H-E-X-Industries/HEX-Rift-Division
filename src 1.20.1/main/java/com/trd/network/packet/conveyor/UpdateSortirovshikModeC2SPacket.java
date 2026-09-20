package com.trd.network.packet.conveyor;

import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Переключение режима секции сортировщика (кнопка в GUI). */
public class UpdateSortirovshikModeC2SPacket {
    private final BlockPos pos;
    private final int section;

    public UpdateSortirovshikModeC2SPacket(BlockPos pos, int section) {
        this.pos = pos;
        this.section = section;
    }

    public UpdateSortirovshikModeC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.section = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(section);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (player.level().getBlockEntity(pos) instanceof SortirovshikBlockEntity sorter
                    && section >= 0 && section < SortirovshikBlockEntity.SECTIONS
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                sorter.cycleMode(section);
            }
        });
        return true;
    }
}
