package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.TacticalForwardAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops Leawind's attack-key camera rotation only for Tactical forward attacks. */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.EntityAgent", remap = false)
public abstract class LeawindTacticalAttackMixin {
    @Inject(method = "isInteracting()Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void blackforge$keepTacticalAttackForward(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && TacticalForwardAttack.shouldSuppressLeawindInteraction()) {
            cir.setReturnValue(false);
        }
    }
}
