package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.ForwardAimGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leawind stores a separate camera pick result which it uses to turn the
 * player/gun toward the crosshair. Replace that result at its source so a wall
 * behind the player can never become the gun's aim target.
 */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.CameraAgent", remap = false)
public abstract class LeawindForwardAimMixin {
    @Inject(method = "pick", at = @At("RETURN"), cancellable = true, require = 0)
    private void blackforge$discardCameraSideHits(
            double pickRange,
            CallbackInfoReturnable<HitResult> cir
    ) {
        HitResult forwardHit = ForwardAimGuard.pickForwardOfPlayer(
                pickRange,
                Minecraft.getInstance().getFrameTime()
        );
        if (forwardHit != null) {
            cir.setReturnValue(forwardHit);
        }
    }
}
