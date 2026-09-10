package com.blackforge.taczleawind.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Stance-sensitive hard limit for Leawind 2.2.0 camera distance. */
final class LeawindZoomIntegration {
    /*
     * Leawind's camera_distance_max is a distance factor, not a block count.
     * With the player's size multiplier used by Leawind, 6.0 is the 12-block
     * ceiling (a value of 8.0 produced about 16 blocks).
     */
    private static final double PASSIVE_MAX_DISTANCE_FACTOR = 6.0D;
    private static final double TACTICAL_MAX_DISTANCE_FACTOR = 4.5D;
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

            double targetMaximum = TacticalForwardAttack.movementTacticalEnabled()
                    ? TACTICAL_MAX_DISTANCE_FACTOR
                    : PASSIVE_MAX_DISTANCE_FACTOR;

            boolean needsUpdate = config != configuredInstance
                    || Double.compare(maximum.getDouble(config), targetMaximum) != 0
                    || Double.compare(normalDistance.getDouble(config), targetMaximum) != 0
                    || Double.compare(aimingDistance.getDouble(config), targetMaximum) != 0;
            if (!needsUpdate) return;

            maximum.setDouble(config, targetMaximum);
            normalDistance.setDouble(config, targetMaximum);
            aimingDistance.setDouble(config, targetMaximum);

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
