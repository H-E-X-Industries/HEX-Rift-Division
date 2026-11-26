package com.trd.block.basic.nature.oil;

import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilVeinManager;
import com.trd.api.nature.ore.oil.ReservoirType;
import com.trd.item.ModItems;
import com.trd.item.nature.oil.OilRockChunkItem;
import com.trd.worldgen.feature.oil.OilCrossChunkVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class OilReservoirBlock extends Block {

    private final ReservoirType reservoirType;

    public OilReservoirBlock(Properties properties, ReservoirType reservoirType) {
        super(properties);
        this.reservoirType = reservoirType;
    }

    public ReservoirType getReservoirType() {
        return reservoirType;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(this);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Легкие струйки нефтяного пара/испарений над открытыми блоками нефти
        if (random.nextFloat() < 0.06f) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).isAir()) {
                double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
                double y = pos.getY() + 1.02;
                double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.015 + random.nextDouble() * 0.015, 0.0);
            }
        }
    }

    /**
     * Отбор керна / образца породы литой киркой.
     * Быстрый детерминированный поиск жилы по координатам без BlockEntity!
     * @param tierLevel 0=iron(30%), 1=steel(45-50%), 2=titanium(60%)
     */
    public void mineWithCastPickaxe(ServerLevel level, BlockPos pos, Player player, int tierLevel) {
        OilCrossChunkVeins.OilVein vein = OilCrossChunkVeins.getVeinAt(level, pos);

        if (vein == null) {
            // Поверхностное нефтепроявление (следы) или блок вне подземного кармана
            player.displayClientMessage(
                    Component.literal("§7[TRD] Поверхностные следы выветрены и окислены. Пробуритесь к подземному карману для отбора керна."),
                    true
            );
            return;
        }

        float chunkChance = player.isCreative() ? 1.0f : switch (tierLevel) {
            case 0 -> 0.30f;
            case 1 -> 0.50f;
            case 2 -> 0.60f;
            default -> 0.30f;
        };

        if (level.random.nextFloat() < chunkChance) {
            OilComposition comp = OilCrossChunkVeins.resolveAuthoritativeComposition(level, vein, pos);
            if (this.reservoirType != ReservoirType.TAR_SANDS && comp.getReservoirType() != this.reservoirType) {
                comp = comp.withReservoirType(this.reservoirType);
            }
            UUID veinId = OilCrossChunkVeins.veinUuid(level.getSeed(), vein);
            OilVeinManager.OilVeinData veinData = OilVeinManager.get(level).ensureVeinRegistered(
                    veinId, comp, vein.estimateBlockCount(), vein.cy,
                    new ChunkPos(vein.cx >> 4, vein.cz >> 4)
            );
            Holder<Biome> veinBiome = OilCrossChunkVeins.resolveVeinCenterBiome(level, vein);
            if (veinBiome == null) {
                veinBiome = level.getBiome(pos);
            }
            ItemStack chunk = OilRockChunkItem.createFromComposition(
                    comp, veinBiome, veinData.remainingUnits, veinData.maxUnits
            );
            Block.popResource(level, pos, chunk);
        } else {
            Block.popResource(level, pos, new ItemStack(ModItems.HARD_ROCK.get()));
            player.displayClientMessage(
                    Component.literal("§e[TRD] Образец породы раскрошился при отборе. Попробуйте еще раз."),
                    true
            );
        }
    }
}
