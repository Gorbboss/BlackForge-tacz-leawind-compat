package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Leawind's own pick pipeline and range, but moves the accepted start of
 * both block and entity rays just beyond the player plane. Camera-side hits
 * cannot become CameraAgent.hitResult, while the final world hit remains the
 * value Leawind uses to rotate the character.
 */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.CameraAgent", remap = false)
public abstract class LeawindForwardPickMixin {
    private static final double FORWARD_PLANE_MARGIN = 0.05D;

    @Shadow
    public abstract Camera getRawCamera();

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$pickBlockForwardOfPlayer(
            double pickRange,
            ClipContext.Block blockShape,
            ClipContext.Fluid fluidShape,
            CallbackInfoReturnable<BlockHitResult> cir
    ) {
        if (!blackforge$shouldFilter()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = getRawCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 direction = new Vec3(camera.getLookVector()).normalize();
        Vec3 originalEnd = cameraPos.add(direction.scale(pickRange));
        Vec3 start = blackforge$acceptedStart(
                cameraPos,
                direction,
                player.getEyePosition(mc.getFrameTime())
        );

        if (start.distanceToSqr(cameraPos) >= pickRange * pickRange) {
            cir.setReturnValue(BlockHitResult.miss(
                    originalEnd,
                    Direction.getNearest(direction.x, direction.y, direction.z),
                    BlockPos.containing(originalEnd)
            ));
            return;
        }

        cir.setReturnValue(mc.level.clip(new ClipContext(
                start,
                originalEnd,
                blockShape,
                fluidShape,
                player
        )));
    }

    @Inject(method = "pickEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$pickEntityForwardOfPlayer(
            double pickRange,
            CallbackInfoReturnable<EntityHitResult> cir
    ) {
        if (!blackforge$shouldFilter()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Camera camera = getRawCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 direction = new Vec3(camera.getLookVector()).normalize();
        Vec3 originalEnd = cameraPos.add(direction.scale(pickRange));
        Vec3 start = blackforge$acceptedStart(
                cameraPos,
                direction,
                player.getEyePosition(mc.getFrameTime())
        );

        double remainingDistanceSqr = start.distanceToSqr(originalEnd);
        if (remainingDistanceSqr <= 1.0E-8D) {
            cir.setReturnValue(null);
            return;
        }

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                originalEnd,
                new AABB(start, originalEnd),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != player,
                remainingDistanceSqr
        );
        cir.setReturnValue(hit);
    }

    private static Vec3 blackforge$acceptedStart(
            Vec3 cameraPos,
            Vec3 direction,
            Vec3 playerEye
    ) {
        double playerPlaneDistance =
                playerEye.subtract(cameraPos).dot(direction);
        return cameraPos.add(direction.scale(
                Math.max(0.0D, playerPlaneDistance)
                        + FORWARD_PLANE_MARGIN
        ));
    }

    private static boolean blackforge$shouldFilter() {
        Minecraft mc = Minecraft.getInstance();
        return ClientConfig.FORWARD_ONLY_TARGETING.get()
                && mc.player != null
                && mc.level != null
                && !mc.options.getCameraType().isFirstPerson();
    }
}
