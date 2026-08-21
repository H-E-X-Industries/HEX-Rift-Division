package com.trd.block.basic.industrial.fluids;

import com.trd.api.fluids.system.BarrelTier;
import com.trd.api.fluids.system.BaseFluidType;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity;
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

import java.util.List;

public class FluidBarrelBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private final BarrelTier tier;

    public FluidBarrelBlock(BarrelTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BarrelTier getTier() { return tier; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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

        if (stack.getItem() instanceof com.trd.item.tools.FluidIdentifierItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidBarrelBlockEntity be) {
                String selectedFluidId = com.trd.item.tools.FluidIdentifierItem.getSelectedFluid(stack);
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
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            barrel.saveAdditional(nbt);
            itemStack.addTagElement("BlockEntityTag", nbt);
            return java.util.Collections.singletonList(itemStack);
        }
        return super.getDrops(pState, pParams);
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