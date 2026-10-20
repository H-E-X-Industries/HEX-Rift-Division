package com.trd.block.entity.industrial.lighting;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.basic.industrial.lighting.FloodlightBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Промисловий прожектор (Floodlight).
 * Споживання: 40 од./такт.
 * Буфер: 1500 од.
 * Пріоритет: NORMAL.
 * Raycast виконується 1 раз на 20 тактів або миттєво при зміні сусідніх блоків.
 */
public class FloodlightBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    public static final long CONSUMPTION = 40L;
    public static final long MAX_ENERGY = 1500L;
    public static final long RECEIVE_SPEED = 80L;
    public static final int MAX_DISTANCE = 32;

    private long energyStored = 0L;
    @Nullable
    private BlockPos targetLightPos = null;
    private boolean raycastDirty = true;
    private int tickCounter = 0;

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOODLIGHT_BE.get(), pos, state);
    }

    public void markRaycastDirty() {
        this.raycastDirty = true;
    }

    // ==================== TICKER ====================

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (this.energyStored >= CONSUMPTION) {
            this.energyStored -= CONSUMPTION;
            setChanged();

            if (!state.getValue(FloodlightBlock.LIT)) {
                level.setBlock(pos, state.setValue(FloodlightBlock.LIT, true), Block.UPDATE_CLIENTS);
                this.raycastDirty = true;
            }

            this.tickCounter++;
            if (this.raycastDirty || this.tickCounter >= 20) {
                this.tickCounter = 0;
                this.raycastDirty = false;
                updateRaycast(level, pos, state);
            }
        } else {
            if (state.getValue(FloodlightBlock.LIT)) {
                level.setBlock(pos, state.setValue(FloodlightBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
            cleanupLightBlock();
        }
    }

    // ==================== RAYCAST & LIGHT PLACEMENT ====================

    private void updateRaycast(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(FloodlightBlock.FACING)) return;
        Direction facing = state.getValue(FloodlightBlock.FACING);

        Vec3 startVec = Vec3.atCenterOf(pos).add(
                facing.getStepX() * 0.55,
                facing.getStepY() * 0.55,
                facing.getStepZ() * 0.55
        );

        Vec3 endVec = startVec.add(
                facing.getStepX() * (double) MAX_DISTANCE,
                facing.getStepY() * (double) MAX_DISTANCE,
                facing.getStepZ() * (double) MAX_DISTANCE
        );

        BlockHitResult hit = level.clip(new ClipContext(
                startVec,
                endVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));

        BlockPos newTargetPos = null;

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            BlockPos lightPos = hitPos.relative(hit.getDirection());

            if (!lightPos.equals(pos) && level.isLoaded(lightPos)) {
                newTargetPos = lightPos;
            }
        } else {
            // У відкритому просторі знаходимо найвіддаленішу позицію
            BlockPos candidate = pos.relative(facing, MAX_DISTANCE);
            for (int i = MAX_DISTANCE; i >= 1; i--) {
                BlockPos checkPos = pos.relative(facing, i);
                if (level.isLoaded(checkPos)) {
                    BlockState bs = level.getBlockState(checkPos);
                    if (bs.isAir() || bs.is(Blocks.LIGHT) || bs.canBeReplaced()) {
                        candidate = checkPos;
                        break;
                    }
                }
            }
            if (!candidate.equals(pos) && level.isLoaded(candidate)) {
                newTargetPos = candidate;
            }
        }

        // Якщо точка проекції змінилась — очищаємо стару
        if (targetLightPos != null && !targetLightPos.equals(newTargetPos)) {
            cleanupLightBlockAt(targetLightPos);
            targetLightPos = null;
        }

        // Встановлюємо віртуальний блок світла
        if (newTargetPos != null && level.isLoaded(newTargetPos)) {
            BlockState targetBs = level.getBlockState(newTargetPos);
            if (targetBs.isAir() || targetBs.is(Blocks.LIGHT) || targetBs.canBeReplaced()) {
                boolean isWater = level.getFluidState(newTargetPos).getType() == Fluids.WATER;
                BlockState desiredLight = Blocks.LIGHT.defaultBlockState()
                        .setValue(LightBlock.LEVEL, 15)
                        .setValue(BlockStateProperties.WATERLOGGED, isWater);

                if (!targetBs.equals(desiredLight)) {
                    level.setBlock(newTargetPos, desiredLight, Block.UPDATE_CLIENTS);
                }
                this.targetLightPos = newTargetPos;
                setChanged();
            }
        }
    }

    private void cleanupLightBlockAt(BlockPos pos) {
        if (level != null && !level.isClientSide && pos != null && level.isLoaded(pos)) {
            BlockState bs = level.getBlockState(pos);
            if (bs.is(Blocks.LIGHT)) {
                if (bs.hasProperty(BlockStateProperties.WATERLOGGED) && bs.getValue(BlockStateProperties.WATERLOGGED)) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public void cleanupLightBlock() {
        if (targetLightPos != null) {
            cleanupLightBlockAt(targetLightPos);
            targetLightPos = null;
            setChanged();
        }
    }

    // ==================== IEnergyReceiver ====================

    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getMaxEnergyStored() {
        return MAX_ENERGY;
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0L, Math.min(energy, MAX_ENERGY));
        setChanged();
    }

    @Override
    public long getReceiveSpeed() {
        return RECEIVE_SPEED;
    }

    @Override
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    @Override
    public boolean canReceive() {
        return energyStored < MAX_ENERGY;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        long canAccept = Math.min(MAX_ENERGY - energyStored, Math.min(maxReceive, RECEIVE_SPEED));
        if (!simulate && canAccept > 0L) {
            energyStored += canAccept;
            setChanged();
        }
        return canAccept;
    }

    // ==================== IEnergyConnector ====================

    @Override
    public boolean canConnectEnergy(@Nullable Direction side) {
        if (side == null) {
            return true;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FloodlightBlock.FACING)) {
            // Лицьова сторона прожектора (скло/промінь) не приймає дроти
            return side != state.getValue(FloodlightBlock.FACING);
        }
        return true;
    }

    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    // ==================== LIFECYCLE & NETWORK ====================

    @Override
    public void onLoad() {
        super.onLoad();
        this.raycastDirty = true;
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cleanupLightBlock();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        cleanupLightBlock();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    // ==================== NBT PERSISTENCE ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Energy", energyStored);
        if (targetLightPos != null) {
            tag.putLong("TargetLightPos", targetLightPos.asLong());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("Energy");
        if (tag.contains("TargetLightPos")) {
            targetLightPos = BlockPos.of(tag.getLong("TargetLightPos"));
        } else {
            targetLightPos = null;
        }
        raycastDirty = true;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }
}
