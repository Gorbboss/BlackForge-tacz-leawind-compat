package com.blackforge.taczleawind.mixin;

import com.blackforge.taczleawind.client.HiddenBlockManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embeddium 0.3.x bypasses vanilla BlockRenderDispatcher while meshing terrain.
 * Cancel its model render at the real per-block entry point.
 */
@Pseudo
@Mixin(
        targets =
                "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false
)
public abstract class EmbeddiumBlockRendererMixin {
    private static final Map<Class<?>, Method> BLACKFORGE_POS_METHODS =
            new ConcurrentHashMap<>();

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void blackforge$hideCameraBlockEmbeddium(
            @Coerce Object context,
            @Coerce Object buffers,
            CallbackInfo ci
    ) {
        BlockPos pos = blackforge$getPosition(context);
        if (pos != null && HiddenBlockManager.isHidden(pos)) {
            ci.cancel();
        }
    }

    private static BlockPos blackforge$getPosition(Object context) {
        if (context == null) return null;

        try {
            Method method = BLACKFORGE_POS_METHODS.computeIfAbsent(
                    context.getClass(),
                    type -> {
                        try {
                            Method found = type.getMethod("pos");
                            found.setAccessible(true);
                            return found;
                        } catch (ReflectiveOperationException ignored) {
                            return null;
                        }
                    }
            );

            if (method == null) return null;
            Object value = method.invoke(context);
            return value instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
