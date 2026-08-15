package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Filters the normal camera/crosshair pick without replacing Leawind's own
 * completed CameraAgent pick. The camera-to-player segment defines the part
 * of the crosshair ray that is not allowed to register.
 */
public final class ForwardAimGuard {
    private static final double FORWARD_PLANE_MARGIN = 0.05D;

    public static void enforce() {
        enforce(Minecraft.getInstance().getFrameTime());
    }

    public static void enforce(float partialTick) {
        if (!ClientConfig.FORWARD_ONLY_TARGETING.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null
                || mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 crosshairDirection = new Vec3(camera.getLookVector()).normalize();
        Vec3 playerEye = player.getEyePosition(partialTick);

        // The second camera-to-player ray supplies the forward cutoff plane.
        double cameraToPlayerAlongCrosshair =
                playerEye.subtract(cameraPos).dot(crosshairDirection);
        double acceptedStartDistance =
                Math.max(0.0D, cameraToPlayerAlongCrosshair)
                        + FORWARD_PLANE_MARGIN;
        Vec3 acceptedStart = cameraPos.add(
                crosshairDirection.scale(acceptedStartDistance)
        );
        Vec3 end = acceptedStart.add(
                crosshairDirection.scale(mc.gameMode.getPickRange())
        );

        HitResult blockHit = mc.level.clip(new ClipContext(
                acceptedStart,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        double blockDistanceSqr =
                acceptedStart.distanceToSqr(blockHit.getLocation());
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                acceptedStart,
                end,
                new AABB(acceptedStart, end).inflate(1.0D),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != player,
                blockDistanceSqr
        );

        if (entityHit != null
                && acceptedStart.distanceToSqr(entityHit.getLocation())
                        < blockDistanceSqr) {
            mc.hitResult = entityHit;
            mc.crosshairPickEntity = entityHit.getEntity();
        } else {
            mc.hitResult = blockHit;
            mc.crosshairPickEntity = null;
        }
    }

    private ForwardAimGuard() {}
}
