package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.OculusUniformBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the camera-cutaway uniforms to every Oculus shader program. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class OculusUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), require = 0)
    private static void blackforge$registerCutawayUniforms(
            @Coerce Object holder,
            @Coerce Object idMap,
            @Coerce Object directives,
            @Coerce Object notifier,
            CallbackInfo ci
    ) {
        OculusUniformBridge.register(holder);
    }
}
