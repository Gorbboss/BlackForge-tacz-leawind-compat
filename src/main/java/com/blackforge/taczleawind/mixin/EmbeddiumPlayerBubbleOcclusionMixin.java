package com.blackforge.taczleawind.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Embeddium's occlusion traversal open in the player-centered 3x3x3
 * section cube. This lets a no-clip camera escape a solid starting section
 * without disabling frustum, distance, or terrain occlusion outside the cube.
 */
@Pseudo
@Mixin(
        targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSection",
        remap = false
)
public abstract class EmbeddiumPlayerBubbleOcclusionMixin {
    @Shadow @Final private int chunkX;
    @Shadow @Final private int chunkY;
    @Shadow @Final private int chunkZ;

    @Inject(method = "getVisibilityData", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void blackforge$openPlayerSectionBubble(
            CallbackInfoReturnable<Long> cir
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        int playerSectionX = SectionPos.blockToSectionCoord(mc.player.getBlockX());
        int playerSectionY = SectionPos.blockToSectionCoord(mc.player.getBlockY());
        int playerSectionZ = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        if (Math.abs(this.chunkX - playerSectionX) <= 1
                && Math.abs(this.chunkY - playerSectionY) <= 1
                && Math.abs(this.chunkZ - playerSectionZ) <= 1) {
            // Embeddium encodes the six-by-six direction connection matrix in
            // a long. All set bits mean every face connects to every other.
            cir.setReturnValue(-1L);
        }
    }
}
