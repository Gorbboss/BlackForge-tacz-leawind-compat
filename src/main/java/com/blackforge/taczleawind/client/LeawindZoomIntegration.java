package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Expands Leawind 2.2.0's scroll-wheel camera-distance range. */
final class LeawindZoomIntegration {
    private static Object configuredInstance;
    private static double configuredMaximum = Double.NaN;
    private static boolean unavailable;

    private LeawindZoomIntegration() {}

    static void update() {
        if (unavailable) return;

        double requestedMaximum = ClientConfig.MAX_THIRD_PERSON_DISTANCE.get();
        try {
            Class<?> thirdPerson = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            Method getConfig = thirdPerson.getMethod("getConfig");
            Object config = getConfig.invoke(null);
            if (config == null) return;

            if (config == configuredInstance
                    && Double.compare(configuredMaximum, requestedMaximum) == 0) {
                return;
            }

            Field maximum = config.getClass().getField("camera_distance_max");
            maximum.setDouble(config, requestedMaximum);

            Method rebuildDistances = config.getClass().getMethod("updateDistancesMonoList");
            rebuildDistances.invoke(config);

            configuredInstance = config;
            configuredMaximum = requestedMaximum;
        } catch (ClassNotFoundException exception) {
            unavailable = true;
        } catch (ReflectiveOperationException ignored) {
            // Keep retrying: Leawind can replace its config object after reload.
        }
    }
}
