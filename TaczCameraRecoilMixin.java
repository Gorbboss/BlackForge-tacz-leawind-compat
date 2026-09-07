package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczCameraRecoilMixin {
    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, require = 0)
    private static void blackforge$cameraRecoilToggle(
            ViewportEvent.ComputeCameraAngles event,
            CallbackInfo ci
    ) {
        if (!ClientConfig.CAMERA_RECOIL.get()) {
            ci.cancel();
        }
    }
}
