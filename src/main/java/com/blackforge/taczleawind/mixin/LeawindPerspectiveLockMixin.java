package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.ScopedFirstPersonController;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks Leawind's proximity-based temporary first person, while allowing
 * TaCZ optics above 2.5x to temporarily use first-person camera behavior.
 */
@Mixin(CameraType.class)
public abstract class LeawindPerspectiveLockMixin {
    @Inject(method = "isFirstPerson", at = @At("RETURN"), cancellable = true)
    private void blackforge$keepSelectedThirdPerson(
            CallbackInfoReturnable<Boolean> cir
    ) {
        if ((CameraType) (Object) this == CameraType.THIRD_PERSON_BACK) {
            cir.setReturnValue(ScopedFirstPersonController.isActive());
        }
    }
}
