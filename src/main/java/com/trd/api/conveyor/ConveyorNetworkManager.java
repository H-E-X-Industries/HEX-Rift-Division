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

import java.util.ArrayList;
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

    private final Set<ConveyorNetwork> networksToSync = new HashSet<>();

    public void markForSync(ConveyorNetwork net) {
        networksToSync.add(net);
    }

    public void syncNetwork(ConveyorNetwork net) {
        // ВАЖНО: отправка асинхронная (Netty-поток), а список предметов мутируется серверным тиком.
        // Сериализация живого списка приводила к битым/потерянным пакетам и "застревающим" предметам.
        java.util.List<com.trd.api.conveyor.ConveyorItem> itemsSnapshot = new ArrayList<>(net.getItems().size());
        for (com.trd.api.conveyor.ConveyorItem item : net.getItems()) {
            itemsSnapshot.add(new com.trd.api.conveyor.ConveyorItem(item.getStack().copy(), item.getProgress()));
        }
        java.util.List<BlockPos> pathSnapshot = new ArrayList<>(net.getPath());
        com.trd.network.ModPacketHandler.INSTANCE.send(
            PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new com.trd.network.packet.conveyor.SyncConveyorNetworkPacket(net.getId(), itemsSnapshot, pathSnapshot)
        );
    }

    public void tickAll() {
        boolean changed = false;
        // ВАЖНО: отметки могут быть поставлены сортировщиками ДО тика сетей (в том же серверном
        // тике). Нельзя их просто стирать — иначе удаление предмета сортировщиком так и не
        // доедет до клиентов, и предмет "застынет" на ленте перед сортировщиком.
        Set<ConveyorNetwork> toSend = new HashSet<>(networksToSync);
        networksToSync.clear();
        for (ConveyorNetwork net : networks) {
            if (net.tick(level, this)) {
                changed = true;
                toSend.add(net);
            }
        }
        for (ConveyorNetwork net : toSend) {
            syncNetwork(net);
        }
        if (changed || !toSend.isEmpty()) {
            this.setDirty();
        }
    }

    public ConveyorNetwork getNetworkFor(BlockPos pos) {
        return blockToNetwork.get(pos);
    }

    private BlockPos getNextPos(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof com.trd.block.basic.industrial.ConveyorElevatorBlock) {
            com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart part = state.getValue(com.trd.block.basic.industrial.ConveyorElevatorBlock.PART);
            Direction facing = state.getValue(ConveyorBlock.FACING);
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.BOTTOM) return pos.above();
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.MIDDLE) return pos.above();
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.TOP) return pos.relative(facing);
        }
        return pos.relative(state.getValue(ConveyorBlock.FACING));
    }

    private BlockPos getPrevPos(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof com.trd.block.basic.industrial.ConveyorElevatorBlock) {
            com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart part = state.getValue(com.trd.block.basic.industrial.ConveyorElevatorBlock.PART);
            Direction facing = state.getValue(ConveyorBlock.FACING);
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.BOTTOM) return pos.relative(facing.getOpposite());
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.MIDDLE) return pos.below();
            if (part == com.trd.block.basic.industrial.ConveyorElevatorBlock.ElevatorPart.TOP) return pos.below();
        }
        return pos.relative(state.getValue(ConveyorBlock.FACING).getOpposite());
    }

    public void addBlock(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ConveyorBlock.FACING);
        BlockPos posBehind = getPrevPos(pos, state);
        BlockPos posAhead = getNextPos(pos, state);

        ConveyorNetwork netBehind = null;
        if (isSameDirectionConveyor(posBehind, pos, false)) {
            netBehind = blockToNetwork.get(posBehind);
        }

        ConveyorNetwork netAhead = null;
        if (isSameDirectionConveyor(posAhead, pos, true)) {
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
            
            // Actually, simpler to just dissolve and rebuild to keep path ordered perfectly
            networks.remove(netBehind);
            networks.remove(netAhead);
            for (BlockPos p : netBehind.getPath()) blockToNetwork.remove(p);
            for (BlockPos p : netAhead.getPath()) blockToNetwork.remove(p);
            rebuildNetworkFrom(pos, state);
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
                    rebuildNetworkFrom(p, state);
                }
            }
        }
        this.setDirty();
    }

    private void rebuildNetworkFrom(BlockPos start, BlockState startState) {
        ConveyorNetwork net = new ConveyorNetwork();
        
        // Find absolute start by traversing backward
        BlockPos current = start;
        BlockState currentState = startState;
        while (true) {
            BlockPos prev = getPrevPos(current, currentState);
            if (isSameDirectionConveyor(prev, current, false)) {
                current = prev;
                currentState = level.getBlockState(current);
            } else {
                break;
            }
        }

        // Traverse forward to build path
        while (true) {
            net.addBlockToEnd(current);
            blockToNetwork.put(current, net);
            
            BlockPos next = getNextPos(current, currentState);
            if (isSameDirectionConveyor(next, current, true)) {
                current = next;
                currentState = level.getBlockState(current);
            } else {
                break;
            }
        }

        networks.add(net);
        this.setDirty();
    }

    private boolean isSameDirectionConveyor(BlockPos otherPos, BlockPos myPos, boolean isAhead) {
        BlockState myState = level.getBlockState(myPos);
        BlockState otherState = level.getBlockState(otherPos);
        
        if (!(myState.getBlock() instanceof ConveyorBlock) || !(otherState.getBlock() instanceof ConveyorBlock)) {
            return false;
        }
        
        // If they are both conveyors, check if they connect
        BlockPos expectedFromMyPerspecive = isAhead ? getNextPos(myPos, myState) : getPrevPos(myPos, myState);
        if (!expectedFromMyPerspecive.equals(otherPos)) {
            return false; // I don't point to them
        }
        
        BlockPos expectedFromTheirPerspective = isAhead ? getPrevPos(otherPos, otherState) : getNextPos(otherPos, otherState);
        if (!expectedFromTheirPerspective.equals(myPos)) {
            return false; // They don't point to me
        }
        
        return true;
    }
}
