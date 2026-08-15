package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.HiddenBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the opaque terrain wall surrounding the camera cutaway to exist in
 * Embeddium's lowest face-occlusion layer.
 */
@Pseudo
@Mixin(
        targets =
                "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache",
        remap = false
)
public abstract class EmbeddiumBlockOcclusionMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$renderCutawayEnclosure(
            BlockState selfState,
            BlockGetter view,
            BlockPos pos,
            Direction facing,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!HiddenBlockManager.isHidden(pos)
                && HiddenBlockManager.isHidden(pos.relative(facing))) {
            cir.setReturnValue(true);
        }
    }
}
