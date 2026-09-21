package com.trd.multiblock.industrial.fueltanks;

import com.trd.api.fluids.system.BaseFluidType;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.industrial.fluids.FluidIdentifierItem;
import com.trd.multiblock.system.*;
import com.trd.multiblock.system.PartRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FuelTankBlock extends BaseEntityBlock implements com.trd.multiblock.system.IMultiblockController {
    public static final com.mojang.serialization.MapCodec<FuelTankBlock> CODEC = simpleCodec(FuelTankBlock::new);
    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static MultiblockStructureHelper helper;
    private static final String CAPACITY = "768000";

    public FuelTankBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return getStructureHelper().generateShapeFromParts(facing);
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState(),
                    '$', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'L', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState()
            );

            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '@', PartRole.CONTROLLER,
                    '$', PartRole.FLUID_CONNECTOR,
                    'L', PartRole.LADDER
            );

            String[][] layers = {
                    {
                            "##$L$##",
                            "###@###",
                            "##$L$##"
                    },
                    {
                            "###L###",
                            "#######",
                            "###L###"
                    },
                    {
                            "###L###",
                            "#######",
                            "###L###"
                    }};
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    layers,
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return helper;
    }

    @Override
    public PartRole getPartRole(BlockPos localOffset) {
        return PartRole.DEFAULT;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(level, pos, facing, this);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FuelTankBlockEntity tank) {
                net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                if (!customData.isEmpty()) {
                    customData.loadInto(tank, level.registryAccess());
                    tank.setChanged();
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FuelTankBlockEntity tank) {
                ItemStackHandler inv = tank.getInventory();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }
            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FuelTankBlockEntity tank) {
                    String selectedFluidId = FluidIdentifierItem.getSelectedFluid(stack);
                    tank.setFilter(selectedFluidId);
                    if (selectedFluidId.equals("none")) {
                        player.displayClientMessage(Component.translatable("message.trd.fuel_tank.filter_reset"), true);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
                    } else {
                        Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(selectedFluidId));
                        String fluidName = fluid != null ? Component.translatable(fluid.getFluidType().getDescriptionId()).getString() : selectedFluidId;
                        player.displayClientMessage(Component.translatable("message.trd.fuel_tank.filter_set", fluidName), true);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FuelTankBlockEntity tank) {
            player.openMenu(tank, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelTankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.FUEL_TANK_BE.get(), FuelTankBlockEntity::tick);
    }

    private int getFluidColor(@Nullable Fluid fluid) {
        if (fluid == null) return 0xFFFFFF;
        net.neoforged.neoforge.fluids.FluidType type = fluid.getFluidType();
        if (type instanceof BaseFluidType base) {
            return base.getTintColor();
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0xFFFFFF;
        if (id.equals(net.minecraft.resources.ResourceLocation.parse("water"))) return 0x3F76E4;
        if (id.equals(net.minecraft.resources.ResourceLocation.parse("lava"))) return 0xFF4500;
        return 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("tooltip.trd.fuel_tank.resistant").withStyle(ChatFormatting.GREEN));

        net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag beTag = customData.copyTag();

        String fluidName = beTag != null ? beTag.getString("FluidName") : "";
        int amount = beTag != null ? beTag.getInt("Amount") : 0;
        boolean hasFluid = !fluidName.isEmpty() && !fluidName.equals("minecraft:empty") && amount > 0;

        String filter = beTag != null ? beTag.getString("FluidFilter") : "";
        boolean hasFilter = filter != null && !filter.isEmpty() && !filter.equals("none");

        String displayId = hasFluid ? fluidName : (hasFilter ? filter : "");

        if (!displayId.isEmpty()) {
            Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(displayId));
            String loc = fluid != null ? Component.translatable(fluid.getFluidType().getDescriptionId()).getString() : displayId;
            int color = getFluidColor(fluid);
            tooltip.add(Component.translatable("tooltip.trd.fuel_tank.fluid_amount", loc, amount, CAPACITY)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
    }
}
