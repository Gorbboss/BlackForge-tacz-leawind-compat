package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public final class HiddenBlockManager {
    private static final double CAMERA_RADIUS = 1.5D;
    private static final double PLAYER_RADIUS = 2.5D;
    private static final double INVISIBLE_CORE_FRACTION = 0.70D;

    /*
     * Chunk meshes can be built on Embeddium worker threads. Publish complete,
     * immutable sets rather than mutating a HashSet while workers read it.
     */
    private static volatile Set<BlockPos> hidden = Set.of();

    public static boolean isHidden(BlockPos pos) {
        return hidden.contains(pos);
    }

    public static Set<BlockPos> snapshot() {
        return hidden;
    }

    public static void clear() {
        Set<BlockPos> old = hidden;
        if (old.isEmpty()) return;

        hidden = Set.of();
        markDirty(Minecraft.getInstance(), old);
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !ClientConfig.HIDE_CAMERA_OBSTRUCTIONS.get()
                || mc.options.getCameraType().isFirstPerson()) {
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

        double blockCenterAllowance = Math.sqrt(3.0D) * 0.5D;
        double maximumSearchRadius = PLAYER_RADIUS + blockCenterAllowance;

        int minX = (int) Math.floor(Math.min(from.x, to.x) - maximumSearchRadius);
        int minY = (int) Math.floor(Math.min(from.y, to.y) - maximumSearchRadius);
        int minZ = (int) Math.floor(Math.min(from.z, to.z) - maximumSearchRadius);
        int maxX = (int) Math.floor(Math.max(from.x, to.x) + maximumSearchRadius);
        int maxY = (int) Math.floor(Math.max(from.y, to.y) + maximumSearchRadius);
        int maxZ = (int) Math.floor(Math.max(from.z, to.z) + maximumSearchRadius);

        HashSet<BlockPos> nextMutable = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(mc.level, pos).isEmpty()) {
                        continue;
                    }

                    Vec3 center = Vec3.atCenterOf(pos);
                    Vec3 fromStart = center.subtract(from);
                    double t = fromStart.dot(segment) / segmentLengthSqr;
                    t = Math.max(0.0D, Math.min(1.0D, t));

                    Vec3 nearest = from.add(segment.scale(t));
                    double outerRadius = CAMERA_RADIUS
                            + (PLAYER_RADIUS - CAMERA_RADIUS) * t;
                    double invisibleRadius =
                            outerRadius * INVISIBLE_CORE_FRACTION
                                    + blockCenterAllowance;

                    if (center.distanceToSqr(nearest)
                            <= invisibleRadius * invisibleRadius) {
                        nextMutable.add(pos.immutable());
                    }
                }
            }
        }

        Set<BlockPos> next = Set.copyOf(nextMutable);
        Set<BlockPos> old = hidden;

        if (!next.equals(old)) {
            HashSet<BlockPos> changed = new HashSet<>(old);
            changed.addAll(next);
            hidden = next;
            markDirty(mc, changed);
        }
    }

    private static void markDirty(Minecraft mc, Set<BlockPos> positions) {
        if (mc.levelRenderer == null) return;

        HashSet<Long> sections = new HashSet<>();
        for (BlockPos pos : positions) {
            sections.add(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ())
            ));
        }

        for (long packed : sections) {
            mc.levelRenderer.setSectionDirty(
                    SectionPos.x(packed),
                    SectionPos.y(packed),
                    SectionPos.z(packed)
            );
        }
    }

    private HiddenBlockManager() {}
}
