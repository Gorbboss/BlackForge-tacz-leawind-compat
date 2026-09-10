package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.TacticalForwardAttack;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps TaCZ's aim state/animation but removes screen magnification in Passive. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczPassiveFovMixin {
    @Inject(method = "applyScopeMagnification", at = @At("HEAD"),
            cancellable = true, require = 0)
    private static void blackforge$disablePassiveScreenZoom(
            ViewportEvent.ComputeFov event,
            CallbackInfo ci
    ) {
        if (TacticalForwardAttack.isPassiveMode()) {
            ci.cancel();
        }
    }
}
