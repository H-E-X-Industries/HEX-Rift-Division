package com.trd.item.tools;

import com.trd.block.entity.industrial.casting.CastingPotBlockEntity;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.multiblock.industrial.smelter.SmelterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class PokerItem extends Item {

    public PokerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();

        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        ItemStack poker = player.getItemInHand(hand);

        if (be instanceof CastingPotBlockEntity pot) {
            return handleCastingPot(level, pos, player, hand, pot, poker);
        }

        if (be instanceof SmelterBlockEntity smelter) {
            return handleSmelter(level, pos, player, smelter, poker);
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleCastingPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                               CastingPotBlockEntity pot, ItemStack poker) {
        if (player.isShiftKeyDown()) {
            return dumpCastingPotContents(level, pos, player, pot, poker);
        }

        if (pot.hasSlag()) {
            return extractSlagFromPot(level, pos, player, hand, pot, poker);
        }

        if (!pot.getOutputItem().isEmpty()) {
            return extractHotItemFromPot(level, pos, player, hand, pot, poker);
        }

        player.displayClientMessage(Component.translatable("message.trd.poker.pot_empty"), true);
        return InteractionResult.PASS;
    }

    private InteractionResult dumpCastingPotContents(Level level, BlockPos pos, Player player,
                                                     CastingPotBlockEntity pot, ItemStack poker) {
        boolean dumped = false;

        if (pot.hasSlag()) {
            ItemStack slag = pot.extractSlag();
            if (!slag.isEmpty()) {
                ensureSlagIsHot(slag);
                popResourceSafe(level, pos, slag);
                dumped = true;
            }
        }

        if (pot.getStoredUnits() > 0 && pot.getCurrentMetal() != null) {
            ItemStack slag = SlagItem.createSlag(pot.getCurrentMetal(), pot.getStoredUnits());
            ensureSlagIsHot(slag);
            popResourceSafe(level, pos, slag);
            pot.clearMetal();
            dumped = true;
        }

        if (!pot.getOutputItem().isEmpty()) {
            ItemStack item = pot.takeOutput();
            popResourceSafe(level, pos, item);
            dumped = true;
        }

        if (dumped) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
            damagePoker(poker, player, 1);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private InteractionResult extractSlagFromPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                 CastingPotBlockEntity pot, ItemStack poker) {
        ItemStack slag = pot.extractSlag();
        if (slag.isEmpty()) return InteractionResult.PASS;

        ensureSlagIsHot(slag);

        if (!player.getInventory().add(slag)) {
            player.drop(slag, false);
        }

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        damagePoker(poker, player, 1);
        return InteractionResult.CONSUME;
    }

    private InteractionResult extractHotItemFromPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                    CastingPotBlockEntity pot, ItemStack poker) {
        ItemStack item = pot.takeOutput();
        if (item.isEmpty()) return InteractionResult.PASS;

        if (HotItemHandler.isHot(item)) {
            int temp = HotItemHandler.getTemperature(item);
            player.displayClientMessage(Component.translatable("message.trd.poker.hot_item_extracted", temp), true);
        }

        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        damagePoker(poker, player, 1);
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleSmelter(Level level, BlockPos pos, Player player,
                                            SmelterBlockEntity smelter, ItemStack poker) {
        if (player.isShiftKeyDown()) {
            return dumpSmelterContents(level, pos, player, smelter, poker);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult dumpSmelterContents(Level level, BlockPos pos, Player player,
                                                  SmelterBlockEntity smelter, ItemStack poker) {
        if (!smelter.hasMetal()) {
            player.displayClientMessage(Component.translatable("message.trd.poker.smelter_empty"), true);
            return InteractionResult.PASS;
        }

        List<ItemStack> slagItems = smelter.dumpMetalAsSlag();

        for (ItemStack slag : slagItems) {
            ensureSlagIsHot(slag);
            popResourceSafe(level, pos, slag);
        }

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
        damagePoker(poker, player, Math.max(1, slagItems.size()));

        player.displayClientMessage(Component.translatable("message.trd.poker.slag_dumped", slagItems.size()), true);

        return InteractionResult.CONSUME;
    }

    private void ensureSlagIsHot(ItemStack slag) {
        if (HotItemHandler.isHot(slag)) return;

        int meltingPoint = 1000;
        CompoundTag tag = slag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SlagItem.TAG_MELTING_POINT)) {
            meltingPoint = tag.getInt(SlagItem.TAG_MELTING_POINT);
        } else if (tag.contains("MeltingPoint")) {
            meltingPoint = tag.getInt("MeltingPoint");
        }

        HotItemHandler.setHot(slag, meltingPoint, false);
    }

    private void popResourceSafe(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
    }

    private void damagePoker(ItemStack poker, Player player, int amount) {
        if (player.getAbilities().instabuild) return;
        poker.hurtAndBreak(amount, player, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return true;
    }
}
