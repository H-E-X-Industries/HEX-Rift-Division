package com.trd.item.industrial.rotation;

import com.trd.api.rotation.BeltConnectionHelper;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BeltItem extends Item {
    public static final int MAX_BELT_LENGTH = 16;

    public BeltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos posB = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState stateB = level.getBlockState(posB);

        if (!(stateB.getBlock() instanceof ShaftBlock)) return InteractionResult.PASS;

        if (level.getBlockEntity(posB) instanceof ShaftBlockEntity beB) {
            if (!beB.hasPulley()) {
                if (!level.isClientSide && player != null) player.displayClientMessage(Component.translatable("message.trd.belt.pulleys_only"), true);
                return InteractionResult.FAIL;
            }

            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

            if (!tag.contains("SelectedPulley")) {
                if (beB.getConnectedPulley() != null) {
                    if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.already_connected"), true);
                    return InteractionResult.FAIL;
                }
                tag.put("SelectedPulley", NbtUtils.writeBlockPos(posB));
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.first_selected"), true);
                level.playSound(null, posB, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }

            BlockPos posA = NbtUtils.readBlockPos(tag, "SelectedPulley").orElse(null);
            if (posA == null) {
                tag.remove("SelectedPulley");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                return InteractionResult.FAIL;
            }

            if (posA.equals(posB)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.cancelled"), true);
                tag.remove("SelectedPulley");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                return InteractionResult.SUCCESS;
            }

            if (posA.distSqr(posB) > MAX_BELT_LENGTH * MAX_BELT_LENGTH) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.too_far", MAX_BELT_LENGTH), true);
                return InteractionResult.FAIL;
            }

            BlockState stateA = level.getBlockState(posA);
            if (!(stateA.getBlock() instanceof ShaftBlock) || !(level.getBlockEntity(posA) instanceof ShaftBlockEntity beA) || !beA.hasPulley()) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.first_destroyed"), true);
                tag.remove("SelectedPulley");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                return InteractionResult.FAIL;
            }

            Direction.Axis axisA = stateA.getValue(ShaftBlock.FACING).getAxis();
            Direction.Axis axisB = stateB.getValue(ShaftBlock.FACING).getAxis();

            if (axisA != axisB) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.axis_mismatch"), true);
                return InteractionResult.FAIL;
            }

            if ((axisA == Direction.Axis.X && posA.getX() != posB.getX()) ||
                    (axisA == Direction.Axis.Y && posA.getY() != posB.getY()) ||
                    (axisA == Direction.Axis.Z && posA.getZ() != posB.getZ())) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.not_coplanar"), true);
                return InteractionResult.FAIL;
            }

            if (beB.getConnectedPulley() != null || beA.getConnectedPulley() != null) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.pulley_occupied"), true);
                tag.remove("SelectedPulley");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                return InteractionResult.FAIL;
            }

            InteractionResult result = BeltConnectionHelper.tryConnectPulleys(level, player, posA, posB);

            if (result == InteractionResult.SUCCESS) {
                tag.remove("SelectedPulley");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                if (player != null && !player.isCreative()) stack.shrink(1);

                if (player != null) player.displayClientMessage(Component.translatable("message.trd.belt.success"), true);
                level.playSound(null, posB, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);

                KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
                manager.updateNetworkAfterRemove(posA);
                manager.updateNetworkAfterPlace(posA);
            }

            return result;
        }
        return InteractionResult.PASS;
    }
}
