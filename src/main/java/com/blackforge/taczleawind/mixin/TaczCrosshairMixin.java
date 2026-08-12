package com.blackforge.taczleawind.mixin;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TaCZ third-person crosshair compatibility without MixinExtras.
 *
 * v0.1.0 used @ModifyExpressionValue from MixinExtras, but MixinExtras was
 * not included as a dependency. This uses standard Sponge Mixin instead.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.RenderCrosshairEvent", remap = false)
public abstract class TaczCrosshairMixin {

    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean blackforge$allowThirdPersonCrosshair(CameraType cameraType) {
        return true;
    }
}
