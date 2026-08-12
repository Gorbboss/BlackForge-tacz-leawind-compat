package com.blackforge.taczleawind.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.RenderCrosshairEvent", remap = false)
public abstract class TaczCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z",
                    remap = true
            ),
            require = 0
    )
    private static boolean blackforge$allowThirdPersonCrosshair(boolean original) {
        return original || !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    @ModifyExpressionValue(
            method = {"onRenderOverlay", "lambda$onRenderOverlay$0"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/api/client/gameplay/IClientPlayerGunOperator;getClientAimingProgress(F)F"
            ),
            require = 0
    )
    private static float blackforge$keepCrosshairVisibleWhileAiming(float oldValue) {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson() ? oldValue : 0.0F;
    }
}
