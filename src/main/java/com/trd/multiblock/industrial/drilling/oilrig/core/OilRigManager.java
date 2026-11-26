package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Поуровневый кэш топологии модульных нефтевышек в памяти ServerLevel.
 * Персистентность остаётся внутри NBT самих BlockEntity — отдельный SavedData не используется.
 * Связи пересобираются событийно при добавлении, удалении или загрузке модулей.
 */
public final class OilRigManager {

    private static final Map<ResourceKey<Level>, OilRigManager> MANAGERS = new HashMap<>();

    private final Map<BlockPos, IRigModule> modulesByPos = new HashMap<>();
    private final Map<WellboreColumn, Set<BlockPos>> axialByColumn = new HashMap<>();
    private final Map<BlockPos, IRigModule> lateralByPos = new HashMap<>();
    private final Map<WellboreColumn, OilRigAssembly> assemblies = new HashMap<>();

    private OilRigManager() {}

    public static synchronized OilRigManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), k -> new OilRigManager());
    }

    public static synchronized void unloadLevel(ResourceKey<Level> dimension) {
        MANAGERS.remove(dimension);
    }

    public static synchronized void clearAll() {
        MANAGERS.clear();
    }

    public synchronized void onModuleAdded(ServerLevel level, IRigModule module) {
        registerModuleInternal(module);
    }

    public synchronized void onModuleLoaded(ServerLevel level, IRigModule module) {
        registerModuleInternal(module);
    }

    public synchronized void onModuleRemoved(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        IRigModule removed = modulesByPos.remove(immutablePos);
        if (removed == null) {
            return;
        }
        removed.setAssembly(null);

        if (removed.isAxialModule()) {
            WellboreColumn col = removed.getWellboreColumn();
            Set<BlockPos> set = axialByColumn.get(col);
            if (set != null) {
                set.remove(immutablePos);
                if (set.isEmpty()) {
                    axialByColumn.remove(col);
                }
            }
            rebuildColumn(col);
        } else {
            lateralByPos.remove(immutablePos);
            OilRigAssembly previous = removed.getAssembly();
            if (previous != null) {
                rebuildColumn(previous.getColumn());
            } else {
                // Повторно привязываем все активные колонны, если боковой модуль стоял рядом
                for (WellboreColumn col : new ArrayList<>(axialByColumn.keySet())) {
                    rebuildColumn(col);
                }
            }
        }
    }

    @Nullable
    public synchronized OilRigAssembly getAssembly(WellboreColumn column) {
        return assemblies.get(column);
    }

    private void registerModuleInternal(IRigModule module) {
        BlockPos pos = module.getRigPos().immutable();
        IRigModule prev = modulesByPos.put(pos, module);
        if (prev != null && prev != module) {
            prev.setAssembly(null);
        }

        if (module.isAxialModule()) {
            axialByColumn.computeIfAbsent(module.getWellboreColumn(), k -> new HashSet<>()).add(pos);
            rebuildColumn(module.getWellboreColumn());
        } else {
            lateralByPos.put(pos, module);
            // Пересобираем только ту колонну, к сокету которой может относиться этот боковой модуль
            boolean matched = false;
            for (WellboreColumn col : new ArrayList<>(axialByColumn.keySet())) {
                if (couldBelongToColumn(col, module)) {
                    rebuildColumn(col);
                    matched = true;
                }
            }
            if (!matched) {
                module.setAssembly(null);
            }
        }
    }

    private boolean couldBelongToColumn(WellboreColumn col, IRigModule lateral) {
        Set<BlockPos> axialPositions = axialByColumn.get(col);
        if (axialPositions == null) return false;
        for (BlockPos p : axialPositions) {
            IRigModule mod = modulesByPos.get(p);
            if (mod instanceof IDrillDriveModule drive && drive.isModuleValid()) {
                for (LateralSocket socket : drive.getLateralSockets()) {
                    if (socket.acceptedRole() == lateral.getModuleRole()
                            && socket.resolveWorldPos(drive.getWellCenterPos(), drive.getDriveFacing()).equals(lateral.getRigPos())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public synchronized void rebuildColumn(WellboreColumn column) {
        OilRigAssembly oldAssembly = assemblies.remove(column);
        if (oldAssembly != null) {
            if (oldAssembly.getWellhead() != null) oldAssembly.getWellhead().setAssembly(null);
            if (oldAssembly.getDrive() != null) oldAssembly.getDrive().setAssembly(null);
            if (oldAssembly.getFeeder() != null) oldAssembly.getFeeder().setAssembly(null);
            for (IBlowoutPreventerModule bop : oldAssembly.getBops()) bop.setAssembly(null);
            for (IWellEnhancerModule enh : oldAssembly.getEnhancers()) enh.setAssembly(null);
        }

        Set<BlockPos> posSet = axialByColumn.get(column);
        if (posSet == null || posSet.isEmpty()) {
            return;
        }

        // Собираем осевые модули вдоль (wellX, wellZ) и сортируем снизу вверх по Y
        List<IRigModule> axialStack = new ArrayList<>();
        Iterator<BlockPos> it = posSet.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            IRigModule mod = modulesByPos.get(p);
            if (mod == null || !mod.isModuleValid()) {
                it.remove();
                modulesByPos.remove(p);
                continue;
            }
            axialStack.add(mod);
        }
        if (axialStack.isEmpty()) {
            axialByColumn.remove(column);
            return;
        }

        axialStack.sort(Comparator.comparingInt(m -> m.getRigPos().getY()));

        IWellheadModule wellhead = null;
        IDrillDriveModule drive = null;
        List<IBlowoutPreventerModule> bops = new ArrayList<>();

        for (IRigModule mod : axialStack) {
            switch (mod.getModuleRole()) {
                case WELLHEAD -> {
                    if (wellhead == null && mod instanceof IWellheadModule w) {
                        wellhead = w;
                    }
                }
                case BLOWOUT_PREVENTER -> {
                    if (mod instanceof IBlowoutPreventerModule bop) {
                        bops.add(bop);
                    }
                }
                case DRILL_DRIVE -> {
                    if (mod instanceof IDrillDriveModule d) {
                        // Выбираем стол, расположенный над крестовиной (или единственный на колонне)
                        if (wellhead == null || d.getWellCenterPos().getY() > wellhead.getWellheadCenterPos().getY()) {
                            drive = d;
                        }
                    }
                }
                default -> {}
            }
        }

        // Привязываем боковые модули по объявленным сокетам привода
        IPipeFeederModule feeder = null;
        List<IWellEnhancerModule> enhancers = new ArrayList<>();

        if (drive != null) {
            BlockPos wellCenter = drive.getWellCenterPos();
            Direction hostFacing = drive.getDriveFacing();

            for (LateralSocket socket : drive.getLateralSockets()) {
                BlockPos targetPos = socket.resolveWorldPos(wellCenter, hostFacing);
                IRigModule lateral = lateralByPos.get(targetPos);
                if (lateral == null || !lateral.isModuleValid()) continue;
                if (lateral.getModuleRole() != socket.acceptedRole()) continue;

                if (lateral instanceof IPipeFeederModule pf) {
                    if (pf.getFeederFacing() == socket.resolveWorldFacing(hostFacing)) {
                        feeder = pf;
                    }
                } else if (lateral instanceof IWellEnhancerModule enh) {
                    enhancers.add(enh);
                }
            }
        }

        if (wellhead == null && drive == null) {
            return;
        }

        OilRigAssembly assembly = new OilRigAssembly(column, wellhead, drive, feeder, bops, enhancers);
        assemblies.put(column, assembly);

        if (wellhead != null) wellhead.setAssembly(assembly);
        if (drive != null) drive.setAssembly(assembly);
        if (feeder != null) feeder.setAssembly(assembly);
        for (IBlowoutPreventerModule bop : bops) bop.setAssembly(assembly);
        for (IWellEnhancerModule enh : enhancers) enh.setAssembly(assembly);
    }
}
