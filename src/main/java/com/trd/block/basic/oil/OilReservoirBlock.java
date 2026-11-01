package com.trd.block.basic.oil;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.api.oil.OilComposition;
import com.trd.api.oil.OilVeinManager;
import com.trd.api.oil.ReservoirType;
import com.trd.api.oil.VeinShape;
import com.trd.block.entity.oil.OilReservoirBlockEntity;
import com.trd.item.ModItems;
import com.trd.item.oil.OilRockChunkItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class OilReservoirBlock extends BaseEntityBlock {

    public static final MapCodec<OilReservoirBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    net.minecraft.util.StringRepresentable.fromEnum(ReservoirType::values)
                            .fieldOf("reservoir_type").forGetter(OilReservoirBlock::getReservoirType)
            ).apply(instance, OilReservoirBlock::new)
    );

    private final ReservoirType reservoirType;

    public OilReservoirBlock(Properties properties, ReservoirType reservoirType) {
        super(properties);
        this.reservoirType = reservoirType;
    }

    public ReservoirType getReservoirType() {
        return reservoirType;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OilReservoirBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
     * @param tierLevel 0=iron(30%), 1=steel(45-50%), 2=titanium(60%)
     */
    public void mineWithCastPickaxe(ServerLevel level, BlockPos pos, Player player, int tierLevel) {
        BlockEntity be = level.getBlockEntity(pos);
        OilVeinManager.OilVeinData vein = null;
        if (be instanceof OilReservoirBlockEntity entity && entity.getVeinId() != null) {
            vein = OilVeinManager.get(level).getVein(entity.getVeinId());
        }

        if (vein == null) {
            // Поверхностное нефтепроявление (следы) или блок вне подземного кармана
            Block.popResource(level, pos, new ItemStack(this.asItem()));
            player.displayClientMessage(
                    Component.literal("§7[TRD] Поверхностные следы выветрены и окислены. Пробуритесь к подземному карману для отбора керна."),
                    true
            );
            return;
        }

        Holder<Biome> biome = level.getBiome(pos);

        float chunkChance = switch (tierLevel) {
            case 0 -> 0.30f;
            case 1 -> 0.50f;
            case 2 -> 0.60f;
            default -> 0.30f;
        };

        if (level.random.nextFloat() < chunkChance) {
            OilComposition comp = vein.composition;
            if (this.reservoirType != ReservoirType.TAR_SANDS && comp.getReservoirType() != this.reservoirType) {
                comp = comp.withReservoirType(this.reservoirType);
            }
            ItemStack chunk = OilRockChunkItem.createFromComposition(comp, biome);
            Block.popResource(level, pos, chunk);
        } else {
            Block.popResource(level, pos, new ItemStack(ModItems.HARD_ROCK.get()));
        }
    }
}
