package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.HiddenBlockManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paintings and item frames are entities, not chunk block models. Hide them
 * only when their bounds intersect the currently active obstruction cone.
 */
@Mixin(LevelRenderer.class)
public abstract class DecorationEntityCutawayMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void blackforge$hideObstructingDecoration(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            CallbackInfo ci
    ) {
        if (HiddenBlockManager.shouldHideEntity(entity)) {
            ci.cancel();
        }
    }
}
