package com.blackforge.taczleawind.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Preserves the real camera crosshair hit before ForwardAimGuard rewrites it. */
public final class CameraCrosshairTarget {
    private static Vec3 location;

    public static void capture(HitResult hit) {
        location = hit == null ? null : hit.getLocation();
    }

    public static Vec3 location() {
        if (location != null) return location;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null) return mc.hitResult.getLocation();
        LocalPlayer player = mc.player;
        return player == null
                ? Vec3.ZERO
                : player.getEyePosition().add(player.getLookAngle().scale(16.0D));
    }

    private CameraCrosshairTarget() {}
}
