package com.blackforge.taczleawind.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaCZ third-person crosshair compatibility.
 *
 * Restores BOTH pieces of the original BlackForge test logic:
 *
 * 1. Allow TaCZ's crosshair render path while using third-person.
 * 2. Override TaCZ aiming-progress checks to 0 while in third-person so
 *    the crosshair does not disappear simply because the player is aiming.
 *
 * This implementation intentionally uses only standard Sponge Mixin.
 * MixinExtras is NOT required.
 */
@Pseudo
@Mixin(
        targets = "com.tacz.guns.client.event.RenderCrosshairEvent",
        remap = false
)
public abstract class TaczCrosshairMixin {

    /**
     * TaCZ normally checks CameraType#isFirstPerson() before allowing its
     * crosshair render path.
     *
     * Our compat layer treats third-person as valid for this particular
     * crosshair-render check.
     */
    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean blackforge$allowThirdPersonCrosshair(
            CameraType cameraType
    ) {
        return true;
    }

    /*
     * Cache reflection lookups because the aiming-progress method may be
     * queried every rendered frame.
     *
     * We avoid a direct compile-time dependency on TaCZ's interface here so
     * the BlackForge project can continue compiling against Forge alone.
     */
    private static final Map<Class<?>, Method> BLACKFORGE_AIM_METHOD_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Restored aiming-progress hook.
     *
     * TaCZ uses getClientAimingProgress(...) during crosshair rendering.
     * In third-person, return 0 for this particular UI check so TaCZ does not
     * hide its crosshair while ADS/aiming.
     *
     * In first-person, invoke TaCZ's original method and return the real value.
     */
    @Redirect(
            method = {
                    "onRenderOverlay",
                    "lambda$onRenderOverlay$0"
            },
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tacz/guns/api/client/gameplay/IClientPlayerGunOperator;"
                                    + "getClientAimingProgress(F)F"
            ),
            require = 0
    )
    private static float blackforge$keepCrosshairVisibleWhileAiming(
            @Coerce Object gunOperator,
            float partialTick
    ) {
        Minecraft mc = Minecraft.getInstance();

        if (!mc.options.getCameraType().isFirstPerson()) {
            return 0.0F;
        }

        return blackforge$invokeOriginalAimingProgress(
                gunOperator,
                partialTick
        );
    }

    /**
     * Calls TaCZ's actual getClientAimingProgress(float) method when we are
     * not overriding the value.
     */
    private static float blackforge$invokeOriginalAimingProgress(
            Object gunOperator,
            float partialTick
    ) {
        if (gunOperator == null) {
            return 0.0F;
        }

        try {
            Method method = BLACKFORGE_AIM_METHOD_CACHE.computeIfAbsent(
                    gunOperator.getClass(),
                    clazz -> {
                        try {
                            Method found = clazz.getMethod(
                                    "getClientAimingProgress",
                                    float.class
                            );
                            found.setAccessible(true);
                            return found;
                        } catch (ReflectiveOperationException e) {
                            return null;
                        }
                    }
            );

            if (method == null) {
                return 0.0F;
            }

            Object result = method.invoke(
                    gunOperator,
                    partialTick
            );

            if (result instanceof Number number) {
                return number.floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // Version-tolerant fallback: if TaCZ changes the method at runtime,
            // do not crash the client over a crosshair UI compatibility hook.
        }

        return 0.0F;
    }
}
