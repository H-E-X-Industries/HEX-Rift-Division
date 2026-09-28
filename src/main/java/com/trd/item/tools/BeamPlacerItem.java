package com.trd.item.tools;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.deco.BeamCollisionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BeamPlacerItem extends Item {
    public static final double MAX_DISTANCE = 32.0;

    public BeamPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (readFirstPos(stack).isPresent()) {
                writeFirstPos(stack, null);
                player.sendSystemMessage(Component.translatable("message.trd.beam_placer.reset"));
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        ItemStack toolStack = context.getItemInHand();
        Optional<BlockPos> first = readFirstPos(toolStack);

        if (player.isShiftKeyDown() && first.isPresent()) {
            writeFirstPos(toolStack, null);
            player.sendSystemMessage(Component.translatable("message.trd.beam_placer.reset"));
            return InteractionResult.SUCCESS;
        }

        BlockPos currentPos = context.getClickedPos();

        if (first.isPresent()) {
            BlockPos firstPos = first.get();

            if (firstPos.equals(currentPos)) {
                player.sendSystemMessage(Component.translatable("message.trd.beam_placer.same_point"));
                writeFirstPos(toolStack, null);
                return InteractionResult.FAIL;
            }

            Vec3 startVec = Vec3.atCenterOf(firstPos);
            Vec3 endVec = Vec3.atCenterOf(currentPos);

            double distance = startVec.distanceTo(endVec);

            if (distance > MAX_DISTANCE) {
                player.sendSystemMessage(Component.translatable("message.trd.beam_placer.too_long", (int) MAX_DISTANCE));
                writeFirstPos(toolStack, null);
                return InteractionResult.FAIL;
            }

            int requiredBeams = (int) Math.ceil(distance);
            Item beamItem = ModBlocks.BEAM_BLOCK.get().asItem();

            if (!player.isCreative() && countItems(player, beamItem) < requiredBeams) {
                player.sendSystemMessage(Component.translatable("message.trd.beam_placer.not_enough", requiredBeams));
                writeFirstPos(toolStack, null);
                return InteractionResult.FAIL;
            }

            Vec3 direction = endVec.subtract(startVec).normalize();
            double stepSize = 0.25;
            int steps = (int) Math.ceil(distance / stepSize);

            // 1. Проверяем, что линия не занята другими блоками
            for (int i = 1; i <= steps; i++) {
                double currentDist = Math.min(i * stepSize, distance - 0.01);
                Vec3 stepVec = startVec.add(direction.scale(currentDist));
                BlockPos posOnLine = BlockPos.containing(stepVec);

                if (!posOnLine.equals(firstPos) && !posOnLine.equals(currentPos)) {
                    BlockState state = level.getBlockState(posOnLine);
                    if (!state.canBeReplaced() && !state.is(ModBlocks.BEAM_COLLISION.get())) {
                        player.sendSystemMessage(Component.translatable("message.trd.beam_placer.obstructed"));
                        writeFirstPos(toolStack, null);
                        return InteractionResult.FAIL;
                    }
                }
            }

            // 2. Ставим невидимые блоки коллизии вдоль всей линии
            boolean masterPlaced = false;
            BlockPos masterPos = null;

            List<BlockPos> placedBlocks = new ArrayList<>();

            for (int i = 1; i <= steps; i++) {
                double currentDist = Math.min(i * stepSize, distance - 0.01);
                Vec3 stepVec = startVec.add(direction.scale(currentDist));
                BlockPos posOnLine = BlockPos.containing(stepVec);

                if (level.getBlockState(posOnLine).canBeReplaced() || level.getBlockState(posOnLine).is(ModBlocks.BEAM_COLLISION.get())) {
                    if (level.getBlockState(posOnLine).canBeReplaced()) {
                        level.setBlock(posOnLine, ModBlocks.BEAM_COLLISION.get().defaultBlockState(), 3);
                    }
                    if (!placedBlocks.contains(posOnLine)) {
                        placedBlocks.add(posOnLine);
                    }
                }
            }

            if (placedBlocks.isEmpty()) {
                writeFirstPos(toolStack, null);
                return InteractionResult.FAIL;
            }

            // Раскладываем целые сегменты балки по ближайшим блокам
            int fullBlocks = (int) distance;
            float remainder = (float) (distance - fullBlocks);
            Map<BlockPos, List<Integer>> segmentMap = new HashMap<>();

            for (int i = 0; i <= fullBlocks; i++) {
                if (i == fullBlocks && remainder <= 0.001f) break;
                double segLength = (i == fullBlocks) ? remainder : 1.0;
                Vec3 segCenter = startVec.add(direction.scale(i + segLength / 2.0));

                BlockPos closest = null;
                double minDist = Double.MAX_VALUE;
                for (BlockPos p : placedBlocks) {
                    double dist = Vec3.atCenterOf(p).distanceTo(segCenter);
                    if (dist < minDist) {
                        minDist = dist;
                        closest = p;
                    }
                }

                if (closest != null) {
                    segmentMap.computeIfAbsent(closest, k -> new ArrayList<>()).add(i);
                }
            }

            for (BlockPos posOnLine : placedBlocks) {
                BlockEntity be = level.getBlockEntity(posOnLine);
                if (be instanceof BeamCollisionBlockEntity collisionBE) {
                    List<Integer> segs = segmentMap.getOrDefault(posOnLine, new ArrayList<>());
                    int[] segArray = segs.stream().mapToInt(Integer::intValue).toArray();

                    if (!masterPlaced) {
                        collisionBE.addMasterData(startVec, endVec, segArray);
                        masterPlaced = true;
                        masterPos = posOnLine;
                    } else {
                        collisionBE.addSlaveData(masterPos, startVec, endVec, segArray);
                    }
                }
            }

            if (!player.isCreative()) consumeItems(player, beamItem, requiredBeams);
            player.sendSystemMessage(Component.translatable("message.trd.beam_placer.placed", requiredBeams));
            writeFirstPos(toolStack, null);

        } else {
            writeFirstPos(toolStack, currentPos);
            player.sendSystemMessage(Component.translatable("message.trd.beam_placer.first_set"));
        }

        return InteractionResult.SUCCESS;
    }

    private static Optional<BlockPos> readFirstPos(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("FirstPos")) return Optional.empty();
        return NbtUtils.readBlockPos(tag, "FirstPos");
    }

    private static void writeFirstPos(ItemStack stack, BlockPos pos) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.remove("FirstPos");
        if (pos != null) {
            tag.put("FirstPos", NbtUtils.writeBlockPos(pos));
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private int countItems(Player player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private void consumeItems(Player player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                int toTake = Math.min(stack.getCount(), remaining);
                stack.shrink(toTake);
                remaining -= toTake;
                if (remaining <= 0) break;
            }
        }
    }
}
