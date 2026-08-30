package com.trd.block.basic.industrial.fluids;

import com.trd.api.fluids.system.BarrelTier;
import com.trd.api.fluids.system.BaseFluidType;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity;
import com.trd.item.industrial.fluids.FluidIdentifierItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;

public class FluidBarrelBlock extends BaseEntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape BASE_SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape NORTH_SHAPE = Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 2.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(5.0D, 5.0D, 14.0D, 11.0D, 11.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 5.0D, 5.0D, 2.0D, 11.0D, 11.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(14.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D);

    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int i = 0; i < 16; i++) {
            VoxelShape shape = BASE_SHAPE;
            if ((i & 1) != 0) shape = Shapes.or(shape, NORTH_SHAPE);
            if ((i & 2) != 0) shape = Shapes.or(shape, SOUTH_SHAPE);
            if ((i & 4) != 0) shape = Shapes.or(shape, EAST_SHAPE);
            if ((i & 8) != 0) shape = Shapes.or(shape, WEST_SHAPE);
            SHAPES[i] = shape;
        }
    }

    private static int getShapeIndex(BlockState state) {
        int index = 0;
        if (state.getValue(NORTH)) index |= 1;
        if (state.getValue(SOUTH)) index |= 2;
        if (state.getValue(EAST)) index |= 4;
        if (state.getValue(WEST)) index |= 8;
        return index;
    }

    private final BarrelTier tier;

    public FluidBarrelBlock(BarrelTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    public BarrelTier getTier() { return tier; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getShapeIndex(state)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    public static boolean canConnectToPipe(BlockGetter level, BlockPos neighborPos) {
        return level.getBlockState(neighborPos).getBlock() instanceof FluidPipeBlock;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.defaultBlockState()
                .setValue(NORTH, canConnectToPipe(level, pos.north()))
                .setValue(SOUTH, canConnectToPipe(level, pos.south()))
                .setValue(EAST, canConnectToPipe(level, pos.east()))
                .setValue(WEST, canConnectToPipe(level, pos.west()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (dir.getAxis().isHorizontal()) {
            boolean connected = neighborState.getBlock() instanceof FluidPipeBlock;
            return switch (dir) {
                case NORTH -> state.setValue(NORTH, connected);
                case SOUTH -> state.setValue(SOUTH, connected);
                case EAST -> state.setValue(EAST, connected);
                case WEST -> state.setValue(WEST, connected);
                default -> state;
            };
        }
        return super.updateShape(state, dir, neighborState, level, currentPos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidBarrelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FLUID_BARREL_BE.get(), FluidBarrelBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidBarrelBlockEntity be) {
                String selectedFluidId = FluidIdentifierItem.getSelectedFluid(stack);
                be.setFilter(selectedFluidId);
                if (selectedFluidId.equals("none")) {
                    player.displayClientMessage(Component.translatable("message.trd.fluid_barrel.filter_reset"), true);
                    level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
                } else {
                    net.minecraft.world.level.material.Fluid fluidToSet = ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(selectedFluidId));
                    String fluidName = fluidToSet != null ? Component.translatable(fluidToSet.getFluidType().getDescriptionId()).getString() : selectedFluidId;
                    player.displayClientMessage(Component.translatable("message.trd.fluid_barrel.filter_set", fluidName), true);
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof FluidBarrelBlockEntity) {
                net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, (FluidBarrelBlockEntity) entity, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!(newState.getBlock() instanceof FluidBarrelBlock)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FluidBarrelBlockEntity barrel) {
                    barrel.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
                        }
                    });
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level pLevel, net.minecraft.core.BlockPos pPos, net.minecraft.world.level.block.state.BlockState pState, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity pPlacer, net.minecraft.world.item.ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            net.minecraft.world.level.block.entity.BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof FluidBarrelBlockEntity barrelBE) {
                net.minecraft.nbt.CompoundTag itemNbt = pStack.getTag();
                if (itemNbt != null && itemNbt.contains("BlockEntityTag")) {
                    barrelBE.load(itemNbt.getCompound("BlockEntityTag"));
                    barrelBE.setChanged();
                }
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(net.minecraft.world.level.block.state.BlockState pState, net.minecraft.world.level.storage.loot.LootParams.Builder pParams) {
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = pParams.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof FluidBarrelBlockEntity barrel) {
            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(this);
            // Полностью пустая бочка выпадает чистой — БЕЗ жидкости и БЕЗ выбранного фильтра.
            if (!barrel.fluidTank.isEmpty()) {
                net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                barrel.saveAdditional(nbt);
                itemStack.addTagElement("BlockEntityTag", nbt);
            }
            return java.util.Collections.singletonList(itemStack);
        }
        return super.getDrops(pState, pParams);
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, BlockGetter level, net.minecraft.core.BlockPos pos, Player player) {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidBarrelBlockEntity barrel && !barrel.fluidTank.isEmpty()) {
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            barrel.saveAdditional(nbt);
            nbt.remove("Inventory");
            stack.addTagElement("BlockEntityTag", nbt);
        }
        return stack;
    }

    private int getFluidColor(@Nullable Fluid fluid) {
        if (fluid == null) return 0xFFFFFF;
        net.minecraftforge.fluids.FluidType type = fluid.getFluidType();
        if (type instanceof BaseFluidType base) {
            return base.getTintColor();
        }
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id == null) return 0xFFFFFF;
        if (id.equals(new ResourceLocation("water"))) return 0x3F76E4;
        if (id.equals(new ResourceLocation("lava"))) return 0xFF4500;
        return 0xFFFFFF;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack pStack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter pLevel, java.util.List<net.minecraft.network.chat.Component> pTooltip, net.minecraft.world.item.TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);

        pTooltip.add(Component.translatable("tooltip.trd.fluid_barrel.melting_point").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tier.getMeltingPoint() + "°C").withStyle(ChatFormatting.GOLD)));

        pTooltip.add(Component.translatable("tooltip.trd.fluid_barrel.corrosion_resistance").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(tier.getCorrosionResistance())).withStyle(ChatFormatting.YELLOW)));

        if (tier.isLeaking()) {
            pTooltip.add(Component.translatable("tooltip.trd.fluid_barrel.leaking").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(tier.getLeakRate() + Component.translatable("tooltip.trd.fluid_barrel.leak_rate_unit").getString()).withStyle(ChatFormatting.RED)));
        }

        net.minecraft.nbt.CompoundTag nbt = pStack.getTag();
        net.minecraft.nbt.CompoundTag beTag = (nbt != null && nbt.contains("BlockEntityTag")) ? nbt.getCompound("BlockEntityTag") : null;

        String fluidName = beTag != null ? beTag.getString("FluidName") : "";
        int amount = beTag != null ? beTag.getInt("Amount") : 0;
        boolean hasFluid = !fluidName.isEmpty() && !fluidName.equals("minecraft:empty") && amount > 0;

        String filter = beTag != null ? beTag.getString("FluidFilter") : "";
        boolean hasFilter = filter != null && !filter.isEmpty() && !filter.equals("none");

        String displayId = hasFluid ? fluidName : (hasFilter ? filter : "");

        if (!displayId.isEmpty()) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(displayId));
            String localizedName = fluid != null ? Component.translatable(fluid.getFluidType().getDescriptionId()).getString() : displayId;
            int color = getFluidColor(fluid);
            pTooltip.add(Component.translatable("tooltip.trd.fluid_barrel.fluid_amount", localizedName, amount, tier.getCapacity())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
    }
}