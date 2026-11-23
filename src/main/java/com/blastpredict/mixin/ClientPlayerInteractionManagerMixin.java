package com.blastpredict.mixin;

import com.blastpredict.BlastPredictor;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    /** Runs before the block is removed locally, so the original state and the crosshair face are still available. */
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void blastpredict$captureBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlastPredictor.captureBreak(pos);
    }

    /** Runs after the local break; only predict the rest of the Blast if the main block really broke. */
    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void blastpredict$confirmBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlastPredictor.confirmBreak(pos, Boolean.TRUE.equals(cir.getReturnValue()));
    }
}
