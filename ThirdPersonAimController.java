package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps TaCZ's non-ADS firing rotation aligned with the rendered character.
 * TaCZ 1.20.1 creates the server shot from the player's pitch and yaw, so the
 * actual player rotation—not only the crosshair hit result—must be level.
 */
public final class ThirdPersonAimController {
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || !ClientConfig.FORWARD_ONLY_TARGETING.get()
                || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                || ScopedFirstPersonController.isAiming(player)) {
            return;
        }

        float bodyYaw = player.yBodyRot;
        player.setXRot(0.0F);
        player.xRotO = 0.0F;
        player.setYRot(bodyYaw);
        player.yRotO = bodyYaw;
    }

    private ThirdPersonAimController() {}
}
