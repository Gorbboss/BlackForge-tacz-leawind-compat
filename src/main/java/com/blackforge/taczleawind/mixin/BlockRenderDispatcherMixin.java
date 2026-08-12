package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.HiddenBlockManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$hideCameraBlock(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (HiddenBlockManager.isHidden(pos)) {
            cir.setReturnValue(false);
        }
    }
}
