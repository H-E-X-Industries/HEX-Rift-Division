package com.trd.item.weapons.turrets;

import com.trd.entity.ModEntities;
import com.trd.entity.weapons.turrets.TurretLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class PigTurretPlacerItem extends Item {

    public PigTurretPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        if (!level.isClientSide) {
            // --- Спавним свинью с седлом ---
            Pig pig = new Pig(net.minecraft.world.entity.EntityType.PIG, level);
            pig.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            pig.equipSaddle(net.minecraft.sounds.SoundSource.NEUTRAL);
            pig.setPersistenceRequired();
            level.addFreshEntity(pig);

            // --- Спавним турель (поднята на 0.25 = 4 пикселя) ---
            TurretLightEntity turret = ModEntities.TURRET_LIGHT.get().create(level);
            if (turret != null) {
                turret.moveTo(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 0, 0);

                Player player = context.getPlayer();
                if (player != null) {
                    turret.setOwner(player);
                }

                turret.setLifetime(3600);
                turret.setAmmo(250);
                turret.setPigMode(true);
                level.addFreshEntity(turret);

                turret.startRiding(pig, true);
            }

            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}