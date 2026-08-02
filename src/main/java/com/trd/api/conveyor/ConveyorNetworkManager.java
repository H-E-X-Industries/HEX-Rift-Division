package com.trd.api.conveyor;

import com.trd.block.basic.industrial.ConveyorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConveyorNetworkManager extends SavedData {
    private static final String DATA_NAME = "trd_conveyor_networks";

    private final Map<BlockPos, ConveyorNetwork> blockToNetwork = new HashMap<>();
    private final Set<ConveyorNetwork> networks = new HashSet<>();
    private final ServerLevel level;

    public ConveyorNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static ConveyorNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                tag -> load(level, tag),
                () -> new ConveyorNetworkManager(level),
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (ConveyorNetwork net : networks) {
            list.add(net.serializeNBT());
        }
        nbt.put("Networks", list);
        return nbt;
    }

    public static ConveyorNetworkManager load(ServerLevel level, CompoundTag nbt) {
        ConveyorNetworkManager manager = new ConveyorNetworkManager(level);
        ListTag list = nbt.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ConveyorNetwork net = ConveyorNetwork.deserializeNBT(list.getCompound(i));
            manager.networks.add(net);
            for (BlockPos pos : net.getPath()) {
                manager.blockToNetwork.put(pos, net);
            }
        }
        return manager;
    }

    public void tickAll() {
        boolean changed = false;
        for (ConveyorNetwork net : networks) {
            if (net.tick(level, this)) {
                changed = true;
                
                // Синхронизация с клиентами (с каждым тиком, если есть изменения)
                // Оптимальнее всего отправлять пакет всем игрокам в измерении или трекающим чанк,
                // но для простоты шлем всем в мире. (В идеале: PacketDistributor.TRACKING_CHUNK)
                com.trd.network.ModPacketHandler.INSTANCE.send(
                    PacketDistributor.DIMENSION.with(() -> level.dimension()),
                    new com.trd.network.packet.conveyor.SyncConveyorNetworkPacket(net.getId(), net.getItems(), net.getPath())
                );
            }
        }
        if (changed) {
            this.setDirty();
        }
    }

    public ConveyorNetwork getNetworkFor(BlockPos pos) {
        return blockToNetwork.get(pos);
    }

    public void addBlock(BlockPos pos, Direction facing) {
        BlockPos posBehind = pos.relative(facing.getOpposite());
        BlockPos posAhead = pos.relative(facing);

        ConveyorNetwork netBehind = null;
        if (isSameDirectionConveyor(posBehind, facing)) {
            netBehind = blockToNetwork.get(posBehind);
        }

        ConveyorNetwork netAhead = null;
        if (isSameDirectionConveyor(posAhead, facing)) {
            netAhead = blockToNetwork.get(posAhead);
        }

        if (netBehind != null && netAhead != null && netBehind != netAhead) {
            // Merge
            for (BlockPos p : netAhead.getPath()) {
                netBehind.addBlockToEnd(p);
                blockToNetwork.put(p, netBehind);
            }
            double offset = netBehind.getPath().size() - netAhead.getPath().size();
            for (ConveyorItem item : netAhead.getItems()) {
                item.setProgress(item.getProgress() + offset);
                netBehind.getItems().add(item);
            }
            netBehind.addBlockToEnd(pos); // Wait, pos goes in the middle!
            
            // Actually, simpler to just dissolve and rebuild to keep path ordered perfectly
            networks.remove(netBehind);
            networks.remove(netAhead);
            for (BlockPos p : netBehind.getPath()) blockToNetwork.remove(p);
            for (BlockPos p : netAhead.getPath()) blockToNetwork.remove(p);
            rebuildNetworkFrom(pos, facing);
        } else if (netBehind != null) {
            // Append to end
            netBehind.addBlockToEnd(pos);
            blockToNetwork.put(pos, netBehind);
        } else if (netAhead != null) {
            // Prepend to start
            netAhead.addBlockToStart(pos);
            blockToNetwork.put(pos, netAhead);
        } else {
            // New network
            ConveyorNetwork net = new ConveyorNetwork();
            net.addBlockToEnd(pos);
            networks.add(net);
            blockToNetwork.put(pos, net);
        }
        this.setDirty();
    }

    public void removeBlock(BlockPos pos) {
        ConveyorNetwork net = blockToNetwork.remove(pos);
        if (net == null) return;

        networks.remove(net);
        for (BlockPos p : net.getPath()) {
            if (!p.equals(pos)) {
                blockToNetwork.remove(p);
            }
        }

        // Drops items on the broken block
        double index = net.getPath().indexOf(pos);
        for (ConveyorItem item : net.getItems()) {
            if (item.getProgress() >= index && item.getProgress() < index + 1) {
                // Eject item in world
                net.ejectItemAt(level, pos, item.getStack());
            }
        }

        // Rebuild fragments
        for (BlockPos p : net.getPath()) {
            if (!p.equals(pos) && !blockToNetwork.containsKey(p)) {
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof ConveyorBlock) {
                    rebuildNetworkFrom(p, state.getValue(ConveyorBlock.FACING));
                }
            }
        }
        this.setDirty();
    }

    private void rebuildNetworkFrom(BlockPos start, Direction facing) {
        ConveyorNetwork net = new ConveyorNetwork();
        
        // Find absolute start by traversing backward
        BlockPos current = start;
        while (isSameDirectionConveyor(current.relative(facing.getOpposite()), facing)) {
            current = current.relative(facing.getOpposite());
        }

        // Traverse forward to build path
        while (isSameDirectionConveyor(current, facing)) {
            net.addBlockToEnd(current);
            blockToNetwork.put(current, net);
            current = current.relative(facing);
        }

        networks.add(net);
        this.setDirty();
    }

    private boolean isSameDirectionConveyor(BlockPos pos, Direction expectedFacing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ConveyorBlock && state.getValue(ConveyorBlock.FACING) == expectedFacing;
    }
}
