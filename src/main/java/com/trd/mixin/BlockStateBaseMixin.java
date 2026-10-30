package com.trd.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures vanilla floor and wall torches are recognized as ticking blocks (isRandomlyTicking).
 * This enables the random tick lifecycle required for torch extinguishing physics.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void trd$isTorchRandomlyTicking(CallbackInfoReturnable<Boolean> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (self.is(Blocks.TORCH) || self.is(Blocks.WALL_TORCH)) {
            cir.setReturnValue(true);
        }
    }
}
