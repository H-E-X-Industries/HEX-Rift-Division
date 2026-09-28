package com.trd.item.weapons.activators;

import com.trd.block.basic.weapons.explosives.IDetonatable;
import com.trd.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Детонатор: присев — записывает позицию блока, стоя — взрывает её.
 * <p>
 * Позиция хранится в {@link DataComponents#CUSTOM_DATA}: в 1.21.1 у ItemStack
 * больше нет методов {@code hasTag/getTag/setTag}.
 */
public class DetonatorItem extends Item {

    private static final String NBT_POS_X = "DetPosX";
    private static final String NBT_POS_Y = "DetPosY";
    private static final String NBT_POS_Z = "DetPosZ";
    private static final String NBT_HAS_TARGET = "HasTarget";

    public DetonatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private static CompoundTag tagOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.PASS;
        }

        // Если игрок присел — сохраняем позицию
        if (player.isCrouching()) {
            CompoundTag nbt = tagOf(stack);
            nbt.putInt(NBT_POS_X, pos.getX());
            nbt.putInt(NBT_POS_Y, pos.getY());
            nbt.putInt(NBT_POS_Z, pos.getZ());
            nbt.putBoolean(NBT_HAS_TARGET, true);
            saveTag(stack, nbt);

            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.trd.detonator.saved", pos.getX(), pos.getY(), pos.getZ())
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
                playSound(level, player, ModSounds.TOOL_TECH_BOOP.get());
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Если игрок НЕ присел — активируем сохранённую позицию
        if (player.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            CompoundTag nbt = tagOf(stack);

            if (!nbt.getBoolean(NBT_HAS_TARGET)) {
                player.displayClientMessage(
                        Component.translatable("message.trd.detonator.no_position")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                playSound(level, player, ModSounds.TOOL_TECH_BLEEP.get());
                return InteractionResultHolder.fail(stack);
            }

            BlockPos targetPos = new BlockPos(
                    nbt.getInt(NBT_POS_X), nbt.getInt(NBT_POS_Y), nbt.getInt(NBT_POS_Z));

            // Проверяем, загружен ли чанк
            if (!level.isLoaded(targetPos)) {
                player.displayClientMessage(
                        Component.translatable("message.trd.detonator.invalid")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                playSound(level, player, ModSounds.TOOL_TECH_BLEEP.get());
                return InteractionResultHolder.fail(stack);
            }

            BlockState state = level.getBlockState(targetPos);
            Block block = state.getBlock();

            if (block instanceof IDetonatable detonatable) {
                if (detonatable.onDetonate(level, targetPos, state, player)) {
                    player.displayClientMessage(
                            Component.translatable("message.trd.detonator.activated")
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                    playSound(level, player, ModSounds.TOOL_TECH_BLEEP.get());
                    return InteractionResultHolder.success(stack);
                }
            }

            player.displayClientMessage(
                    Component.translatable("message.trd.detonator.invalid")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            playSound(level, player, ModSounds.TOOL_TECH_BLEEP.get());
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.success(stack);
    }

    private static void playSound(Level level, Player player, SoundEvent sound) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, player.getSoundSource(), 1.0F, 1.0F);
    }
}
