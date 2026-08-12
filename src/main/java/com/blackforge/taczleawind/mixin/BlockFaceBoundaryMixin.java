package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.HiddenBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockFaceBoundaryMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void blackforge$renderBoundaryFace(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!HiddenBlockManager.isHidden(pos) && HiddenBlockManager.isHidden(neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}
