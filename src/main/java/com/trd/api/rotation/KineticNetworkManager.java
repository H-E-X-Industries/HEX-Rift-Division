package com.trd.api.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.trd.main.MainRegistry.LOGGER;

public class KineticNetworkManager extends SavedData {

    private static final String DATA_NAME = "trd_kinetic_networks";

    private final Map<BlockPos, KineticNetwork> blockToNetwork = new HashMap<>();
    private final Set<KineticNetwork> networks = new HashSet<>();
    private final Set<BlockPos> pendingBreakages = new HashSet<>();
    private final Set<BlockPos> pendingStructuralFailures = new HashSet<>();
    private final ServerLevel level;

    /**
     * После загрузки мира из NBT ждём этот таймер тиков перед первым пересчётом сети.
     * За это время все BlockEntity успевают инициализироваться (onLoad + tick).
     * При достижении 0 выполняется полный ребилд всех сетей от начала.
     * -1 = таймер не активен (обычный режим работы).
     */
    private int postLoadRebuildTimer = -1;

    public KineticNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static KineticNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new KineticNetworkManager(level),
                        (tag, provider) -> load(level, tag, provider),
                        null
                ),
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag networksList = new ListTag();
        for (KineticNetwork net : networks) {
            networksList.add(net.serializeNBT());
        }
        nbt.put("Networks", networksList);
        return nbt;
    }

    public static KineticNetworkManager load(ServerLevel level, CompoundTag nbt, HolderLookup.Provider provider) {
        KineticNetworkManager manager = new KineticNetworkManager(level);
        ListTag networksList = nbt.getList("Networks", Tag.TAG_COMPOUND);

        for (int i = 0; i < networksList.size(); i++) {
            CompoundTag netTag = networksList.getCompound(i);
            KineticNetwork net = KineticNetwork.deserializeNBT(netTag);

            for (BlockPos pos : net.getMembers()) {
                manager.blockToNetwork.put(pos, net);
            }
            manager.networks.add(net);
        }
        LOGGER.info("[Kinetic] Loaded {} networks from NBT. needsRecalculation=true for all.",
                manager.networks.size());
        manager.postLoadRebuildTimer = 20;
        return manager;
    }

    public void updateNetworkAfterPlace(BlockPos pos) {
        LOGGER.debug("[Kinetic] Block placed at {}", pos.toShortString());

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Rotational node)) {
            LOGGER.warn("[Kinetic] Block at {} does not support rotation!", pos.toShortString());
            return;
        }

        Set<KineticNetwork> neighborNetworks = new HashSet<>();

        for (BlockPos neighborPos : node.getPotentialConnections(level, pos)) {
            if (!level.isLoaded(neighborPos)) continue;
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);

            if (neighborBE instanceof Rotational neighborNode) {
                if (neighborNode.getPotentialConnections(level, neighborPos).contains(pos)) {
                    if (node.canConnectMechanically(pos, neighborPos, neighborNode) &&
                            neighborNode.canConnectMechanically(neighborPos, pos, node)) {

                        KineticNetwork net = blockToNetwork.get(neighborPos);
                        if (net == null) {
                            net = createNewNetworkFrom(neighborPos, null);
                        }
                        if (net != null) neighborNetworks.add(net);
                    }
                }
            }
        }

        if (neighborNetworks.isEmpty()) {
            KineticNetwork newNet = new KineticNetwork();
            registerBlockToNetwork(pos, newNet);
            newNet.recalculate(level);
        } else if (neighborNetworks.size() == 1) {
            KineticNetwork existingNet = neighborNetworks.iterator().next();

            double oldMomentum = existingNet.getExactSpeed() * existingNet.getTotalInertia();
            double newInertia = existingNet.getTotalInertia() + node.getInertiaContribution();
            double newSpeed = newInertia > 0 ? (oldMomentum / newInertia) : 0.0;

            registerBlockToNetwork(pos, existingNet);

            boolean valid = recalculateNetworkSigns(existingNet);

            if (!valid) {
                LOGGER.info("[Kinetic] Direct motor placement conflict at {}!", pos.toShortString());

                updateNetworkAfterRemove(pos);

                BlockPos blockToBreak = pos;
                for (Direction dir : node.getPropagationDirections()) {
                    BlockPos neighborPos = pos.relative(dir);
                    if (existingNet.getMembers().contains(neighborPos)) {
                        BlockEntity beNeighbor = level.getBlockEntity(neighborPos);
                        if (beNeighbor instanceof Rotational rot && !rot.isSource()) {
                            blockToBreak = neighborPos;
                            break;
                        }
                    }
                }

                level.destroyBlock(blockToBreak, true);

                if (!blockToBreak.equals(pos)) {
                    KineticNetwork newNet = new KineticNetwork();
                    registerBlockToNetwork(pos, newNet);
                    newNet.recalculate(level);
                }

                this.setDirty();
                return;
            }

            existingNet.setCurrentSpeed(newSpeed);
            existingNet.recalculate(level);
        } else {
            if (!mergeNetworks(neighborNetworks, pos)) {
                LOGGER.info("[Kinetic] Conflict detected while merging networks at {}!", pos.toShortString());
                updateNetworkAfterRemove(pos);
                level.destroyBlock(pos, true);
                this.setDirty();
                return;
            }
        }

        this.setDirty();
    }

    public void updateNetworkAfterRemove(BlockPos pos) {
        KineticNetwork oldNet = blockToNetwork.remove(pos);
        if (oldNet == null) return;

        LOGGER.debug("[Kinetic] Block removed at {}. Breaking network {}", pos.toShortString(), oldNet.getId().toString().substring(0, 8));

        Set<BlockPos> membersToRebuild = new HashSet<>(oldNet.getMembers());
        membersToRebuild.remove(pos);

        networks.remove(oldNet);

        for (BlockPos memberPos : membersToRebuild) {
            blockToNetwork.remove(memberPos);
        }

        LOGGER.debug("[Kinetic] Network {} dissolved. Rebuilding {} blocks...", oldNet.getId().toString().substring(0, 8), membersToRebuild.size());

        for (BlockPos startPos : membersToRebuild) {
            if (!blockToNetwork.containsKey(startPos)) {
                createNewNetworkFrom(startPos, pos);
            }
        }

        this.setDirty();
    }

    private KineticNetwork createNewNetworkFrom(BlockPos start, BlockPos ignorePos) {
        KineticNetwork newNet = new KineticNetwork();

        if (level.getBlockEntity(start) instanceof Rotational startNode) {
            float scale = startNode.getNetworkScale();
            double baseSpeed = scale != 0 ? (startNode.getSpeed() / (double) scale) : (double) startNode.getSpeed();
            newNet.setCurrentSpeed(baseSpeed);
        }

        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (blockToNetwork.containsKey(current)) continue;

            if (level.getBlockEntity(current) instanceof Rotational node) {
                registerBlockToNetwork(current, newNet);

                for (BlockPos neighborPos : node.getPotentialConnections(level, current)) {
                    if (visited.contains(neighborPos)) continue;
                    if (neighborPos.equals(ignorePos)) continue;

                    BlockEntity neighborBE = level.getBlockEntity(neighborPos);
                    if (neighborBE instanceof Rotational neighborNode) {
                        if (neighborNode.getPotentialConnections(level, neighborPos).contains(current) &&
                                node.canConnectMechanically(current, neighborPos, neighborNode) &&
                                neighborNode.canConnectMechanically(neighborPos, current, node)) {

                            queue.add(neighborPos);
                        }
                    }
                }
            }
        }

        recalculateNetworkSigns(newNet);
        newNet.recalculate(level);

        LOGGER.info("[Kinetic] New sub-network {} created: members={}, generators={}, speed={}, targetSpeed={}",
                newNet.getId().toString().substring(0, 8),
                newNet.getMembers().size(),
                newNet.getGenerators().size(),
                newNet.getSpeed(),
                newNet.getTargetSpeed());
        return newNet;
    }

    public boolean recalculateNetworkSigns(KineticNetwork net) {
        if (net.getMembers().isEmpty()) return true;

        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        Map<BlockPos, Float> scales = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos root = null;
        if (!net.getGenerators().isEmpty()) {
            for (BlockPos genPos : net.getGenerators()) {
                if (level.isLoaded(genPos) && level.getBlockEntity(genPos) instanceof Rotational) {
                    root = genPos;
                    break;
                }
            }
            if (root == null) root = net.getGenerators().iterator().next();
        } else {
            for (BlockPos p : net.getMembers()) {
                if (level.isLoaded(p)) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof Rotational rot && Math.abs(rot.getNetworkScale()) > 0.1f) {
                        root = p;
                        break;
                    }
                }
            }
            if (root == null) {
                for (BlockPos p : net.getMembers()) {
                    if (level.isLoaded(p) && level.getBlockEntity(p) instanceof Rotational) {
                        root = p;
                        break;
                    }
                }
            }
            if (root == null) root = net.getMembers().iterator().next();
        }

        queue.add(root);

        float rootScale = 1.0f;
        if (level.isLoaded(root) && level.getBlockEntity(root) instanceof Rotational rootNode) {
            if (Math.abs(rootNode.getNetworkScale()) > 0.1f) {
                rootScale = Math.signum(rootNode.getNetworkScale());
            }
            rootNode.setNetworkScale(rootScale);
        }
        scales.put(root, rootScale);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (!level.isLoaded(current)) continue;

            BlockEntity currentBE = level.getBlockEntity(current);
            if (currentBE instanceof Rotational node) {
                float currentScale = scales.getOrDefault(current, 1.0f);
                node.setNetworkScale(currentScale);

                for (BlockPos neighborPos : node.getPotentialConnections(level, current)) {
                    if (net.getMembers().contains(neighborPos) && !visited.contains(neighborPos)) {
                        if (!level.isLoaded(neighborPos)) continue;

                        BlockEntity neighborBE = level.getBlockEntity(neighborPos);

                        if (neighborBE instanceof Rotational neighborNode) {
                            if (!node.canConnectMechanically(current, neighborPos, neighborNode) ||
                                    !neighborNode.canConnectMechanically(neighborPos, current, node)) {
                                continue;
                            }

                            float ratio = node.calculateTransmissionRatio(current, neighborPos, neighborNode);
                            float nextScale = currentScale * ratio;

                            if (!scales.containsKey(neighborPos)) {
                                scales.put(neighborPos, nextScale);
                                queue.add(neighborPos);
                            } else {
                                float existingScale = scales.get(neighborPos);
                                if (Math.abs(existingScale - nextScale) > 0.01f) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean mergeNetworks(Set<KineticNetwork> networks, BlockPos connectorPos) {
        double totalAngularMomentum = 0;
        double totalCombinedInertia = 0;

        for (KineticNetwork net : networks) {
            totalAngularMomentum += net.getExactSpeed() * net.getTotalInertia();
            totalCombinedInertia += net.getTotalInertia();
        }

        BlockEntity be = level.getBlockEntity(connectorPos);
        if (be instanceof Rotational node) {
            totalCombinedInertia += node.getInertiaContribution();
        }

        double newSpeed = totalCombinedInertia > 0 ? (totalAngularMomentum / totalCombinedInertia) : 0.0;

        KineticNetwork mainNet = networks.iterator().next();
        networks.remove(mainNet);

        for (KineticNetwork otherNet : networks) {
            this.networks.remove(otherNet);

            for (BlockPos memberPos : otherNet.getMembers()) {
                blockToNetwork.put(memberPos, mainNet);
                mainNet.addMember(memberPos);
            }

            for (BlockPos genPos : otherNet.getGenerators()) {
                mainNet.addGenerator(genPos);
            }
        }

        registerBlockToNetwork(connectorPos, mainNet);
        boolean valid = recalculateNetworkSigns(mainNet);
        if (!valid) {
            return false;
        }
        mainNet.setCurrentSpeed(newSpeed);
        mainNet.recalculate(level);
        return true;
    }

    private void registerBlockToNetwork(BlockPos pos, KineticNetwork net) {
        blockToNetwork.put(pos, net);
        net.addMember(pos);
        networks.add(net);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Rotational rot && rot.isSource()) {
            net.addGenerator(pos);
        }
    }

    public void scheduleBreakage(BlockPos pos) {
        pendingBreakages.add(pos);
    }

    public void scheduleStructuralFailure(BlockPos pos) {
        pendingStructuralFailures.add(pos);
    }

    private void processPendingFailures() {
        if (pendingStructuralFailures.isEmpty()) return;
        Set<BlockPos> toProcess = new HashSet<>(pendingStructuralFailures);
        pendingStructuralFailures.clear();
        for (BlockPos pos : toProcess) {
            level.destroyBlock(pos, true);
        }
    }

    private void processPendingBreakages() {
        if (pendingBreakages.isEmpty()) return;

        Set<BlockPos> toProcess = new java.util.HashSet<>(pendingBreakages);
        pendingBreakages.clear();

        for (BlockPos pos : toProcess) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Rotational node) {
                boolean broken = false;
                for (Direction dir : node.getPropagationDirections()) {
                    BlockPos neighborPos = pos.relative(dir);
                    BlockEntity neighborBE = level.getBlockEntity(neighborPos);
                    if (neighborBE instanceof com.trd.block.entity.industrial.rotation.ShaftBlockEntity) {
                        level.destroyBlock(neighborPos, true);
                        broken = true;
                        break;
                    }
                }
                if (!broken) {
                    level.destroyBlock(pos, true);
                }
            }
        }
    }

    public void tickAllNetworks() {
        if (postLoadRebuildTimer > 0) {
            postLoadRebuildTimer--;
            return;
        }
        if (postLoadRebuildTimer == 0) {
            postLoadRebuildTimer = -1;
            performFullRebuild();
            return;
        }

        processPendingFailures();
        processPendingBreakages();

        boolean anyChanged = false;
        for (KineticNetwork net : networks) {
            if (net.tick(this.level)) {
                anyChanged = true;
            }
        }
        if (anyChanged) {
            this.setDirty();
        }
    }

    private void performFullRebuild() {
        LOGGER.info("[Kinetic] Post-load full rebuild started ({} known members across {} networks)",
                blockToNetwork.size(), networks.size());

        Set<BlockPos> allKnownPositions = new HashSet<>(blockToNetwork.keySet());

        blockToNetwork.clear();
        networks.clear();

        for (BlockPos pos : allKnownPositions) {
            if (blockToNetwork.containsKey(pos)) continue;
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof Rotational)) continue;

            createNewNetworkFrom(pos, null);
        }

        LOGGER.info("[Kinetic] Post-load full rebuild done. Networks: {}, mapped blocks: {}",
                networks.size(), blockToNetwork.size());
        this.setDirty();
    }

    public KineticNetwork getNetworkFor(BlockPos pos) {
        return blockToNetwork.get(pos);
    }
}
