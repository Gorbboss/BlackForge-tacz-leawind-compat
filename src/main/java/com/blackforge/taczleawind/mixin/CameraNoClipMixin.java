package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraNoClipMixin {
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$disableCameraCollision(
            double requestedDistance,
            CallbackInfoReturnable<Double> cir
    ) {
        if (ClientConfig.DISABLE_CAMERA_COLLISION.get()) {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = (Camera) (Object) this;
            Vec3 look = new Vec3(camera.getLookVector());
            Vec3 requestedPosition = camera.getPosition()
                    .subtract(look.scale(requestedDistance));
            if (mc.player != null
                    && Math.floor(requestedPosition.y)
                    <= Math.floor(mc.player.getY())) {
                return;
            }
            cir.setReturnValue(requestedDistance);
        }
    }
}
