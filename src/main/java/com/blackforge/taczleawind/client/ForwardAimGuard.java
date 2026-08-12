package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ForwardAimGuard {
    public static void enforce() {
        if (!ClientConfig.FORWARD_ONLY_TARGETING.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        HitResult hit = mc.hitResult;

        if (player == null || hit == null || mc.options.getCameraType().isFirstPerson()) return;

        Vec3 origin = player.getEyePosition(1.0F);
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

            // Minecraft 1.20.1 does not provide HitResult.miss(...).
            // BlockHitResult.miss(...) is the correct vanilla factory.
            mc.hitResult = BlockHitResult.miss(
                    legalPoint,
                    player.getDirection(),
                    BlockPos.containing(legalPoint)
            );

            mc.crosshairPickEntity = null;
        }
    }

    private ForwardAimGuard() {}
}
