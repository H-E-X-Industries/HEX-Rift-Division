package com.trd.block.basic.necrosis.hive;

import com.mojang.serialization.MapCodec;
import com.trd.api.hive.HiveNetworkManager;
import com.trd.api.hive.HiveNetworkMember;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.hive.HiveSoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class HiveSoilBlock extends Block implements EntityBlock {
    public static final MapCodec<HiveSoilBlock> CODEC = simpleCodec(HiveSoilBlock::new);

    public HiveSoilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HiveSoilBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (level.isClientSide) return;

        BlockEntity existingBE = level.getBlockEntity(pos);
        if (existingBE instanceof HiveSoilBlockEntity soil) {
            UUID existingId = soil.getNetworkId();

            if (existingId != null) {
                HiveNetworkManager manager = HiveNetworkManager.get(level);
                if (manager != null && manager.getNetwork(existingId) != null) {
                    manager.addNode(existingId, pos, false);
                    return;
                }
                soil.setNetworkId(null);
            }
        }

        UUID finalNetId = null;
        HiveNetworkManager manager = HiveNetworkManager.get(level);
        boolean hasColonyNeighbor = false;

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor instanceof HiveNetworkMember member) {
                UUID neighborId = member.getNetworkId();
                if (neighborId == null) continue;

                hasColonyNeighbor = true;

                if (level.getBlockState(pos.relative(dir)).is(ModBlocks.DEPTH_WORM_NEST.get())) {
                    finalNetId = neighborId;
                    break;
                }
                if (finalNetId == null) {
                    finalNetId = neighborId;
                } else if (!finalNetId.equals(neighborId)) {
                    if (manager != null) {
                        manager.mergeNetworks(finalNetId, neighborId, level);
                    }
                }
            }
        }

        if (!hasColonyNeighbor || finalNetId == null) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveNetworkMember member) {
            member.setNetworkId(finalNetId);
            if (manager != null) {
                manager.addNode(finalNetId, pos, false);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof HiveNetworkMember member) {
                UUID netId = member.getNetworkId();
                if (netId != null) {
                    HiveNetworkManager manager = HiveNetworkManager.get(level);
                    if (manager != null) {
                        manager.removeNode(netId, pos, level);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
