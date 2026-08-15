package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bypasses Leawind 2.2.0's private camera-to-wall ray casts.
 *
 * Vanilla Camera#getMaxZoom is not involved after Leawind calculates and
 * installs its own camera position, so the vanilla-only hook cannot disable
 * Leawind's collision handling.
 */
@Pseudo
@Mixin(
        targets = "com.github.leawind.thirdperson.core.CameraAgent",
        remap = false
)
public abstract class LeawindCameraNoClipMixin {
    @Redirect(
            method = "updateTempCameraRotationPosition",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/world/level/Level;"
                                    + "clip(Lnet/minecraft/world/level/ClipContext;)"
                                    + "Lnet/minecraft/world/phys/BlockHitResult;",
                    remap = true
            ),
            require = 0
    )
    private BlockHitResult blackforge$bypassLeawindCameraCollision(
            Level level,
            ClipContext context
    ) {
        if (!ClientConfig.DISABLE_CAMERA_COLLISION.get()) {
            return level.clip(context);
        }

        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        Vec3 delta = to.subtract(from);

        return BlockHitResult.miss(
                to,
                Direction.getNearest(delta.x, delta.y, delta.z),
                BlockPos.containing(to)
        );
    }
}
