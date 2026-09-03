package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class HiddenBlockManager {
    private static final double CAMERA_APEX_BACK_OFFSET = 1.0D;
    private static final double PLAYER_END_BACK_OFFSET = 1.0D;
    // Half a block in radius gives a one-block-wide opening at each end.
    private static final double END_RADIUS = 0.5D;
    private static final double TUBE_RADIUS = 1.5D;
    private static final double MAX_TAPER_LENGTH = 3.0D;

    /*
     * Embeddium builds chunk meshes on worker threads. Always publish a
     * complete immutable snapshot.
     */
    private static final double OUTER_FADE_WIDTH = 3.0D;
    private static final double TRIGGER_RAY_OFFSET = 0.85D;
    private static final int CENTER_FADE_TICKS = 5;   // 0.25 seconds
    private static final int CROSS_FADE_TICKS = 10;  // 0.50 seconds
    private static final int FULL_FADE_TICKS = 20;   // 1.00 second
    private static final ThreadLocal<Boolean> OVERLAY_RENDERING =
            ThreadLocal.withInitial(() -> false);

    private static volatile Set<BlockPos> hidden = Set.of();
    private static volatile Map<BlockPos, Float> translucent = Map.of();
    private static volatile ConeVolume cone = ConeVolume.INACTIVE;

    public static boolean isHidden(BlockPos pos) {
        return !OVERLAY_RENDERING.get() && hidden.contains(pos);
    }

    public static boolean isCutaway(BlockPos pos) {
        return hidden.contains(pos);
    }

    public static Map<BlockPos, Float> translucentSnapshot() {
        return translucent;
    }

    public static void beginOverlayRender() {
        OVERLAY_RENDERING.set(true);
    }

    public static void endOverlayRender() {
        OVERLAY_RENDERING.set(false);
    }

    public static Set<BlockPos> snapshot() {
        return hidden;
    }

    public static boolean shouldHideEntity(Entity entity) {
        if (!(entity instanceof Painting) && !(entity instanceof ItemFrame)) {
            return false;
        }
        return cone.contains(entity.getBoundingBox());
    }

    public static void clear() {
        clearImmediately(Minecraft.getInstance());
    }

    private static void clearImmediately(Minecraft mc) {
        cone = ConeVolume.INACTIVE;
        translucent = Map.of();
        Set<BlockPos> old = hidden;
        if (old.isEmpty()) return;

        hidden = Set.of();
        markDirty(mc, old);
    }

    private static void closeGradually(Minecraft mc) {
        if (hidden.isEmpty()) {
            return;
        }

        HashSet<BlockPos> remaining = new HashSet<>();
        HashMap<BlockPos, Float> remainingTranslucent = new HashMap<>();
        for (BlockPos pos : hidden) {
            float previous = translucent.getOrDefault(pos, 0.0F);
            float opacity = Math.min(1.0F, previous + 1.0F / FULL_FADE_TICKS);
            if (opacity < 0.999F) {
                remaining.add(pos);
                remainingTranslucent.put(pos, opacity);
            }
        }

        Set<BlockPos> next = Set.copyOf(remaining);
        Set<BlockPos> old = hidden;
        if (!next.equals(old)) {
            hidden = next;
            translucent = Map.copyOf(remainingTranslucent);
            HashSet<BlockPos> changed = new HashSet<>(old);
            changed.removeAll(next);
            HashSet<BlockPos> newlyChanged = new HashSet<>(next);
            newlyChanged.removeAll(old);
            changed.addAll(newlyChanged);
            markDirty(mc, changed);
        } else {
            translucent = Map.copyOf(remainingTranslucent);
        }

        if (next.isEmpty()) {
            cone = ConeVolume.INACTIVE;
        }
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !ClientConfig.HIDE_CAMERA_OBSTRUCTIONS.get()
                || mc.options.getCameraType().isFirstPerson()) {
            clearImmediately(mc);
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 playerPos = mc.player.getEyePosition(1.0F)
                .add(0.0D, -0.30D, 0.0D);

        Vec3 cameraToPlayer = playerPos.subtract(cameraPos);
        double cameraDistance = cameraToPlayer.length();
        if (cameraDistance < 2.05D) {
            closeGradually(mc);
            return;
        }

        Vec3 axis = cameraToPlayer.scale(1.0D / cameraDistance);
        Vec3 right = cameraRight(axis);
        Vec3 up = right.cross(axis).normalize();

        /*
         * Test the center plus the four cardinal edges of the camera opening.
         * A wall clipping any one of these rays activates the cutaway early.
         */
        if (!hasCrossObstruction(mc, cameraPos, playerPos, right, up)) {
            closeGradually(mc);
            return;
        }

        // Start one block behind the camera and stop one block behind the
        // character, on the camera-facing side.
        Vec3 start = cameraPos.subtract(axis.scale(CAMERA_APEX_BACK_OFFSET));
        Vec3 end = playerPos.subtract(axis.scale(PLAYER_END_BACK_OFFSET));
        Vec3 shapeAxis = end.subtract(start);
        double shapeLength = shapeAxis.length();
        Vec3 shapeDirection = shapeAxis.scale(1.0D / shapeLength);
        double taperLength = Math.min(MAX_TAPER_LENGTH, shapeLength * 0.25D);

        double blockAllowance = Math.sqrt(3.0D) * 0.5D;
        double searchRadius = TUBE_RADIUS
                + OUTER_FADE_WIDTH
                + blockAllowance;

        int minX = (int) Math.floor(Math.min(start.x, end.x) - searchRadius);
        int minY = (int) Math.floor(Math.min(start.y, end.y) - searchRadius);
        int minZ = (int) Math.floor(Math.min(start.z, end.z) - searchRadius);
        int maxX = (int) Math.floor(Math.max(start.x, end.x) + searchRadius);
        int maxY = (int) Math.floor(Math.max(start.y, end.y) + searchRadius);
        int maxZ = (int) Math.floor(Math.max(start.z, end.z) + searchRadius);

        HashSet<BlockPos> targetMutable = new HashSet<>();
        HashMap<BlockPos, Float> targetTranslucent = new HashMap<>();
        HashMap<BlockPos, Integer> targetFadeTicks = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()
                            || state.getRenderShape() == RenderShape.INVISIBLE) {
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

                    double radius = radiusAt(
                            axialDistance,
                            shapeLength,
                            taperLength
                    );

                    Vec3 nearest = start.add(
                            shapeDirection.scale(axialDistance)
                    );
                    double distance = Math.sqrt(center.distanceToSqr(nearest));
                    double innerEdge = radius + blockAllowance;
                    double fadeEdge = innerEdge + OUTER_FADE_WIDTH;

                    BlockPos immutable = pos.immutable();
                    if (distance <= innerEdge) {
                        // Fully invisible center.
                        targetMutable.add(immutable);
                        targetTranslucent.put(immutable, 0.0F);
                        targetFadeTicks.put(
                                immutable,
                                fadeTicksFor(center.subtract(nearest), right, up)
                        );
                    } else if (distance <= fadeEdge) {
                        // Smoothly blend from zero visibility at the cutaway
                        // edge to full visibility across three outer rings.
                        double progress = (distance - innerEdge) / OUTER_FADE_WIDTH;
                        double smooth = progress * progress * (3.0D - 2.0D * progress);
                        targetMutable.add(immutable);
                        targetTranslucent.put(immutable, (float) smooth);
                        targetFadeTicks.put(immutable, FULL_FADE_TICKS);
                    }
                }
            }
        }

        cone = new ConeVolume(
                start,
                shapeDirection,
                shapeLength,
                taperLength,
                (int) Math.floor(mc.player.getY()) + 1
        );

        HashSet<BlockPos> animated = new HashSet<>(targetMutable);
        HashMap<BlockPos, Float> animatedTranslucent = new HashMap<>();
        for (BlockPos pos : targetMutable) {
            float target = targetTranslucent.getOrDefault(pos, 0.0F);
            float previous = hidden.contains(pos)
                    ? translucent.getOrDefault(pos, target)
                    : 1.0F;
            int duration = targetFadeTicks.getOrDefault(pos, FULL_FADE_TICKS);
            float opacity = Math.max(target, previous - 1.0F / duration);
            animatedTranslucent.put(pos, opacity);
        }

        // Blocks leaving a moving cutaway fade completely visible before
        // returning to the normal opaque chunk mesh.
        for (BlockPos pos : hidden) {
            if (targetMutable.contains(pos)) continue;
            float previous = translucent.getOrDefault(pos, 0.0F);
            float opacity = Math.min(1.0F, previous + 1.0F / FULL_FADE_TICKS);
            if (opacity < 0.999F) {
                animated.add(pos);
                animatedTranslucent.put(pos, opacity);
            }
        }

        Set<BlockPos> next = Set.copyOf(animated);
        Map<BlockPos, Float> nextFade = Map.copyOf(animatedTranslucent);
        Set<BlockPos> old = hidden;

        if (!next.equals(old)) {
            HashSet<BlockPos> changed = new HashSet<>(old);
            changed.removeAll(next);
            HashSet<BlockPos> newlyChanged = new HashSet<>(next);
            newlyChanged.removeAll(old);
            changed.addAll(newlyChanged);
            hidden = next;
            translucent = nextFade;
            markDirty(mc, changed);
        } else if (!nextFade.equals(translucent)) {
            translucent = nextFade;
        }
    }

    private static boolean hasCrossObstruction(
            Minecraft mc,
            Vec3 cameraPos,
            Vec3 playerPos,
            Vec3 right,
            Vec3 up
    ) {
        Vec3[] offsets = {
                Vec3.ZERO,
                right.scale(TRIGGER_RAY_OFFSET),
                right.scale(-TRIGGER_RAY_OFFSET),
                up.scale(TRIGGER_RAY_OFFSET),
                up.scale(-TRIGGER_RAY_OFFSET)
        };
        for (Vec3 offset : offsets) {
            HitResult hit = mc.level.clip(new ClipContext(
                    cameraPos.add(offset),
                    playerPos.add(offset),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (hit.getType() == HitResult.Type.BLOCK) return true;
        }
        return false;
    }

    private static Vec3 cameraRight(Vec3 axis) {
        Vec3 right = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : right.normalize();
    }

    private static int fadeTicksFor(Vec3 fromAxis, Vec3 right, Vec3 up) {
        double horizontal = fromAxis.dot(right);
        double vertical = fromAxis.dot(up);
        double centerDistance = Math.hypot(horizontal, vertical);
        if (centerDistance <= 0.65D) return CENTER_FADE_TICKS;

        double cardinalDistance = Math.min(
                Math.min(
                        Math.hypot(horizontal - TRIGGER_RAY_OFFSET, vertical),
                        Math.hypot(horizontal + TRIGGER_RAY_OFFSET, vertical)
                ),
                Math.min(
                        Math.hypot(horizontal, vertical - TRIGGER_RAY_OFFSET),
                        Math.hypot(horizontal, vertical + TRIGGER_RAY_OFFSET)
                )
        );
        if (cardinalDistance <= 0.75D) return CROSS_FADE_TICKS;
        return FULL_FADE_TICKS;
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

    private record ConeVolume(
            Vec3 start,
            Vec3 direction,
            double length,
            double taperLength,
            int minimumY
    ) {
        private static final ConeVolume INACTIVE =
                new ConeVolume(Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D, Integer.MAX_VALUE);

        private boolean contains(AABB box) {
            if (length <= 0.0D || box.maxY < minimumY) return false;

            Vec3 center = box.getCenter();
            double axial = center.subtract(start).dot(direction);
            if (axial < 0.0D || axial > length) return false;

            double radius = radiusAt(axial, length, taperLength);

            double entityAllowance = 0.5D * Math.sqrt(
                    box.getXsize() * box.getXsize()
                            + box.getYsize() * box.getYsize()
                            + box.getZsize() * box.getZsize()
            );
            Vec3 nearest = start.add(direction.scale(axial));
            double accepted = radius + entityAllowance;
            return center.distanceToSqr(nearest) <= accepted * accepted;
        }
    }

    private static double radiusAt(
            double axialDistance,
            double shapeLength,
            double taperLength
    ) {
        if (taperLength <= 0.0D) return END_RADIUS;
        if (axialDistance < taperLength) {
            return END_RADIUS
                    + (TUBE_RADIUS - END_RADIUS)
                    * (axialDistance / taperLength);
        }
        double closingStart = shapeLength - taperLength;
        if (axialDistance > closingStart) {
            return TUBE_RADIUS
                    + (END_RADIUS - TUBE_RADIUS)
                    * ((axialDistance - closingStart) / taperLength);
        }
        return TUBE_RADIUS;
    }

    private HiddenBlockManager() {}
}
