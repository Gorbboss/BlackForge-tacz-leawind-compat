package com.blackforge.taczleawind.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Hard-limits Leawind 2.2.0 to an observed 12-block camera distance. */
final class LeawindZoomIntegration {
    /*
     * Leawind's camera_distance_max is a distance factor, not a block count.
     * With the player's size multiplier used by Leawind, 6.0 is the 12-block
     * ceiling (a value of 8.0 produced about 16 blocks).
     */
    private static final double LEAWIND_MAX_DISTANCE_FACTOR = 6.0D;
    private static Object configuredInstance;
    private static boolean unavailable;

    private LeawindZoomIntegration() {}

    static void update() {
        if (unavailable) return;

        try {
            Class<?> thirdPerson = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            Method getConfig = thirdPerson.getMethod("getConfig");
            Object config = getConfig.invoke(null);
            if (config == null) return;

            Field maximum = config.getClass().getField("camera_distance_max");
            Field normalDistance = config.getClass().getField("normal_max_distance");
            Field aimingDistance = config.getClass().getField("aiming_max_distance");

            boolean needsUpdate = config != configuredInstance
                    || Double.compare(maximum.getDouble(config), LEAWIND_MAX_DISTANCE_FACTOR) != 0
                    || normalDistance.getDouble(config) > LEAWIND_MAX_DISTANCE_FACTOR
                    || aimingDistance.getDouble(config) > LEAWIND_MAX_DISTANCE_FACTOR;
            if (!needsUpdate) return;

            maximum.setDouble(config, LEAWIND_MAX_DISTANCE_FACTOR);
            normalDistance.setDouble(config, Math.min(
                    normalDistance.getDouble(config),
                    LEAWIND_MAX_DISTANCE_FACTOR
            ));
            aimingDistance.setDouble(config, Math.min(
                    aimingDistance.getDouble(config),
                    LEAWIND_MAX_DISTANCE_FACTOR
            ));

            Method rebuildDistances = config.getClass().getMethod("updateDistancesMonoList");
            rebuildDistances.invoke(config);

            configuredInstance = config;
        } catch (ClassNotFoundException exception) {
            unavailable = true;
        } catch (ReflectiveOperationException ignored) {
            // Keep retrying: Leawind can replace its config object after reload.
        }
    }
}
