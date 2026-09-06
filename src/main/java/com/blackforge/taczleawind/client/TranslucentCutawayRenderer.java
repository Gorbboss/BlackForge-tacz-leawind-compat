package com.blackforge.taczleawind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import java.util.Set;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

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
        Set<HiddenBlockManager.BoundaryFace> boundary =
                HiddenBlockManager.blackBoundarySnapshot();
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
        TextureAtlasSprite blackConcrete = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation("minecraft", "block/black_concrete"));

        HiddenBlockManager.beginOverlayRender();
        try {
            for (Map.Entry<BlockPos, Float> entry : blocks.entrySet()) {
                // Transition blocks use fake black-concrete geometry. The
                // center opening remains absent and submits no zero-alpha mesh.
                if (entry.getValue() <= 0.001F) continue;
                BlockPos pos = entry.getKey();
                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - camera.getPosition().x,
                        pos.getY() - camera.getPosition().y,
                        pos.getZ() - camera.getPosition().z
                );
                PoseStack.Pose blockPose = poseStack.last();
                int light = LevelRenderer.getLightColor(mc.level, pos);
                for (Direction face : Direction.values()) {
                    if (blocks.containsKey(pos.relative(face))) continue;
                    emitBlackConcreteFace(translucentBuffer, blockPose,
                            BlockPos.ZERO, face, blackConcrete, light,
                            entry.getValue());
                }
                poseStack.popPose();
            }
        } finally {
            HiddenBlockManager.endOverlayRender();
        }


        // Draw only the camera-facing cavity boundary using Minecraft's real
        // black-concrete atlas texture. No block is placed or replaced.
        poseStack.pushPose();
        poseStack.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);
        PoseStack.Pose pose = poseStack.last();
        for (HiddenBlockManager.BoundaryFace face : boundary) {
            int light = LevelRenderer.getLightColor(mc.level, face.pos());
            emitBlackConcreteFace(translucentBuffer, pose, face.pos(),
                    face.face(), blackConcrete, light, 1.0F);
        }
        poseStack.popPose();

        buffers.endBatch(shaderAwareTranslucent);
    }

    private static void emitBlackConcreteFace(
            VertexConsumer consumer, PoseStack.Pose pose,
            BlockPos pos, Direction face, TextureAtlasSprite sprite,
            int light, float opacity
    ) {
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float e = 0.001F;
        float[][] vertices = switch (face) {
            case DOWN -> new float[][]{{x,y-e,z},{x+1,y-e,z},{x+1,y-e,z+1},{x,y-e,z+1}};
            case UP -> new float[][]{{x,y+1+e,z},{x,y+1+e,z+1},{x+1,y+1+e,z+1},{x+1,y+1+e,z}};
            case NORTH -> new float[][]{{x,y,z-e},{x,y+1,z-e},{x+1,y+1,z-e},{x+1,y,z-e}};
            case SOUTH -> new float[][]{{x,y,z+1+e},{x+1,y,z+1+e},{x+1,y+1,z+1+e},{x,y+1,z+1+e}};
            case WEST -> new float[][]{{x-e,y,z},{x-e,y,z+1},{x-e,y+1,z+1},{x-e,y+1,z}};
            case EAST -> new float[][]{{x+1+e,y,z},{x+1+e,y+1,z},{x+1+e,y+1,z+1},{x+1+e,y,z+1}};
        };
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        for (int i = 0; i < 4; i++) {
            consumer.vertex(matrix, vertices[i][0], vertices[i][1], vertices[i][2])
                    .color(255, 255, 255, Math.round(255.0F * opacity))
                    .uv((i == 1 || i == 2) ? u1 : u0, i >= 2 ? v1 : v0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal, nx, ny, nz)
                    .endVertex();
        }
    }

    private TranslucentCutawayRenderer() {}
}
