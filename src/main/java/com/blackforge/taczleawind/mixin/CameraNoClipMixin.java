package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraNoClipMixin {
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$disableCameraCollision(
            double requestedDistance,
            CallbackInfoReturnable<Double> cir
    ) {
        if (ClientConfig.DISABLE_CAMERA_COLLISION.get()) {
            cir.setReturnValue(requestedDistance);
        }
    }
}
