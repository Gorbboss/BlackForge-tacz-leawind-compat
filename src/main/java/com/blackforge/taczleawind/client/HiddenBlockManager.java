package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public final class HiddenBlockManager {
    private static final double CAMERA_APEX_BACK_OFFSET = 1.0D;
    private static final double PLAYER_END_BACK_OFFSET = 1.0D;
    private static final double CONE_HALF_ANGLE_RADIANS = Math.toRadians(35.0D);
    private static final double PLAYER_END_RADIUS = 1.5D;

    /*
     * Embeddium builds chunk meshes on worker threads. Always publish a
     * complete immutable snapshot.
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
        Vec3 cameraPos = camera.getPosition();
        Vec3 playerPos = mc.player.getEyePosition(1.0F)
                .add(0.0D, -0.30D, 0.0D);

        Vec3 cameraToPlayer = playerPos.subtract(cameraPos);
        double cameraDistance = cameraToPlayer.length();
        if (cameraDistance < 2.05D) {
            clear();
            return;
        }

        Vec3 axis = cameraToPlayer.scale(1.0D / cameraDistance);

        // Start one block behind the camera and stop one block behind the
        // character, on the camera-facing side.
        Vec3 start = cameraPos.subtract(axis.scale(CAMERA_APEX_BACK_OFFSET));
        Vec3 end = playerPos.subtract(axis.scale(PLAYER_END_BACK_OFFSET));
        Vec3 shapeAxis = end.subtract(start);
        double shapeLength = shapeAxis.length();
        Vec3 shapeDirection = shapeAxis.scale(1.0D / shapeLength);
        double midpoint = shapeLength * 0.5D;

        // 70 degrees is the complete opening angle, hence tan(35 degrees).
        double maximumRadius = Math.tan(CONE_HALF_ANGLE_RADIANS) * midpoint;
        maximumRadius = Math.max(maximumRadius, PLAYER_END_RADIUS);

        double blockAllowance = Math.sqrt(3.0D) * 0.5D;
        double searchRadius = maximumRadius + blockAllowance;

        int minX = (int) Math.floor(Math.min(start.x, end.x) - searchRadius);
        int minY = (int) Math.floor(Math.min(start.y, end.y) - searchRadius);
        int minZ = (int) Math.floor(Math.min(start.z, end.z) - searchRadius);
        int maxX = (int) Math.floor(Math.max(start.x, end.x) + searchRadius);
        int maxY = (int) Math.floor(Math.max(start.y, end.y) + searchRadius);
        int maxZ = (int) Math.floor(Math.max(start.z, end.z) + searchRadius);

        HashSet<BlockPos> nextMutable = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()
                            || state.getCollisionShape(mc.level, pos).isEmpty()) {
                        continue;
                    }

                    Vec3 center = Vec3.atCenterOf(pos);

                    Vec3 fromStart = center.subtract(start);
                    double axialDistance = fromStart.dot(shapeDirection);

                    // Do not hide anything beyond either end cap.
                    if (axialDistance < 0.0D || axialDistance > shapeLength) {
                        continue;
                    }

                    // Keep the cutaway one full block above the surface the
                    // player is standing on. Camera pitch cannot lower it.
                    int minimumHiddenY = (int) Math.floor(mc.player.getY()) + 1;
                    if (pos.getY() < minimumHiddenY) {
                        continue;
                    }

                    double radius;
                    if (axialDistance <= midpoint) {
                        radius = Math.tan(CONE_HALF_ANGLE_RADIANS)
                                * axialDistance;
                    } else {
                        double closingProgress =
                                (axialDistance - midpoint)
                                        / (shapeLength - midpoint);
                        radius = maximumRadius
                                + (PLAYER_END_RADIUS - maximumRadius)
                                * closingProgress;
                    }

                    Vec3 nearest = start.add(
                            shapeDirection.scale(axialDistance)
                    );
                    double acceptedRadius = radius + blockAllowance;

                    if (center.distanceToSqr(nearest)
                            <= acceptedRadius * acceptedRadius) {
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
            addSection(sections, pos);

            // A visible cavity face may belong to the neighboring block, even
            // across a render-section boundary.
            for (Direction direction : Direction.values()) {
                addSection(sections, pos.relative(direction));
            }
        }

        for (long packed : sections) {
            mc.levelRenderer.setSectionDirty(
                    SectionPos.x(packed),
                    SectionPos.y(packed),
                    SectionPos.z(packed)
            );
        }
    }

    private static void addSection(Set<Long> sections, BlockPos pos) {
        sections.add(SectionPos.asLong(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ())
        ));
    }

    private HiddenBlockManager() {}
}
