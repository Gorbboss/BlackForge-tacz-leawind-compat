package com.blackforge.taczleawind.mixin;

import com.github.leawind.thirdperson.ThirdPersonStatus;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Leawind from temporarily impersonating first person while the user
 * has explicitly selected rear third person. Manual perspective changes remain
 * authoritative.
 */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.ThirdPersonEvents", remap = false)
public abstract class LeawindPerspectiveLockMixin {
    @Inject(method = "onClientTickStart", at = @At("RETURN"), require = 0)
    private static void blackforge$keepSelectedThirdPerson(CallbackInfo ci) {
        if (Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            ThirdPersonStatus.isPerspectiveInverted = false;
        }
    }
}
