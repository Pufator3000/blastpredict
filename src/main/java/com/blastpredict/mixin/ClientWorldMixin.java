package com.blastpredict.mixin;

import com.blastpredict.BlastPredictor;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    /**
     * Both BlockUpdateS2CPacket and every entry of ChunkDeltaUpdateS2CPacket end up here, so one hook covers
     * whichever packet type the server uses. The server's state is applied as usual; we just forget our guess.
     */
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void blastpredict$onServerBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        BlastPredictor.onServerBlockUpdate(pos);
    }
}
