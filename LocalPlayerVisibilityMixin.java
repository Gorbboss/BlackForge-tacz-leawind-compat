package com.blackforge.taczleawind.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the local player visible while a third-person camera is positioned
 * through terrain. Only the local player bypasses entity visibility culling.
 */
@Mixin(EntityRenderer.class)
public abstract class LocalPlayerVisibilityMixin<T extends Entity> {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void blackforge$keepLocalPlayerRendered(
            T entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player
                && !mc.options.getCameraType().isFirstPerson()
                && !cir.getReturnValue()) {
            cir.setReturnValue(true);
        }
    }
}
