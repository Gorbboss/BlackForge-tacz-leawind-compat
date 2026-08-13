package com.blackforge.taczleawind.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Map;

/**
 * Re-renders the two cutaway edge shells in the translucent pass after their
 * ordinary opaque/chunk geometry has been suppressed.
 */
public final class TranslucentCutawayRenderer {
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Map<BlockPos, Float> blocks = HiddenBlockManager.translucentSnapshot();
        HiddenBlockManager.CameraBoxRenderData enclosure =
                HiddenBlockManager.cameraBoxRenderData();
        if (blocks.isEmpty() && !enclosure.active()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer translucentBuffer = buffers.getBuffer(RenderType.translucent());
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        HiddenBlockManager.beginOverlayRender();
        try {
            for (Map.Entry<BlockPos, Float> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir()) continue;

                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - camera.getPosition().x,
                        pos.getY() - camera.getPosition().y,
                        pos.getZ() - camera.getPosition().z
                );

                dispatcher.renderBatched(
                        state,
                        pos,
                        mc.level,
                        poseStack,
                        new AlphaVertexConsumer(
                                translucentBuffer,
                                entry.getValue()
                        ),
                        true,
                        RandomSource.create()
                );
                poseStack.popPose();
            }
        } finally {
            HiddenBlockManager.endOverlayRender();
        }

        buffers.endBatch(RenderType.translucent());

        if (enclosure.active()) {
            renderBlackCameraBox(enclosure, camera);
        }
    }

    /*
     * Five-sided 3x3x2 enclosure around the camera. The face toward the
     * player is deliberately open. This replaces the long black cone/tube.
     */
    private static void renderBlackCameraBox(
            HiddenBlockManager.CameraBoxRenderData box,
            Camera camera
    ) {
        Vec3 center = box.camera();
        Vec3 forward = box.forward();
        Vec3 right = box.right();
        Vec3 up = box.up();
        double half = 1.5D;

        Vec3 back = center.subtract(forward.scale(0.25D));
        Vec3 front = center.add(forward.scale(2.0D));

        Vec3 btl = back.add(up.scale(half)).subtract(right.scale(half));
        Vec3 btr = back.add(up.scale(half)).add(right.scale(half));
        Vec3 bbl = back.subtract(up.scale(half)).subtract(right.scale(half));
        Vec3 bbr = back.subtract(up.scale(half)).add(right.scale(half));
        Vec3 ftl = front.add(up.scale(half)).subtract(right.scale(half));
        Vec3 ftr = front.add(up.scale(half)).add(right.scale(half));
        Vec3 fbl = front.subtract(up.scale(half)).subtract(right.scale(half));
        Vec3 fbr = front.subtract(up.scale(half)).add(right.scale(half));

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vec3 cameraPos = camera.getPosition();

        // Rear, top, bottom, left and right. Front remains open.
        quad(builder, btl, btr, bbr, bbl, cameraPos);
        quad(builder, btl, ftl, ftr, btr, cameraPos);
        quad(builder, bbl, bbr, fbr, fbl, cameraPos);
        quad(builder, btl, bbl, fbl, ftl, cameraPos);
        quad(builder, btr, ftr, fbr, bbr, cameraPos);

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
    }

    private static void quad(
            BufferBuilder builder,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 camera
    ) {
        vertex(builder, a, camera);
        vertex(builder, b, camera);
        vertex(builder, c, camera);
        vertex(builder, d, camera);
    }

    private static void vertex(
            BufferBuilder builder,
            Vec3 point,
            Vec3 camera
    ) {
        builder.vertex(
                point.x - camera.x,
                point.y - camera.y,
                point.z - camera.z
        ).color(0, 0, 0, 255).endVertex();
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float opacity;

        private AlphaVertexConsumer(VertexConsumer delegate, float opacity) {
            this.delegate = delegate;
            this.opacity = opacity;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(
                    red,
                    green,
                    blue,
                    Math.round(alpha * opacity)
            );
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(
                    red,
                    green,
                    blue,
                    Math.round(alpha * opacity)
            );
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    private TranslucentCutawayRenderer() {}
}
