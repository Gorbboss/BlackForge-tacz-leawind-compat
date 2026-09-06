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
        corridorFade = obstruction.any() ? 1.0F : Math.max(0.0F, corridorFade - 0.05F);
        for (int i = 0; i < 8; i++) {
            sectorFade[i] = move(sectorFade[i], obstruction.sector(i));
        }
        overheadFade = overheadClearance ? 1.0F : Math.max(0.0F, overheadFade - 0.05F);
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
        Snapshot old = snapshot;
        if (!old.active()) return;
        corridorFade = Math.max(0.0F, corridorFade - 0.05F);
        for (int i = 0; i < 8; i++) {
            sectorFade[i] = Math.max(0.0F, sectorFade[i] - 0.05F);
        }
        overheadFade = Math.max(0.0F, overheadFade - 0.05F);
        boolean sectorsClear = true;
        for (float value : sectorFade) sectorsClear &= value <= 0.0F;
        if (corridorFade <= 0.0F && sectorsClear && overheadFade <= 0.0F) {
            clear();
            return;
        }
        snapshot = new Snapshot(true, old.cameraBlock(), old.start(), old.end(),
                old.right(), old.up(), old.taperLength(), old.endRadius(),
                old.tubeRadius(), old.outerFadeWidth(), corridorFade,
                sectorFade[0], sectorFade[1], sectorFade[2], sectorFade[3],
                sectorFade[4], sectorFade[5], sectorFade[6], sectorFade[7],
                overheadFade);
    }

    private static float move(float value, float target) {
        if (value < target) return Math.min(target, value + 0.05F);
        return Math.max(target, value - 0.05F);
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
