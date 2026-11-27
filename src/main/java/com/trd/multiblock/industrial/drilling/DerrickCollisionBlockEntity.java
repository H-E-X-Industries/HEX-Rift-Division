package com.trd.multiblock.industrial.drilling;

import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Данные коллизии одного блока каркаса нефтевышки:
 * полноблочный сегмент верхнего кольца 5×5 и/или наклонные ноги по алгоритму Liang–Barsky.
 */
public class DerrickCollisionBlockEntity extends BlockEntity {

    private static final double BEAM_RADIUS = 5.0 / 16.0;
    private static final int BEAM_SHAPE_STEPS = 4;

    public record LegSegment(Vec3 start, Vec3 end) {
    }

    @Nullable
    private BlockPos controllerPos;
    private boolean fullRingBlock = false;
    private final List<LegSegment> legSegments = new ArrayList<>();
    @Nullable
    private VoxelShape cachedShape;

    public DerrickCollisionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DERRICK_COLLISION_BE.get(), pos, state);
    }

    public void setControllerPos(@Nullable BlockPos controllerPos) {
        this.controllerPos = controllerPos;
        setChanged();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setFullRingBlock(boolean fullRingBlock) {
        this.fullRingBlock = fullRingBlock;
        this.cachedShape = null;
        setChanged();
        syncToClient();
    }

    public void addLegSegment(Vec3 start, Vec3 end) {
        for (LegSegment seg : legSegments) {
            if (seg.start.distanceToSqr(start) < 0.01 && seg.end.distanceToSqr(end) < 0.01) {
                return;
            }
        }
        legSegments.add(new LegSegment(start, end));
        this.cachedShape = null;
        setChanged();
        syncToClient();
    }

    public VoxelShape getCollisionShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    private VoxelShape computeShape() {
        VoxelShape shape = fullRingBlock ? Shapes.block() : Shapes.empty();
        if (!legSegments.isEmpty()) {
            for (LegSegment seg : legSegments) {
                for (AABB box : segmentBoxesInBlock(seg.start, seg.end, worldPosition)) {
                    shape = Shapes.joinUnoptimized(shape, Shapes.create(box), BooleanOp.OR);
                }
            }
            if (!shape.isEmpty()) {
                shape = shape.optimize();
            }
        }
        return shape;
    }

    private static List<AABB> segmentBoxesInBlock(Vec3 worldStart, Vec3 worldEnd, BlockPos pos) {
        List<AABB> boxes = new ArrayList<>(BEAM_SHAPE_STEPS);
        Vec3 start = worldStart.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 end = worldEnd.subtract(pos.getX(), pos.getY(), pos.getZ());

        double[] dir = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] origin = {start.x, start.y, start.z};

        double tEnter = 0.0;
        double tExit = 1.0;

        for (int axis = 0; axis < 3; axis++) {
            double d = dir[axis];
            double o = origin[axis];
            if (Math.abs(d) < 1.0E-9) {
                if (o < 0.0 || o > 1.0) {
                    return boxes;
                }
                continue;
            }
            double inv = 1.0 / d;
            double t0 = (0.0 - o) * inv;
            double t1 = (1.0 - o) * inv;
            if (t0 > t1) {
                double tmp = t0;
                t0 = t1;
                t1 = tmp;
            }
            if (t0 > tEnter) tEnter = t0;
            if (t1 < tExit) tExit = t1;
            if (tEnter > tExit) {
                return boxes;
            }
        }

        if (tExit - tEnter <= 1.0E-7) {
            return boxes;
        }

        int axes = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) > 1.0E-9) axes++;
        }
        int steps = axes <= 1 ? 1 : BEAM_SHAPE_STEPS;

        for (int i = 0; i < steps; i++) {
            double ta = tEnter + (tExit - tEnter) * i / steps;
            double tb = tEnter + (tExit - tEnter) * (i + 1) / steps;

            double minX = Math.min(start.x + dir[0] * ta, start.x + dir[0] * tb) - BEAM_RADIUS;
            double minY = Math.min(start.y + dir[1] * ta, start.y + dir[1] * tb) - BEAM_RADIUS;
            double minZ = Math.min(start.z + dir[2] * ta, start.z + dir[2] * tb) - BEAM_RADIUS;
            double maxX = Math.max(start.x + dir[0] * ta, start.x + dir[0] * tb) + BEAM_RADIUS;
            double maxY = Math.max(start.y + dir[1] * ta, start.y + dir[1] * tb) + BEAM_RADIUS;
            double maxZ = Math.max(start.z + dir[2] * ta, start.z + dir[2] * tb) + BEAM_RADIUS;

            if (maxX <= 0.0 || minX >= 1.0 || maxY <= 0.0 || minY >= 1.0 || maxZ <= 0.0 || minZ >= 1.0) {
                continue;
            }

            boxes.add(new AABB(
                    Math.max(0.0, minX), Math.max(0.0, minY), Math.max(0.0, minZ),
                    Math.min(1.0, maxX), Math.min(1.0, maxY), Math.min(1.0, maxZ)
            ));
        }

        return boxes;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong("ControllerPos", controllerPos.asLong());
        }
        tag.putBoolean("FullRing", fullRingBlock);
        ListTag list = new ListTag();
        for (LegSegment seg : legSegments) {
            CompoundTag st = new CompoundTag();
            st.putDouble("sx", seg.start.x);
            st.putDouble("sy", seg.start.y);
            st.putDouble("sz", seg.start.z);
            st.putDouble("ex", seg.end.x);
            st.putDouble("ey", seg.end.y);
            st.putDouble("ez", seg.end.z);
            list.add(st);
        }
        tag.put("Legs", list);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("ControllerPos") ? BlockPos.of(tag.getLong("ControllerPos")) : null;
        fullRingBlock = tag.getBoolean("FullRing");
        legSegments.clear();
        ListTag list = tag.getList("Legs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag st = list.getCompound(i);
            legSegments.add(new LegSegment(
                    new Vec3(st.getDouble("sx"), st.getDouble("sy"), st.getDouble("sz")),
                    new Vec3(st.getDouble("ex"), st.getDouble("ey"), st.getDouble("ez"))
            ));
        }
        cachedShape = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        cachedShape = null;
    }
}
