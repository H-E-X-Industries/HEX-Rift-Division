package com.trd.multiblock.industrial.steel_storage;

import com.trd.multiblock.system.MultiblockBlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стальное хранилище как предмет. Устанавливается и разрушается как обычный
 * мультиблок (Shift + ПКМ по грани ставит блок), но его ещё можно открыть
 * прямо из инвентаря — обычным ПКМ в воздухе.
 * <p>
 * Важно: {@code Item#use} ванилла зовёт и когда прицел наведён на блок — клиент
 * вызывает его, если взаимодействие с блоком ничего не израсходовало. Поэтому
 * предмет перехватывает только «смотрю в воздух» ({@link #isLookingAtBlock}),
 * иначе он бы ломал установку блока.
 */
public class SteelStorageItem extends MultiblockBlockItem {

    public SteelStorageItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // ПКМ по блоку: пропускаем дальше, там разберутся взаимодействие с блоком
        // и установка нового блока.
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || player.isShiftKeyDown() || player.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        }
        if (isLookingAtBlock(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        player.openMenu(new SteelStorageItemMenuProvider(player, stack));
        return InteractionResultHolder.consume(stack);
    }

    /** Наведён ли взгляд игрока на блок в пределах досягаемости. */
    private static boolean isLookingAtBlock(Player player) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Level level = player.level();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
