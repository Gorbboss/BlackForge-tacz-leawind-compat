package com.blackforge.taczleawind.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Immutable per-frame data consumed by the optional Oculus uniform bridge. */
public final class ShaderCutawayState {
    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
    private static float rightFade, leftFade, upFade, downFade;
    private static float corridorFade;
    private static float overheadFade;
    static void activate(
            BlockPos cameraBlock,
            Vec3 start,
            Vec3 end,
            Vec3 right,
            Vec3 up,
            double taperLength,
            double endRadius,
            double tubeRadius,
            double outerFadeWidth,
            HiddenBlockManager.DirectionalObstruction obstruction,
            boolean overheadClearance
    ) {
        corridorFade = obstruction.any() ? 1.0F : Math.max(0.0F, corridorFade - 0.05F);
        rightFade = move(rightFade, obstruction.right());
        leftFade = move(leftFade, obstruction.left());
        upFade = move(upFade, obstruction.up());
        downFade = move(downFade, obstruction.down());
        overheadFade = overheadClearance ? 1.0F : Math.max(0.0F, overheadFade - 0.05F);
        snapshot = new Snapshot(
                true, cameraBlock, start, end, right, up,
                (float) taperLength,
                (float) endRadius,
                (float) tubeRadius,
                (float) outerFadeWidth,
                corridorFade, rightFade, leftFade, upFade, downFade,
                overheadFade
        );
    }

    static void deactivateSmoothly() {
        Snapshot old = snapshot;
        if (!old.active()) return;
        corridorFade = Math.max(0.0F, corridorFade - 0.05F);
        rightFade = Math.max(0.0F, rightFade - 0.05F);
        leftFade = Math.max(0.0F, leftFade - 0.05F);
        upFade = Math.max(0.0F, upFade - 0.05F);
        downFade = Math.max(0.0F, downFade - 0.05F);
        overheadFade = Math.max(0.0F, overheadFade - 0.05F);
        if (corridorFade <= 0.0F && rightFade <= 0.0F && leftFade <= 0.0F
                && upFade <= 0.0F && downFade <= 0.0F && overheadFade <= 0.0F) {
            clear();
            return;
        }
        snapshot = new Snapshot(true, old.cameraBlock(), old.start(), old.end(),
                old.right(), old.up(), old.taperLength(), old.endRadius(),
                old.tubeRadius(), old.outerFadeWidth(), corridorFade,
                rightFade, leftFade, upFade, downFade, overheadFade);
    }

    private static float move(float value, boolean enabled) {
        return enabled ? Math.min(1.0F, value + 0.05F)
                : Math.max(0.0F, value - 0.05F);
    }

    static void clear() {
        corridorFade = rightFade = leftFade = upFade = downFade = overheadFade = 0.0F;
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
            float corridorFade,
            float rightFade,
            float leftFade,
            float upFade,
            float downFade,
            float overheadFade
    ) {
        private static final Snapshot INACTIVE = new Snapshot(
                false, BlockPos.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
    }

    private ShaderCutawayState() {}
}
