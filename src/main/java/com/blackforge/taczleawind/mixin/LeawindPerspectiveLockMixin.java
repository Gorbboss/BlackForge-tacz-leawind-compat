package com.blackforge.taczleawind.mixin;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the explicitly selected rear-third-person perspective from being
 * reported as temporary first person by Leawind.
 */
@Mixin(CameraType.class)
public abstract class LeawindPerspectiveLockMixin {
    @Inject(method = "isFirstPerson", at = @At("RETURN"), cancellable = true)
    private void blackforge$keepSelectedThirdPerson(
            CallbackInfoReturnable<Boolean> cir
    ) {
        if ((CameraType) (Object) this == CameraType.THIRD_PERSON_BACK) {
            cir.setReturnValue(false);
        }
    }
}
