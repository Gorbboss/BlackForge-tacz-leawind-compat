package com.blackforge.taczleawind.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Immutable per-frame data consumed by the optional Oculus uniform bridge. */
public final class ShaderCutawayState {
    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
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
        snapshot = new Snapshot(
                true, cameraBlock, start, end, right, up,
                (float) taperLength,
                (float) endRadius,
                (float) tubeRadius,
                (float) outerFadeWidth,
                obstruction.any(), obstruction.right(), obstruction.left(),
                obstruction.up(), obstruction.down(), overheadClearance
        );
    }

    static void clear() {
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
            boolean corridorActive,
            boolean rightActive,
            boolean leftActive,
            boolean upActive,
            boolean downActive,
            boolean overheadClearance
    ) {
        private static final Snapshot INACTIVE = new Snapshot(
                false, BlockPos.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                0.0F, 0.0F, 0.0F, 0.0F,
                false, false, false, false, false, false
        );
    }

    private ShaderCutawayState() {}
}
