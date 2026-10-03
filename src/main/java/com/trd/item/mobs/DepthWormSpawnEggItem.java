package com.trd.item.mobs;

import com.trd.entity.ModEntities;
import com.trd.entity.mobs.depth_worm.DepthWormEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class DepthWormSpawnEggItem extends Item {
    public DepthWormSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            DepthWormEntity worm = ModEntities.DEPTH_WORM.get().create(level);
            if (worm != null) {
                worm.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                level.addFreshEntity(worm);
                if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }
}
