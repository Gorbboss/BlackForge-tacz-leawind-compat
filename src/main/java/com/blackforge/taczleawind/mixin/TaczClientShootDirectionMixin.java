package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.TacticalForwardAttack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/** Keeps TaCZ's local prediction aligned with Passive body-facing hip-fire. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczClientShootDirectionMixin {
    private static final String FINAL_SHOOT =
            "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)" +
            "Lcom/tacz/guns/api/entity/ShootResult;";

    @Shadow @Final private LivingEntity shooter;

    @ModifyVariable(method = FINAL_SHOOT, at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0)
    private Supplier<Float> blackforge$passivePitch(Supplier<Float> original) {
        if (!(shooter instanceof LocalPlayer)
                || !TacticalForwardAttack.isPassiveMode()) return original;
        return () -> 0.0F;
    }

    @ModifyVariable(method = FINAL_SHOOT, at = @At("HEAD"), argsOnly = true,
            ordinal = 1, require = 0)
    private Supplier<Float> blackforge$passiveYaw(Supplier<Float> original) {
        if (!(shooter instanceof LocalPlayer)
                || !TacticalForwardAttack.isPassiveMode()) return original;
        float facingYaw = TacticalForwardAttack.getPassiveShotYaw();
        return () -> facingYaw;
    }
}
