package com.trd.item.industrial.rotation;

import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class RotorItem extends Item {
    public RotorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof ShaftBlock) {
            if (level.getBlockEntity(pos) instanceof ShaftBlockEntity shaftBE) {
                if (!shaftBE.hasCentralAttachment()) {
                    // Запрет установки ротора на вал T-образного узла конической шестерни
                    if (shaftBE.isBevelTJunctionShaft()) {
                        if (!level.isClientSide && context.getPlayer() != null) {
                            context.getPlayer().displayClientMessage(
                                    Component.translatable("message.trd.bevel_tjunction_blocked")
                                            .withStyle(ChatFormatting.RED), true);
                        }
                        return InteractionResult.FAIL;
                    }
                    if (!level.isClientSide) {
                        ItemStack rotorStack = context.getItemInHand().copy();
                        rotorStack.setCount(1);
                        shaftBE.setAttachedRotor(rotorStack);
                        
                        context.getItemInHand().shrink(1);
                        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 1.0F);

                        // Trigger recalculation
                        KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
                        var net = manager.getNetworkFor(pos);
                        if (net != null) {
                            net.requestRecalculation();
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.trd.machine.rotor.desc").withStyle(ChatFormatting.GRAY));
    }
}
