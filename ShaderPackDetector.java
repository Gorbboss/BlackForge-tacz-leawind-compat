package com.blackforge.taczleawind.client;

import java.lang.reflect.Method;

/** Detects Iris/Oculus without making either mod a required dependency. */
final class ShaderPackDetector {
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };
    private static Method getInstance;
    private static Method isShaderPackInUse;
    private static boolean searched;

    static boolean isShaderPackActive() {
        resolve();
        if (getInstance == null || isShaderPackInUse == null) return false;
        try {
            Object api = getInstance.invoke(null);
            return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void resolve() {
        if (searched) return;
        searched = true;
        for (String name : API_CLASSES) {
            try {
                Class<?> api = Class.forName(name);
                getInstance = api.getMethod("getInstance");
                isShaderPackInUse = api.getMethod("isShaderPackInUse");
                return;
            } catch (ReflectiveOperationException ignored) {
                // Try the package name used by the other Oculus generation.
            }
        }
    }

    private ShaderPackDetector() {}
}
