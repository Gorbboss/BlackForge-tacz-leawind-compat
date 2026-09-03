package com.blackforge.taczleawind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Map;

/** Re-renders the cutaway fade while Oculus still owns the world buffers. */
public final class TranslucentCutawayRenderer {
    public static void render(RenderLevelStageEvent event) {
        /*
         * AFTER_TRANSLUCENT_BLOCKS is too late for several Oculus pipelines:
         * their terrain G-buffer has already been closed. Submit and flush the
         * translucent block RenderType immediately before Minecraft renders
         * its native translucent terrain, so Oculus handles these vertices by
         * the same shader-aware world pass it uses for glass.
         */
        // Forge 1.20.1 exposes this stage with the duplicated BLOCKS suffix.
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) return;

        Map<BlockPos, Float> blocks = HiddenBlockManager.translucentSnapshot();
        if (blocks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType shaderAwareTranslucent = RenderType.entityTranslucent(
                InventoryMenu.BLOCK_ATLAS
        );
        VertexConsumer translucentBuffer = buffers.getBuffer(shaderAwareTranslucent);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();

        HiddenBlockManager.beginOverlayRender();
        try {
            for (Map.Entry<BlockPos, Float> entry : blocks.entrySet()) {
                // A completed center/cardinal fade is represented only by the
                // absent chunk block. Never submit a zero-alpha replacement;
                // some pipelines treat that geometry as opaque.
                if (entry.getValue() <= 0.001F) continue;
                BlockPos pos = entry.getKey();
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir()) continue;

                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - camera.getPosition().x,
                        pos.getY() - camera.getPosition().y,
                        pos.getZ() - camera.getPosition().z
                );
                random.setSeed(state.getSeed(pos));
                dispatcher.renderBatched(
                        state,
                        pos,
                        mc.level,
                        poseStack,
                        new AlphaVertexConsumer(translucentBuffer, entry.getValue()),
                        true,
                        random
                );
                poseStack.popPose();
            }
        } finally {
            HiddenBlockManager.endOverlayRender();
        }

        buffers.endBatch(shaderAwareTranslucent);
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float opacity;

        private AlphaVertexConsumer(VertexConsumer delegate, float opacity) {
            this.delegate = delegate;
            this.opacity = opacity;
        }

        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, Math.round(a * opacity)); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, Math.round(a * opacity)); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }

    private TranslucentCutawayRenderer() {}
}
