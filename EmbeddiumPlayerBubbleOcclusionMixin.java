package com.blackforge.taczleawind.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Embeddium's occlusion traversal open only in the render sections
 * crossed by the player-to-camera segment. With the 12-block camera limit,
 * that is normally no more than a dynamic 2x2x2 group (eight sections),
 * instead of the old fixed player-centered 3x3x3 group (27 sections).
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

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 playerPos = mc.player.getEyePosition(1.0F);

        int cameraSectionX = SectionPos.blockToSectionCoord((int) Math.floor(cameraPos.x));
        int cameraSectionY = SectionPos.blockToSectionCoord((int) Math.floor(cameraPos.y));
        int cameraSectionZ = SectionPos.blockToSectionCoord((int) Math.floor(cameraPos.z));
        int playerSectionX = SectionPos.blockToSectionCoord((int) Math.floor(playerPos.x));
        int playerSectionY = SectionPos.blockToSectionCoord((int) Math.floor(playerPos.y));
        int playerSectionZ = SectionPos.blockToSectionCoord((int) Math.floor(playerPos.z));

        int minX = Math.min(playerSectionX, cameraSectionX);
        int minY = Math.min(playerSectionY, cameraSectionY);
        int minZ = Math.min(playerSectionZ, cameraSectionZ);
        int maxX = Math.max(playerSectionX, cameraSectionX);
        int maxY = Math.max(playerSectionY, cameraSectionY);
        int maxZ = Math.max(playerSectionZ, cameraSectionZ);

        if (this.chunkX >= minX && this.chunkX <= maxX
                && this.chunkY >= minY && this.chunkY <= maxY
                && this.chunkZ >= minZ && this.chunkZ <= maxZ) {
            // Embeddium encodes the six-by-six direction connection matrix in
            // a long. All set bits mean every face connects to every other.
            cir.setReturnValue(-1L);
        }
    }
}
