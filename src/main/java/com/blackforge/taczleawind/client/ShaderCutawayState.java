package com.blackforge.taczleawind.client;

import net.minecraft.world.phys.Vec3;

/** Immutable per-frame data consumed by the optional Oculus uniform bridge. */
public final class ShaderCutawayState {
    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
    private static Vec3 lastStart = Vec3.ZERO;
    private static Vec3 lastEnd = Vec3.ZERO;
    private static Vec3 lastRight = Vec3.ZERO;
    private static Vec3 lastUp = Vec3.ZERO;
    private static float centerFade;
    private static float crossFade;
    private static float fullFade;

    static void activate(
            Vec3 start,
            Vec3 end,
            Vec3 right,
            Vec3 up,
            double taperLength,
            double endRadius,
            double tubeRadius,
            double outerFadeWidth
    ) {
        lastStart = start;
        lastEnd = end;
        lastRight = right;
        lastUp = up;
        centerFade = Math.min(1.0F, centerFade + 1.0F / 5.0F);
        crossFade = Math.min(1.0F, crossFade + 1.0F / 10.0F);
        fullFade = Math.min(1.0F, fullFade + 1.0F / 20.0F);
        snapshot = new Snapshot(
                true, start, end, right, up,
                (float) taperLength,
                (float) endRadius,
                (float) tubeRadius,
                (float) outerFadeWidth,
                centerFade, crossFade, fullFade
        );
    }

    static void deactivateGradually() {
        centerFade = Math.max(0.0F, centerFade - 1.0F / 20.0F);
        crossFade = Math.max(0.0F, crossFade - 1.0F / 20.0F);
        fullFade = Math.max(0.0F, fullFade - 1.0F / 20.0F);
        if (centerFade == 0.0F && crossFade == 0.0F && fullFade == 0.0F) {
            snapshot = Snapshot.INACTIVE;
            return;
        }
        Snapshot old = snapshot;
        snapshot = new Snapshot(
                true, lastStart, lastEnd, lastRight, lastUp,
                old.taperLength(), old.endRadius(), old.tubeRadius(),
                old.outerFadeWidth(), centerFade, crossFade, fullFade
        );
    }

    static void clear() {
        centerFade = 0.0F;
        crossFade = 0.0F;
        fullFade = 0.0F;
        snapshot = Snapshot.INACTIVE;
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public record Snapshot(
            boolean active,
            Vec3 start,
            Vec3 end,
            Vec3 right,
            Vec3 up,
            float taperLength,
            float endRadius,
            float tubeRadius,
            float outerFadeWidth,
            float centerFade,
            float crossFade,
            float fullFade
    ) {
        private static final Snapshot INACTIVE = new Snapshot(
                false, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
    }

    private ShaderCutawayState() {}
}
