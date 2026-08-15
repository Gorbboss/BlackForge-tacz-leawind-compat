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
 * Replaces Leawind's camera-side target with the second pick generated from
 * the player's forward plane. Hits between the camera and character, or
 * behind the character, cannot become the aim target.
 */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.CameraAgent", remap = false)
public abstract class LeawindForwardAimMixin {
    @Inject(method = "pick", at = @At("RETURN"), cancellable = true, require = 0)
    private void blackforge$useForwardPlanePick(
            double pickRange,
            CallbackInfoReturnable<HitResult> cir
    ) {
        Minecraft mc = Minecraft.getInstance();
        ForwardAimGuard.enforce(mc.getFrameTime());
        if (mc.hitResult != null) {
            cir.setReturnValue(mc.hitResult);
        }
    }
}
