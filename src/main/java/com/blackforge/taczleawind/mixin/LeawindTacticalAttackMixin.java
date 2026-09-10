package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.TacticalForwardAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector2d;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/** Applies BlackForge Movement's Passive/Tactical camera-facing rules. */
@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.EntityAgent", remap = false)
public abstract class LeawindTacticalAttackMixin {
    @Inject(method = "isInteracting()Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void blackforge$keepTacticalAttackForward(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && TacticalForwardAttack.shouldSuppressLeawindInteraction()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setRawRotation", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$applyStanceRotation(Vector2d rotation, CallbackInfo ci) {
        if (TacticalForwardAttack.isTacticalCrosshairMode()) {
            Vector2d crosshairRotation = blackforge$getCrosshairRotation();
            if (crosshairRotation != null) rotation.set(crosshairRotation);
            return;
        }

        if (!TacticalForwardAttack.isPassiveForwardMode()) return;

        // Passive may still turn with actual movement, but never with the
        // camera/crosshair. It also remains perfectly level outside ADS.
        if (blackforge$isMovementTarget()) {
            rotation.x = 0.0D;
        } else {
            ci.cancel();
        }
    }

    private boolean blackforge$isMovementTarget() {
        try {
            Method method = this.getClass().getMethod("getRotateTarget");
            Object target = method.invoke(this);
            return target != null && target.toString().contains("IMPULSE_DIRECTION");
        } catch (ReflectiveOperationException ignored) {
            // Failing closed prevents an accidental passive crosshair snap.
            return false;
        }
    }

    private static Vector2d blackforge$getCrosshairRotation() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        HitResult hit = mc.hitResult;
        if (player == null || hit == null) return null;

        Vec3 delta = hit.getLocation().subtract(player.getEyePosition());
        double horizontal = Math.hypot(delta.x, delta.z);
        if (horizontal < 1.0E-6D && Math.abs(delta.y) < 1.0E-6D) return null;

        double pitch = -Math.toDegrees(Math.atan2(delta.y, horizontal));
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        return new Vector2d(pitch, yaw);
    }
}
