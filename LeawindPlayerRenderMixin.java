package com.blackforge.taczleawind.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leawind normally hides the camera entity when camera distance or opacity is
 * below its threshold. Keep the local player rendered in selected rear third
 * person, even when the camera occupies the first-person position.
 */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.ThirdPersonStatus", remap = false)
public abstract class LeawindPlayerRenderMixin {
    @Inject(method = "shouldRenderCameraEntity", at = @At("RETURN"), cancellable = true, require = 0)
    private static void blackforge$alwaysRenderLocalPlayer(
            float partialTick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            cir.setReturnValue(true);
        }
    }
}
