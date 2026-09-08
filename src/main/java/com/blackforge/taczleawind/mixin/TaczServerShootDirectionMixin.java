package com.blackforge.taczleawind.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Changes the values at the final TaCZ server firing point. This leaves the
 * camera and local-player rotations untouched, preventing the visible snap
 * toward the crosshair that occurred in 0.4.25.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczServerShootDirectionMixin {
    private static final String FINAL_SHOOT =
            "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)" +
            "Lcom/tacz/guns/api/entity/ShootResult;";
    private static final Map<Class<?>, Method> AIM_METHODS =
            new ConcurrentHashMap<>();

    @Shadow @Final private LivingEntity shooter;

    @ModifyVariable(
            method = FINAL_SHOOT,
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 1
    )
    private Supplier<Float> blackforge$levelHipFirePitch(
            Supplier<Float> original
    ) {
        if (!blackforge$useHipFireDirection()) return original;
        return () -> 0.0F;
    }

    @ModifyVariable(
            method = FINAL_SHOOT,
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            require = 1
    )
    private Supplier<Float> blackforge$useBodyFacingYaw(
            Supplier<Float> original
    ) {
        if (!blackforge$useHipFireDirection()) return original;
        float facingYaw = shooter.yBodyRot;
        return () -> facingYaw;
    }

    private boolean blackforge$useHipFireDirection() {
        if (!(shooter instanceof ServerPlayer)) return false;
        return !blackforge$isAiming();
    }

    private boolean blackforge$isAiming() {
        try {
            Method method = AIM_METHODS.computeIfAbsent(
                    shooter.getClass(),
                    type -> {
                        try {
                            Method found = type.getMethod("getSynIsAiming");
                            found.setAccessible(true);
                            return found;
                        } catch (ReflectiveOperationException ignored) {
                            return null;
                        }
                    }
            );
            if (method == null) return true;
            return Boolean.TRUE.equals(method.invoke(shooter));
        } catch (ReflectiveOperationException ignored) {
            // Preserve normal TaCZ aim if its API ever changes instead of
            // accidentally forcing ADS shots level.
            return true;
        }
    }
}
