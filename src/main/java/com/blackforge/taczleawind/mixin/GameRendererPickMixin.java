package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.ForwardAimGuard;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the forward-only filter after every vanilla/Leawind crosshair pick.
 *
 * A client-tick-only check is too early: GameRenderer#pick is also called
 * during level rendering and immediately before Leawind interactions.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void blackforge$filterThirdPersonTarget(
            float partialTick,
            CallbackInfo ci
    ) {
        ForwardAimGuard.enforce();
    }
}
