package com.blackforge.taczleawind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Hidden cutaway blocks stay absent from the visible terrain mesh, but are
 * submitted again while Oculus/Iris is drawing its shadow map. They therefore
 * remain visually transparent while still occluding shader sunlight.
 */
public final class ShaderShadowCutawayRenderer {
    private static final String[] IRIS_CLASSES = {
            "net.coderbot.iris.Iris",
            "net.irisshaders.iris.Iris"
    };

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || !blackforge$isShadowPass()
                || HiddenBlockManager.snapshot().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        HiddenBlockManager.beginOverlayRender();
        try {
            for (BlockPos pos : HiddenBlockManager.snapshot()) {
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir()
                        || state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }

                poses.pushPose();
                poses.translate(
                        pos.getX() - camera.x,
                        pos.getY() - camera.y,
                        pos.getZ() - camera.z
                );
                mc.getBlockRenderer().renderSingleBlock(
                        state,
                        poses,
                        buffers,
                        LevelRenderer.getLightColor(mc.level, pos),
                        OverlayTexture.NO_OVERLAY
                );
                poses.popPose();
            }
            buffers.endBatch();
        } finally {
            HiddenBlockManager.endOverlayRender();
        }
    }

    private static boolean blackforge$isShadowPass() {
        for (String className : IRIS_CLASSES) {
            try {
                Class<?> iris = Class.forName(className);
                Method managerMethod = iris.getMethod("getPipelineManager");
                Object manager = managerMethod.invoke(null);
                Method pipelineMethod = manager.getClass().getMethod("getPipeline");
                Object value = pipelineMethod.invoke(manager);
                Object pipeline = value instanceof Optional<?> optional
                        ? optional.orElse(null)
                        : value;
                if (pipeline == null) continue;

                Method shadowMethod =
                        pipeline.getClass().getMethod("isRenderingShadowPass");
                return Boolean.TRUE.equals(shadowMethod.invoke(pipeline));
            } catch (ReflectiveOperationException ignored) {
                // Try the other Oculus/Iris package name.
            }
        }
        return false;
    }

    private ShaderShadowCutawayRenderer() {}
}
