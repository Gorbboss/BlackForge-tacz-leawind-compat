package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class HiddenBlockManager {
    /**
     * A five-block-wide obstruction tube means a 2.5-block radius.
     * Keep this as a hard minimum so an older generated config containing the
     * previous 0.32 value cannot silently restore the pencil-thin corridor.
     */
    private static final double REQUIRED_TUBE_RADIUS = 2.5D;

    private static final Set<BlockPos> HIDDEN = new HashSet<>();

    public static boolean isHidden(BlockPos pos) {
        return HIDDEN.contains(pos);
    }

    public static Set<BlockPos> snapshot() {
        return Collections.unmodifiableSet(HIDDEN);
    }

    public static void clear() {
        if (HIDDEN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Set<BlockPos> old = new HashSet<>(HIDDEN);
        HIDDEN.clear();
        markDirty(mc, old);
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientConfig.HIDE_CAMERA_OBSTRUCTIONS.get()) {
            clear();
            return;
        }

        if (mc.options.getCameraType().isFirstPerson()) {
            clear();
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 from = camera.getPosition();
        Vec3 to = mc.player.getEyePosition(1.0F).add(0.0D, -0.30D, 0.0D);
        Vec3 segment = to.subtract(from);
        double segmentLengthSqr = segment.lengthSqr();

        if (segmentLengthSqr < 1.0E-4D) {
            clear();
            return;
        }

        double radius = Math.max(
                REQUIRED_TUBE_RADIUS,
                ClientConfig.HIDE_CORRIDOR_RADIUS.get()
        );
        double blockCenterAllowance = Math.sqrt(3.0D) * 0.5D;
        double searchRadius = radius + blockCenterAllowance;
        double searchRadiusSqr = searchRadius * searchRadius;

        int minX = (int) Math.floor(Math.min(from.x, to.x) - searchRadius);
        int minY = (int) Math.floor(Math.min(from.y, to.y) - searchRadius);
        int minZ = (int) Math.floor(Math.min(from.z, to.z) - searchRadius);
        int maxX = (int) Math.floor(Math.max(from.x, to.x) + searchRadius);
        int maxY = (int) Math.floor(Math.max(from.y, to.y) + searchRadius);
        int maxZ = (int) Math.floor(Math.max(from.z, to.z) + searchRadius);

        HashSet<BlockPos> next = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(mc.level, pos).isEmpty()) {
                        continue;
                    }

                    Vec3 center = Vec3.atCenterOf(pos);
                    if (distanceToSegmentSqr(center, from, segment, segmentLengthSqr)
                            <= searchRadiusSqr) {
                        next.add(pos.immutable());
                    }
                }
            }
        }

        if (!next.equals(HIDDEN)) {
            HashSet<BlockPos> changed = new HashSet<>(HIDDEN);
            changed.addAll(next);
            HIDDEN.clear();
            HIDDEN.addAll(next);
            markDirty(mc, changed);
        }
    }

    private static double distanceToSegmentSqr(
            Vec3 point,
            Vec3 start,
            Vec3 segment,
            double segmentLengthSqr
    ) {
        double t = point.subtract(start).dot(segment) / segmentLengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    private static void markDirty(Minecraft mc, Set<BlockPos> positions) {
        if (mc.levelRenderer == null) return;
        for (BlockPos p : positions) {
            BlockState state = mc.level != null ? mc.level.getBlockState(p) : null;
            mc.levelRenderer.setBlockDirty(p, state, state);
        }
    }

    private HiddenBlockManager() {}
}
