package com.blackforge.taczleawind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        Map<HiddenBlockManager.BoundaryFace, Float> boundary =
                HiddenBlockManager.boundarySnapshot();
        if (blocks.isEmpty() && boundary.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType shaderAwareTranslucent = RenderType.entityTranslucent(
                InventoryMenu.BLOCK_ATLAS
        );
        VertexConsumer translucentBuffer = buffers.getBuffer(shaderAwareTranslucent);
        // Boundary faces are opaque camera-cavity walls. A cutout/no-cull
        // entity pass gives Oculus a depth-writing shader-aware draw and
        // prevents translucent sorting from dropping or reordering faces.
        RenderType shaderAwareBoundary = RenderType.entityCutoutNoCull(
                InventoryMenu.BLOCK_ATLAS
        );
        VertexConsumer boundaryBuffer = buffers.getBuffer(shaderAwareBoundary);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();

        HiddenBlockManager.beginOverlayRender();
        try {
            for (Map.Entry<BlockPos, Float> entry : blocks.entrySet()) {
                // Retained for compatibility with any nonzero overlay entry.
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
                dispatcher.renderBatched(state, pos, mc.level, poseStack,
                        new AlphaVertexConsumer(translucentBuffer, entry.getValue()),
                        true, random);
                poseStack.popPose();
            }
        } finally {
            HiddenBlockManager.endOverlayRender();
        }

        // Re-render only the face exposed to the camera cavity. Its vertex
        // color and packed light are both zero so neither vanilla nor a shader
        // can relight it. This replaces the artificial stone-texture shell.
        for (Map.Entry<HiddenBlockManager.BoundaryFace, Float> entry
                : boundary.entrySet()) {
            HiddenBlockManager.BoundaryFace face = entry.getKey();
            BlockState state = mc.level.getBlockState(face.pos());
            if (state.isAir()) continue;
            poseStack.pushPose();
            poseStack.translate(
                    face.pos().getX() - camera.getPosition().x,
                    face.pos().getY() - camera.getPosition().y,
                    face.pos().getZ() - camera.getPosition().z
            );
            random.setSeed(state.getSeed(face.pos()));
            dispatcher.renderBatched(state, face.pos(), mc.level, poseStack,
                    new UnlitFaceVertexConsumer(boundaryBuffer, face.face()),
                    false, random);
            poseStack.popPose();
        }

        buffers.endBatch(shaderAwareTranslucent);
        buffers.endBatch(shaderAwareBoundary);
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

    /** Buffers one model vertex and emits only quads facing the cavity. */
    private static final class UnlitFaceVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Direction face;
        private double x, y, z;
        private int alpha = 255;
        private float u, v, normalX, normalY, normalZ;
        private int overlayU = OverlayTexture.NO_OVERLAY & 0xFFFF;
        private int overlayV = OverlayTexture.NO_OVERLAY >>> 16;

        private UnlitFaceVertexConsumer(VertexConsumer delegate, Direction face) {
            this.delegate = delegate;
            this.face = face;
        }

        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            alpha = a; return this;
        }
        @Override public VertexConsumer uv(float u, float v) {
            this.u = u; this.v = v; return this;
        }
        @Override public VertexConsumer overlayCoords(int u, int v) {
            overlayU = u; overlayV = v; return this;
        }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) {
            normalX = x; normalY = y; normalZ = z; return this;
        }
        @Override public void endVertex() {
            float alignment = normalX * face.getStepX()
                    + normalY * face.getStepY()
                    + normalZ * face.getStepZ();
            if (alignment > 0.99F) {
                delegate.vertex(x + face.getStepX() * 0.001D,
                                y + face.getStepY() * 0.001D,
                                z + face.getStepZ() * 0.001D)
                        .color(0, 0, 0, alpha)
                        .uv(u, v)
                        .overlayCoords(overlayU, overlayV)
                        .uv2(0, 0)
                        .normal(normalX, normalY, normalZ)
                        .endVertex();
            }
        }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    private TranslucentCutawayRenderer() {}
}
