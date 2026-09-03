package com.blackforge.taczleawind.client;

import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/** Registers BlackForge uniforms with Oculus while keeping Oculus optional. */
public final class OculusUniformBridge {
    public static void register(Object holder) {
        if (holder == null) return;
        try {
            Class<?> frequencyClass = Class.forName(
                    "net.irisshaders.iris.gl.uniform.UniformUpdateFrequency"
            );
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object perFrame = Enum.valueOf((Class) frequencyClass, "PER_FRAME");

            addFloat(holder, perFrame, "bfCutawayActive",
                    () -> ShaderCutawayState.snapshot().active() ? 1.0F : 0.0F);
            addVec3(holder, perFrame, "bfCutawayStart",
                    () -> vector(ShaderCutawayState.snapshot().start()));
            addVec3(holder, perFrame, "bfCutawayEnd",
                    () -> vector(ShaderCutawayState.snapshot().end()));
            addVec3(holder, perFrame, "bfCutawayRight",
                    () -> vector(ShaderCutawayState.snapshot().right()));
            addVec3(holder, perFrame, "bfCutawayUp",
                    () -> vector(ShaderCutawayState.snapshot().up()));
            addVec3(holder, perFrame, "bfCutawayFade", () -> {
                ShaderCutawayState.Snapshot state = ShaderCutawayState.snapshot();
                return new Vector3f(
                        state.centerFade(), state.crossFade(), state.fullFade()
                );
            });
            addVec4(holder, perFrame, "bfCutawayShape", () -> {
                ShaderCutawayState.Snapshot state = ShaderCutawayState.snapshot();
                return new Vector4f(
                        state.taperLength(), state.endRadius(),
                        state.tubeRadius(), state.outerFadeWidth()
                );
            });
        } catch (ReflectiveOperationException ignored) {
            // Oculus is optional, or this Oculus generation has a different API.
        }
    }

    private static void addFloat(
            Object holder, Object frequency, String name, FloatValue value
    ) throws ReflectiveOperationException {
        for (Method method : holder.getClass().getMethods()) {
            if (!method.getName().equals("uniform1f") || method.getParameterCount() != 3) continue;
            Class<?> supplierType = method.getParameterTypes()[2];
            if (!supplierType.getName().endsWith("FloatSupplier")) continue;
            Object supplier = Proxy.newProxyInstance(
                    supplierType.getClassLoader(),
                    new Class<?>[]{supplierType},
                    (proxy, called, args) -> called.getName().equals("getAsFloat")
                            ? value.getAsFloat()
                            : null
            );
            method.setAccessible(true);
            method.invoke(holder, frequency, name, supplier);
            return;
        }
    }

    private static void addVec3(
            Object holder, Object frequency, String name, Supplier<Vector3f> value
    ) throws ReflectiveOperationException {
        invokeSupplierUniform(holder, frequency, "uniform3f", name, value);
    }

    private static void addVec4(
            Object holder, Object frequency, String name, Supplier<Vector4f> value
    ) throws ReflectiveOperationException {
        invokeSupplierUniform(holder, frequency, "uniform4f", name, value);
    }

    private static void invokeSupplierUniform(
            Object holder, Object frequency, String methodName,
            String name, Supplier<?> value
    ) throws ReflectiveOperationException {
        for (Method method : holder.getClass().getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == 3
                    && Supplier.class.isAssignableFrom(method.getParameterTypes()[2])) {
                method.setAccessible(true);
                method.invoke(holder, frequency, name, value);
                return;
            }
        }
    }

    private static Vector3f vector(net.minecraft.world.phys.Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    @FunctionalInterface
    private interface FloatValue {
        float getAsFloat();
    }

    private OculusUniformBridge() {}
}
