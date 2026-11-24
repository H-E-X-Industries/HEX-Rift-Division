package com.trd.climate.block;

import com.mojang.serialization.MapCodec;
import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.block.entity.MercuryThermometerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Стационарный ртутный термометр (блок).
 * <p>
 * В отличие от предметной версии ({@code MercuryThermometerItem}), которая показывает
 * температуру ТЕЛА игрока, блок измеряет температуру в точке установки:
 * если он стоит внутри герметичного помещения — комнатную, снаружи — «как её
 * почувствовал бы стоящий тут игрок» (биом, погода, укрытие, лучистое тепло).
 * Значение пересчитывается сервером раз в секунду и сникает через BlockEntity
 * update-tag; при наведении игрока на блок на экране по центру рисуется
 * HUD со столбом ртути (см. {@code com.trd.climate.client.StationaryThermometerHud}).
 * <p>
 * Ориентация (как у {@code LampBlock}, направление К опоре):
 * <ul>
 *   <li>{@code facing=down} — напольная модель, поставлен на пол;</li>
 *   <li>{@code facing=up} — напольная модель, перевёрнутая на 180°, висит на потолке;</li>
 *   <li>{@code facing=N/S/W/E} — настенная модель, прижата к стене со стороны facing.</li>
 * </ul>
 * Свойство {@link #DIAL} задаёт горизонтальную ориентацию циферблата:
 * при установке на пол/потолок термометр «смотрит» на игрока
 * (направление, противоположное взгляду ставящего).
 * <p>
 * Заполняется водой ({@code waterlogged}), как трубы/конденсатор мода
 * ({@link SimpleWaterloggedBlock}): наливается ведром снизу/сбоку, вода
 * забирается пустым ведром, при установке в воде сразу waterlogged.
 * При потере опоры (сломали стену/пол) блок сам отваливается.
 */
public class MercuryThermometerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<MercuryThermometerBlock> CODEC = simpleCodec(MercuryThermometerBlock::new);

    // facing — направление к опорной поверхности (к стене/полу/потолку)
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    // Куда смотрит циферблат (только горизонталь; для настенной версии — от опоры)
    public static final DirectionProperty DIAL = DirectionProperty.create("dial", Direction.Plane.HORIZONTAL);
    // Заполнен ли блок водой (как у труб/конденсатора мода)
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /*
     * Формы по габаритам OBJ-моделей:
     * thermometer_merc_1 (напольная): x 5.9..10.1, y 0..14.9, z 5.2..7.6 при dial=north;
     * профиль поворачивается вокруг центра ячейки на 90° вместе с моделью;
     * thermometer_merc_2 (настенная): прижата к опорной грани (z 14.2..16 при facing=south).
     */
    private static final VoxelShape SHAPE_FLOOR_NORTH = Block.box(5.9, 0.0, 5.2, 10.1, 14.9, 7.6);
    private static final VoxelShape SHAPE_FLOOR_EAST = Block.box(8.4, 0.0, 5.9, 10.8, 14.9, 10.1);
    private static final VoxelShape SHAPE_FLOOR_SOUTH = Block.box(5.9, 0.0, 8.4, 10.1, 14.9, 10.8);
    private static final VoxelShape SHAPE_FLOOR_WEST = Block.box(5.2, 0.0, 5.9, 7.6, 14.9, 10.1);
    // Потолочная: тот же профиль по xz (переворот x=180 зеркалит z, а поворот на 180° возвращает),
    // но тело занимает верхнюю часть ячейки
    private static final VoxelShape SHAPE_CEIL_NORTH = Block.box(5.9, 1.1, 5.2, 10.1, 16.0, 7.6);
    private static final VoxelShape SHAPE_CEIL_EAST = Block.box(8.4, 1.1, 5.9, 10.8, 16.0, 10.1);
    private static final VoxelShape SHAPE_CEIL_SOUTH = Block.box(5.9, 1.1, 8.4, 10.1, 16.0, 10.8);
    private static final VoxelShape SHAPE_CEIL_WEST = Block.box(5.2, 1.1, 5.9, 7.6, 16.0, 10.1);
    // Настенные (базовая ориентация стеновой модели — прижата к south, циферблат смотрит на север)
    private static final VoxelShape SHAPE_SOUTH =
            Block.box(6.3, 0.0, 14.2, 10.4, 15.0, 16.0);
    private static final VoxelShape SHAPE_NORTH =
            Block.box(6.3, 0.0, 0.0, 10.4, 15.0, 1.8);
    private static final VoxelShape SHAPE_EAST =
            Block.box(14.2, 0.0, 6.3, 16.0, 15.0, 10.4);
    private static final VoxelShape SHAPE_WEST =
            Block.box(0.0, 0.0, 6.3, 1.8, 15.0, 10.4);

    public MercuryThermometerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.DOWN)
                .setValue(DIAL, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIAL, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MercuryThermometerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction towardSupport = context.getClickedFace().getOpposite();
        // Напольная/потолочная версия: циферблат разворачивается на игрока;
        // настенная: «смотрит» от опоры, в помещение
        Direction dial = towardSupport.getAxis().isVertical()
                ? context.getHorizontalDirection().getOpposite()
                : towardSupport.getOpposite();
        return this.defaultBlockState()
                .setValue(FACING, towardSupport)
                .setValue(DIAL, dial)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case DOWN -> floorShape(state.getValue(DIAL), false);
            case UP -> floorShape(state.getValue(DIAL), true);
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
        };
    }

    /** Профиль напольной/потолочной модели, повёрнутый циферблатом к указанному направлению. */
    private static VoxelShape floorShape(Direction dial, boolean ceiling) {
        return switch (dial) {
            case NORTH -> ceiling ? SHAPE_CEIL_NORTH : SHAPE_FLOOR_NORTH;
            case EAST -> ceiling ? SHAPE_CEIL_EAST : SHAPE_FLOOR_EAST;
            case SOUTH -> ceiling ? SHAPE_CEIL_SOUTH : SHAPE_FLOOR_SOUTH;
            default -> ceiling ? SHAPE_CEIL_WEST : SHAPE_FLOOR_WEST;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction supportDir = state.getValue(FACING);
        BlockPos supportPos = pos.relative(supportDir);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, supportDir.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        // Вода «оседает» в блоке, как у труб мода
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    // ═══════════════════════════════════════════════════════
    // ВОДА (SimpleWaterloggedBlock: ведро наливает/забирает)
    // ═══════════════════════════════════════════════════════

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MercuryThermometerBlockEntity thermometer) {
                String reading = String.format(Locale.ROOT, "%.1f°C", thermometer.getDisplayedTemp());
                player.displayClientMessage(
                        Component.translatable("message.trd.thermometer.reading", reading), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.THERMOMETER_MERC_BE.get(),
                MercuryThermometerBlockEntity::serverTick);
    }
}
