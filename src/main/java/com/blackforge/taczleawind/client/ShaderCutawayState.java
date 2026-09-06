package com.blackforge.taczleawind.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Immutable per-frame data consumed by the optional Oculus uniform bridge. */
public final class ShaderCutawayState {
    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
    private static Vec3 lastStart = Vec3.ZERO;
    private static Vec3 lastEnd = Vec3.ZERO;
    private static Vec3 lastRight = Vec3.ZERO;
    private static Vec3 lastUp = Vec3.ZERO;
    private static BlockPos lastCameraBlock = BlockPos.ZERO;
    private static float nearFade;
    private static float middleFade;
    private static float farFade;
    private static float fullFade;

    static void activate(
            BlockPos cameraBlock,
            Vec3 start,
            Vec3 end,
            Vec3 right,
            Vec3 up,
            double taperLength,
            double endRadius,
            double tubeRadius,
            double outerFadeWidth
    ) {
        lastCameraBlock = cameraBlock;
        lastStart = start;
        lastEnd = end;
        lastRight = right;
        lastUp = up;
        nearFade = Math.min(1.0F, nearFade + 1.0F / 5.0F);
        middleFade = Math.min(1.0F, middleFade + 1.0F / 10.0F);
        farFade = Math.min(1.0F, farFade + 1.0F / 15.0F);
        fullFade = Math.min(1.0F, fullFade + 1.0F / 20.0F);
        snapshot = new Snapshot(
                true, cameraBlock, start, end, right, up,
                (float) taperLength,
                (float) endRadius,
                (float) tubeRadius,
                (float) outerFadeWidth,
                nearFade, middleFade, farFade, fullFade
        );
    }

    static void deactivateGradually() {
        nearFade = Math.max(0.0F, nearFade - 1.0F / 20.0F);
        middleFade = Math.max(0.0F, middleFade - 1.0F / 20.0F);
        farFade = Math.max(0.0F, farFade - 1.0F / 20.0F);
        fullFade = Math.max(0.0F, fullFade - 1.0F / 20.0F);
        if (nearFade == 0.0F && middleFade == 0.0F && farFade == 0.0F && fullFade == 0.0F) {
            snapshot = Snapshot.INACTIVE;
            return;
        }
        Snapshot old = snapshot;
        snapshot = new Snapshot(
                true, lastCameraBlock, lastStart, lastEnd, lastRight, lastUp,
                old.taperLength(), old.endRadius(), old.tubeRadius(),
                old.outerFadeWidth(), nearFade, middleFade, farFade, fullFade
        );
    }

    static void clear() {
        nearFade = 0.0F;
        middleFade = 0.0F;
        farFade = 0.0F;
        fullFade = 0.0F;
        snapshot = Snapshot.INACTIVE;
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public record Snapshot(
            boolean active,
            BlockPos cameraBlock,
            Vec3 start,
            Vec3 end,
            Vec3 right,
            Vec3 up,
            float taperLength,
            float endRadius,
            float tubeRadius,
            float outerFadeWidth,
            float nearFade,
            float middleFade,
            float farFade,
            float fullFade
    ) {
        private static final Snapshot INACTIVE = new Snapshot(
                false, BlockPos.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
    }

    private ShaderCutawayState() {}
}
