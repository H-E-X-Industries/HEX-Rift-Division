package com.trd.block.entity.deco;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class BeamCollisionBlockEntity extends BlockEntity {
    public static class BeamData {
        public Vec3 startPos;
        public Vec3 endPos;
        public int[] segmentsToRender;
        public boolean isMaster;
        public BlockPos masterPos;

        public BeamData(Vec3 startPos, Vec3 endPos, int[] segmentsToRender, boolean isMaster, BlockPos masterPos) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.segmentsToRender = segmentsToRender;
            this.isMaster = isMaster;
            this.masterPos = masterPos;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("StartX", startPos.x);
            tag.putDouble("StartY", startPos.y);
            tag.putDouble("StartZ", startPos.z);
            tag.putDouble("EndX", endPos.x);
            tag.putDouble("EndY", endPos.y);
            tag.putDouble("EndZ", endPos.z);
            tag.putIntArray("Segments", segmentsToRender);
            tag.putBoolean("IsMaster", isMaster);
            if (!isMaster && masterPos != null) {
                tag.put("MasterPos", NbtUtils.writeBlockPos(masterPos));
            }
            return tag;
        }

        public static BeamData deserialize(CompoundTag tag) {
            Vec3 start = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
            Vec3 end = new Vec3(tag.getDouble("EndX"), tag.getDouble("EndY"), tag.getDouble("EndZ"));
            int[] segments = tag.getIntArray("Segments");
            boolean isMaster = tag.getBoolean("IsMaster");
            BlockPos master = isMaster ? null : NbtUtils.readBlockPos(tag, "MasterPos").orElse(null);
            return new BeamData(start, end, segments, isMaster, master);
        }
    }

    private final List<BeamData> beams = new CopyOnWriteArrayList<>();
    public boolean isDestroyed = false;

    // Model Properties для DynamicBakedModel
    public static final ModelProperty<List<BeamData>> BEAMS_LIST = new ModelProperty<>();
    public static final ModelProperty<BlockPos> MY_POS = new ModelProperty<>();

    public BeamCollisionBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.BEAM_COLLISION_BE.get(), pPos, pBlockState);
    }

    public void addMasterData(Vec3 start, Vec3 end, int[] segments) {
        if (!hasBeam(start, end)) {
            this.beams.add(new BeamData(start, end, segments, true, null));
            this.cachedShape = null;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    public void addSlaveData(BlockPos masterPos, Vec3 start, Vec3 end, int[] segments) {
        if (!hasBeam(start, end)) {
            this.beams.add(new BeamData(start, end, segments, false, masterPos));
            this.cachedShape = null;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    private boolean hasBeam(Vec3 start, Vec3 end) {
        for (BeamData data : beams) {
            if (data.startPos.distanceToSqr(start) < 0.01 && data.endPos.distanceToSqr(end) < 0.01) {
                return true;
            }
        }
        return false;
    }

    public List<BeamData> getBeams() {
        return beams;
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider registries) {
        super.saveAdditional(pTag, registries);
        ListTag listTag = new ListTag();
        for (BeamData data : beams) {
            listTag.add(data.serialize());
        }
        pTag.put("BeamsList", listTag);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider registries) {
        super.loadAdditional(pTag, registries);
        this.beams.clear();
        this.cachedShape = null;
        if (pTag.contains("BeamsList")) {
            ListTag listTag = pTag.getList("BeamsList", 10);
            for (int i = 0; i < listTag.size(); i++) {
                this.beams.add(BeamData.deserialize(listTag.getCompound(i)));
            }
        } else if (pTag.contains("StartX")) {
            // Старый формат: одна балка прямо в корне тега
            Vec3 start = new Vec3(pTag.getDouble("StartX"), pTag.getDouble("StartY"), pTag.getDouble("StartZ"));
            Vec3 end = new Vec3(pTag.getDouble("EndX"), pTag.getDouble("EndY"), pTag.getDouble("EndZ"));
            int[] segments = pTag.contains("Segments") ? pTag.getIntArray("Segments") : new int[0];
            boolean isM = pTag.getBoolean("IsMaster");
            BlockPos mPos = isM ? null : NbtUtils.readBlockPos(pTag, "MasterPos").orElse(null);
            this.beams.add(new BeamData(start, end, segments, isM, mPos));
        }
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private VoxelShape cachedShape = null;

    public VoxelShape getCollisionShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /** Радиус сечения луча в блоках. */
    private static final double BEAM_RADIUS = 5.0 / 16.0;

    /** На сколько частей режется сечение луча внутри одного блока. */
    private static final int BEAM_SHAPE_STEPS = 4;

    private VoxelShape computeShape() {
        if (beams.isEmpty()) {
            return Shapes.empty();
        }

        BlockPos myPos = this.getBlockPos();
        VoxelShape shape = Shapes.empty();

        for (BeamData data : beams) {
            for (AABB local : beamBoxesInBlock(data, myPos)) {
                shape = Shapes.joinUnoptimized(shape, Shapes.create(local), BooleanOp.OR);
            }
        }

        return shape.isEmpty() ? Shapes.empty() : shape.optimize();
    }

    /**
     * Пересекает отрезок луча с кубом блока и возвращает локальные AABB (0..1) сечения.
     * Раньше здесь был пошаговый сэмплинг с шагом 1/8 блока, из-за чего на длинных лучах
     * в shape попадали сотни дробных боксов и коллизия «рассыпалась» на стыках.
     * Сейчас отрезок режется на куб параметрическим отсевом, а результат делится
     * максимум на {@link #BEAM_SHAPE_STEPS} частей — на сквозных лучах это ровно один
     * бокс, на диагональных получается аккуратная «лесенка» вместо целого куба.
     */
    private static List<AABB> beamBoxesInBlock(BeamData data, BlockPos pos) {
        List<AABB> boxes = new ArrayList<>(BEAM_SHAPE_STEPS);

        // Отрезок в координатах блока: 0..1 по каждой оси
        Vec3 start = data.startPos.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 end = data.endPos.subtract(pos.getX(), pos.getY(), pos.getZ());

        double[] dir = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] origin = {start.x, start.y, start.z};

        // Параметрический отрезок [tEnter, tExit], внутри которого луч лежит в кубе 0..1
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

        // Сколько осей затрагивает сечение: по одной оси — ровно один бокс,
        // по двум-трём режем на части, иначе диагональ распухает до целого куба
        int axes = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) > 1.0E-9) {
                axes++;
            }
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

    public void removeBeamData(Vec3 start, Vec3 end) {
        beams.removeIf(data -> data.startPos.distanceToSqr(start) < 0.01 && data.endPos.distanceToSqr(end) < 0.01);
        cachedShape = null;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            if (beams.isEmpty()) {
                this.level.removeBlock(this.getBlockPos(), false);
            }
        }
    }

    public void breakEntireBeam(net.minecraft.world.level.Level level) {
        if (this.isDestroyed || beams.isEmpty()) return;
        this.isDestroyed = true;

        List<BeamData> beamsCopy = new ArrayList<>(this.beams);

        for (BeamData data : beamsCopy) {
            double distance = data.startPos.distanceTo(data.endPos);
            int amountToDrop = (int) Math.ceil(distance);

            ItemStack dropStack = new ItemStack(ModBlocks.BEAM_BLOCK.get(), amountToDrop);
            Containers.dropItemStack(level, this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ(), dropStack);

            Vec3 direction = data.endPos.subtract(data.startPos).normalize();
            double stepSize = 0.5;
            int steps = (int) (distance / stepSize);

            for (int i = 1; i < steps; i++) {
                Vec3 stepVec = data.startPos.add(direction.scale(i * stepSize));
                BlockPos posOnLine = BlockPos.containing(stepVec);

                removeBeamDataFromPos(level, posOnLine, data.startPos, data.endPos);

                // Соседние блоки тоже могли накопить данные этой балки
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    removeBeamDataFromPos(level, posOnLine.relative(dir), data.startPos, data.endPos);
                }
            }
        }
    }

    private void removeBeamDataFromPos(net.minecraft.world.level.Level level, BlockPos pos, Vec3 start, Vec3 end) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BeamCollisionBlockEntity slaveBE) {
            slaveBE.removeBeamData(start, end);
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(BEAMS_LIST, new ArrayList<>(beams))
                .with(MY_POS, this.getBlockPos())
                .build();
    }
}
