package com.blackforge.taczleawind.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporarily uses first-person camera behavior for TaCZ optics above 2.5x
 * without changing the perspective selected by the player.
 */
public final class ScopedFirstPersonController {
    private static final float FIRST_PERSON_MAGNIFICATION_THRESHOLD = 2.5F;
    private static final float FULL_AIM_PROGRESS = 0.999F;
    private static final double EXTRA_ADS_DURATION_FRACTION = 0.10D;
    private static int aimingTicks;
    private static int ticksAtFullAim = -1;
    private static final Map<Class<?>, Method> AIM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> ZOOM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> AIM_PROGRESS_METHODS = new ConcurrentHashMap<>();
    private static volatile boolean active;

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null
                || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            blackforge$reset();
            return;
        }

        ItemStack stack = player.getMainHandItem();
        boolean eligible = blackforge$isAiming(player)
                && blackforge$getAimingZoom(stack) > FIRST_PERSON_MAGNIFICATION_THRESHOLD;
        if (!eligible) {
            blackforge$reset();
            return;
        }

        aimingTicks++;
        float progress = blackforge$getAimingProgress(player);
        if (progress < FULL_AIM_PROGRESS) {
            active = false;
            return;
        }

        if (ticksAtFullAim < 0) {
            ticksAtFullAim = aimingTicks;
        }

        int extraTicks = Math.max(
                1,
                (int) Math.ceil(ticksAtFullAim * EXTRA_ADS_DURATION_FRACTION)
        );
        active = aimingTicks >= ticksAtFullAim + extraTicks;
    }

    public static boolean isActive() {
        return active;
    }

    private static void blackforge$reset() {
        active = false;
        aimingTicks = 0;
        ticksAtFullAim = -1;
    }

    private static boolean blackforge$isAiming(LocalPlayer player) {
        try {
            Method method = AIM_METHODS.computeIfAbsent(
                    player.getClass(),
                    type -> blackforge$findMethod(type, "isAim")
            );
            return method != null && Boolean.TRUE.equals(method.invoke(player));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static float blackforge$getAimingProgress(LocalPlayer player) {
        try {
            Method method = AIM_PROGRESS_METHODS.computeIfAbsent(
                    player.getClass(),
                    type -> blackforge$findMethod(
                            type,
                            "getClientAimingProgress",
                            float.class
                    )
            );
            if (method == null) return 0.0F;

            Object value = method.invoke(
                    player,
                    Minecraft.getInstance().getFrameTime()
            );
            return value instanceof Number number
                    ? number.floatValue()
                    : 0.0F;
        } catch (ReflectiveOperationException ignored) {
            return 0.0F;
        }
    }

    private static float blackforge$getAimingZoom(ItemStack stack) {
        if (stack.isEmpty()) return 1.0F;

        Object item = stack.getItem();
        try {
            Method method = ZOOM_METHODS.computeIfAbsent(
                    item.getClass(),
                    type -> blackforge$findMethod(
                            type,
                            "getAimingZoom",
                            ItemStack.class
                    )
            );
            if (method == null) return 1.0F;

            Object value = method.invoke(item, stack);
            return value instanceof Number number
                    ? number.floatValue()
                    : 1.0F;
        } catch (ReflectiveOperationException ignored) {
            return 1.0F;
        }
    }

    private static Method blackforge$findMethod(
            Class<?> type,
            String name,
            Class<?>... parameters
    ) {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private ScopedFirstPersonController() {}
}
