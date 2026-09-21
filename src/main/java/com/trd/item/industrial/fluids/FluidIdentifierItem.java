package com.trd.item.industrial.fluids;

import com.trd.block.basic.industrial.fluids.FluidPipeBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FluidIdentifierItem extends Item {
    public FluidIdentifierItem(Properties pProperties) {
        super(pProperties.stacksTo(1));
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).getBlock() instanceof FluidPipeBlock;
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        if (pContext.getLevel().getBlockState(pContext.getClickedPos()).getBlock() instanceof FluidPipeBlock) {
            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    
    private void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new com.trd.client.overlay.gui.GUIFluidIdentifier(stack));
    }

    public static String getSelectedFluid(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("SelectedFluid") ? tag.getString("SelectedFluid") : "none";
    }

    public static List<String> getRecentFluids(ItemStack stack) {
        List<String> list = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("RecentFluids")) {
            ListTag listTag = tag.getList("RecentFluids", Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getString(i));
            }
        }
        return list;
    }

    public static List<String> getFavorites(ItemStack stack) {
        List<String> list = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("Favorites")) {
            ListTag listTag = tag.getList("Favorites", Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getString(i));
            }
        }
        return list;
    }

    private void cycleFluid(ItemStack stack, int direction) {
        List<String> history = getRecentFluids(stack);
        if (history.isEmpty()) return;

        String current = getSelectedFluid(stack);
        int currentIndex = history.indexOf(current);

        int newIndex = (currentIndex + direction) % history.size();
        if (newIndex < 0) newIndex = history.size() - 1;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("SelectedFluid", history.get(newIndex));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private int getFluidColor(String fluidName) {
        if (fluidName.contains("lava")) return 0xe64306;
        if (fluidName.equals("none")) return 0xFFFFFF;

        try {
            ResourceLocation id = ResourceLocation.tryParse(fluidName);
            if (id != null) {
                Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                if (fluid != null) {
                    return IClientFluidTypeExtensions.of(fluid.getFluidType())
                            .getTintColor(new FluidStack(fluid, 1000)) | 0xFF000000;
                }
            }
        } catch (Exception e) {
        }
        return 0xFFFFFF;
    }

    public Component getFluidDisplayName(String fluidName) {
        if (fluidName.equals("none")) {
            return Component.translatable("tooltip.trd.no_fluid").withStyle(ChatFormatting.GRAY);
        }

        String dropId = fluidName.replace(":", ":fluid_drop_").replace("minecraft:fluid_drop_", "trd:fluid_drop_");
        ResourceLocation rl = ResourceLocation.tryParse(dropId);
        if (rl != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item.getDescription().copy().withStyle(style -> style.withColor(getFluidColor(fluidName)));
            }
        }

        return Component.translatable("fluid." + fluidName.replace(":", "."))
                .withStyle(style -> style.withColor(getFluidColor(fluidName)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) openScreen(stack);
        } else {
            if (!level.isClientSide) {
                cycleFluid(stack, 1);
                String current = getSelectedFluid(stack);
                player.displayClientMessage(Component.translatable("message.trd.selected_fluid")
                        .append(": ").append(getFluidDisplayName(current)), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String fluid = getSelectedFluid(stack);
        tooltip.add(Component.translatable("tooltip.trd.fluid_identifier.fluid").withStyle(ChatFormatting.GOLD).append(getFluidDisplayName(fluid)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
