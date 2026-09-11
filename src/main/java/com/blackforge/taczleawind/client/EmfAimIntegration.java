package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.BlackForgeCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/** Current-EMF replacement for the old TaCZ EMF Aim Variable bridge. */
public final class EmfAimIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;

    public static void register() {
        if (registered) return;
        try {
            Class<?> api = Class.forName(
                    "traben.entity_model_features.EMFAnimationApi"
            );
            Method registration = api.getMethod(
                    "registerSingletonAnimationVariable",
                    String.class,
                    String.class,
                    String.class,
                    BooleanSupplier.class
            );
            registration.invoke(
                    null,
                    BlackForgeCompat.MOD_ID,
                    "tacz_is_aiming",
                    "True during TaCZ ADS or BlackForge Tactical aim focus",
                    (BooleanSupplier) EmfAimIntegration::isAnimationAiming
            );
            registered = true;
            LOGGER.info("[BlackForge TLC] Registered current-EMF tacz_is_aiming bridge");
        } catch (ClassNotFoundException ignored) {
            // EMF is optional.
        } catch (Throwable failure) {
            LOGGER.error("[BlackForge TLC] Failed to register tacz_is_aiming", failure);
        }
    }

    private static boolean isAnimationAiming() {
        try {
            Object rendered = Class.forName(
                            "traben.entity_model_features.EMFAnimationApi"
                    ).getMethod("getCurrentEntity").invoke(null);
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || rendered != player
                    || !TacticalForwardAttack.isEligible(player.getMainHandItem())) {
                return false;
            }

            boolean tacticalFocus =
                    mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                            && TacticalForwardAttack.movementTacticalEnabled();
            return TacticalForwardAttack.isAdsRequested() || tacticalFocus;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EmfAimIntegration() {}
}
