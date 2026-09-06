package com.blackforge.taczleawind.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Immutable per-frame data consumed by the optional Oculus uniform bridge. */
public final class ShaderCutawayState {
    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
    private static final float[] sectorFade = new float[8];
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
            HiddenBlockManager.CutawayObstruction obstruction,
            boolean overheadClearance
    ) {
        corridorFade = obstruction.any() ? 1.0F : 0.0F;
        for (int i = 0; i < 8; i++) {
            sectorFade[i] = obstruction.sector(i);
        }
        overheadFade = overheadClearance ? 1.0F : 0.0F;
        snapshot = new Snapshot(
                true, cameraBlock, start, end, right, up,
                (float) taperLength,
                (float) endRadius,
                (float) tubeRadius,
                (float) outerFadeWidth,
                corridorFade,
                sectorFade[0], sectorFade[1], sectorFade[2], sectorFade[3],
                sectorFade[4], sectorFade[5], sectorFade[6], sectorFade[7],
                overheadFade
        );
    }

    static void deactivateSmoothly() {
        clear();
    }

    static void clear() {
        corridorFade = overheadFade = 0.0F;
        java.util.Arrays.fill(sectorFade, 0.0F);
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
            float sector0, float sector1, float sector2, float sector3,
            float sector4, float sector5, float sector6, float sector7,
            float overheadFade
    ) {
        private static final Snapshot INACTIVE = new Snapshot(
                false, BlockPos.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
    }

    private ShaderCutawayState() {}
}
