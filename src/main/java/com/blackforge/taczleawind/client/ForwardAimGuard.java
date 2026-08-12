package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ForwardAimGuard {
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

        /*
         * Leawind aims along the camera ray. When the camera is allowed inside
         * terrain, its initial pick can hit a block behind the player.
         *
         * Project the player's eye onto that same ray and begin a replacement
         * pick just beyond the perpendicular plane through the player. This
         * preserves the visible crosshair direction while making every block
         * on the camera side of the player irrelevant.
         */
        repickForwardOfPlayer(mc, player, partialTick);

        HitResult hit = mc.hitResult;
        if (hit == null) return;

        Vec3 origin = player.getEyePosition(partialTick);
        Vec3 targetDir = hit.getLocation().subtract(origin);
        Vec3 horizontalTarget = new Vec3(targetDir.x, 0.0D, targetDir.z);

        if (horizontalTarget.lengthSqr() < 1.0E-6D) return;
        horizontalTarget = horizontalTarget.normalize();

        float yaw = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad)).normalize();

        double dot = Math.max(-1.0D, Math.min(1.0D, forward.dot(horizontalTarget)));
        double angle = Math.toDegrees(Math.acos(dot));

        if (angle > ClientConfig.FORWARD_HEMISPHERE_DEGREES.get()) {
            double distance = Math.max(6.0D, targetDir.length());
            Vec3 legalPoint = origin.add(forward.scale(distance));

            mc.hitResult = BlockHitResult.miss(
                    legalPoint,
                    player.getDirection(),
                    BlockPos.containing(legalPoint)
            );
            mc.crosshairPickEntity = null;
        }
    }

    private static void repickForwardOfPlayer(
            Minecraft mc,
            LocalPlayer player,
            float partialTick
    ) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 rayDirection = new Vec3(camera.getLookVector()).normalize();
        Vec3 eye = player.getEyePosition(partialTick);

        double playerPlaneDistance = eye.subtract(cameraPos).dot(rayDirection);
        Vec3 start = cameraPos.add(
                rayDirection.scale(Math.max(0.0D, playerPlaneDistance) + 0.05D)
        );

        double pickRange = mc.gameMode.getPickRange();
        Vec3 end = start.add(rayDirection.scale(pickRange));

        BlockHitResult blockHit = player.pick(
                start.distanceTo(end),
                partialTick,
                false
        );

        /*
         * Entity#pick starts at the entity eye, not our forward plane, so use
         * the level clip performed by the camera ray for blocks below.
         */
        blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                start,
                end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));

        double blockDistanceSqr = start.distanceToSqr(blockHit.getLocation());
        AABB searchBox = new AABB(start, end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                searchBox,
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != player,
                blockDistanceSqr
        );

        if (entityHit != null
                && start.distanceToSqr(entityHit.getLocation()) < blockDistanceSqr) {
            mc.hitResult = entityHit;
            mc.crosshairPickEntity = entityHit.getEntity();
        } else {
            mc.hitResult = blockHit;
            mc.crosshairPickEntity = null;
        }
    }

    private ForwardAimGuard() {}
}
