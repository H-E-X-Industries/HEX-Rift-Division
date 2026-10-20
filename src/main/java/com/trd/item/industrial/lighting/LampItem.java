package com.trd.item.industrial.lighting;

import com.trd.api.components.LampComponent;
import com.trd.api.components.ModDataComponents;
import com.trd.block.basic.industrial.lighting.ElectricLampBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public class LampItem extends BlockItem {

    public LampItem(ElectricLampBlock block, Properties properties) {
        super(block, properties);
    }

    public ElectricLampBlock getLampBlock() {
        return (ElectricLampBlock) this.getBlock();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Забороняємо встановлення «голих» ламп безпосередньо на звичайні поверхні.
        // Лампи мають вкручуватися виключно у патрон (EmptySocketBlock).
        // При взаємодії з EmptySocketBlock спочатку спрацьовує useItemOn самого блоку патрона.
        return InteractionResult.PASS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        LampComponent comp = stack.get(ModDataComponents.LAMP_DATA.get());
        if (comp != null) {
            return comp.isBroken() || comp.durability() < 1.0f;
        }
        return false;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        LampComponent comp = stack.get(ModDataComponents.LAMP_DATA.get());
        if (comp != null) {
            if (comp.isBroken()) {
                return 0;
            }
            return Math.round(13.0f * Math.clamp(comp.durability(), 0.0f, 1.0f));
        }
        return 13;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        LampComponent comp = stack.get(ModDataComponents.LAMP_DATA.get());
        if (comp != null) {
            if (comp.isBroken()) {
                return 0xFF2222; // Червоний для перегорілої лампи
            }
            float ratio = Math.clamp(comp.durability(), 0.0f, 1.0f);
            return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F); // Градієнт від зеленого до червоного
        }
        return 0x55FF55;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        LampComponent comp = stack.get(ModDataComponents.LAMP_DATA.get());
        if (comp != null) {
            if (comp.isBroken()) {
                tooltipComponents.add(Component.translatable("tooltip.trd.lamp.broken").withStyle(ChatFormatting.RED));
            } else {
                int percent = comp.getDurabilityPercent();
                ChatFormatting color = percent > 50 ? ChatFormatting.GREEN : (percent > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);
                tooltipComponents.add(Component.translatable("tooltip.trd.lamp.durability", percent + "%").withStyle(color));
            }
        }
        tooltipComponents.add(Component.translatable("tooltip.trd.lamp.hint").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
