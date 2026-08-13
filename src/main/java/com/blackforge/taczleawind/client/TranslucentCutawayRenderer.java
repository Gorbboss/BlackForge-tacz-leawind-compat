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
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import org.joml.Vector3d;

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
        HiddenBlockManager.ConeRenderData chamber =
                HiddenBlockManager.coneRenderData();
        if (blocks.isEmpty() && !chamber.active()) return;

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

        if (chamber.active()) {
            renderBlackChamber(chamber, camera);
        }
    }

    private static void renderBlackChamber(
            HiddenBlockManager.ConeRenderData chamber,
            Camera camera
    ) {
        final int segments = 32;
        Vec3 axis = chamber.direction().normalize();
        Vec3 helper = Math.abs(axis.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = axis.cross(helper).normalize();
        Vec3 up = right.cross(axis).normalize();
        Vec3 apex = chamber.start();
        Vec3 ringCenter = apex.add(axis.scale(chamber.length()));
        double radius = chamber.radius();
        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 p0 = ringCenter
                    .add(right.scale(Math.cos(a0) * radius))
                    .add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = ringCenter
                    .add(right.scale(Math.cos(a1) * radius))
                    .add(up.scale(Math.sin(a1) * radius));

            vertex(builder, apex, cameraPos);
            vertex(builder, p0, cameraPos);
            vertex(builder, p1, cameraPos);
            vertex(builder, apex, cameraPos);
        }

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
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
