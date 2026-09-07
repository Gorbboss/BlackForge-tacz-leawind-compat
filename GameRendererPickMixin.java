package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.ForwardAimGuard;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuilds the target after every vanilla/Leawind crosshair pick so terrain
 * behind the player cannot overwrite the forward target.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void blackforge$filterThirdPersonTarget(
            float partialTick,
            CallbackInfo ci
    ) {
        ForwardAimGuard.enforce(partialTick);
    }
}
