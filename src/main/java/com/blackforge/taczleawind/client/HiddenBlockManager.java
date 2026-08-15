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
    private static final double CONE_HALF_ANGLE_RADIANS = Math.toRadians(35.0D);
    private static final double PLAYER_END_RADIUS = 1.5D;

    /*
     * Embeddium builds chunk meshes on worker threads. Always publish a
     * complete immutable snapshot.
     */
    private static final double FIRST_FADE_SHELL_WIDTH = 1.0D;
    private static final double SECOND_FADE_SHELL_WIDTH = 1.0D;
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
        cone = ConeVolume.INACTIVE;
        Map<BlockPos, Float> oldTranslucent = translucent;
        Set<BlockPos> old = hidden;
        translucent = Map.of();
        hidden = Set.of();
        if (!old.isEmpty() || !oldTranslucent.isEmpty()) {
            markDirty(Minecraft.getInstance(), old);
        }
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

        /*
         * Do not create a cutaway in open space. OUTLINE includes rendered
         * non-solid models such as grass, cobwebs, doors and trapdoors.
         */
        if (!hasMeaningfulObstruction(mc, cameraPos, playerPos)) {
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
        double searchRadius = maximumRadius
                + FIRST_FADE_SHELL_WIDTH
                + SECOND_FADE_SHELL_WIDTH
                + blockAllowance;

        int minX = (int) Math.floor(Math.min(start.x, end.x) - searchRadius);
        int minY = (int) Math.floor(Math.min(start.y, end.y) - searchRadius);
        int minZ = (int) Math.floor(Math.min(start.z, end.z) - searchRadius);
        int maxX = (int) Math.floor(Math.max(start.x, end.x) + searchRadius);
        int maxY = (int) Math.floor(Math.max(start.y, end.y) + searchRadius);
        int maxZ = (int) Math.floor(Math.max(start.z, end.z) + searchRadius);

        HashSet<BlockPos> nextMutable = new HashSet<>();
        HashMap<BlockPos, Float> nextTranslucent = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()
                            || state.getRenderShape() == RenderShape.INVISIBLE
                            || isIgnoredVegetation(state)) {
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
                    double distance = Math.sqrt(center.distanceToSqr(nearest));
                    double innerEdge = radius + blockAllowance;
                    double firstEdge = innerEdge + FIRST_FADE_SHELL_WIDTH;
                    double secondEdge = firstEdge + SECOND_FADE_SHELL_WIDTH;

                    BlockPos immutable = pos.immutable();
                    if (distance <= innerEdge) {
                        nextMutable.add(immutable);
                        nextTranslucent.put(immutable, 0.0F);
                    } else if (distance <= firstEdge) {
                        nextMutable.add(immutable);
                        nextTranslucent.put(immutable, 0.30F);
                    } else if (distance <= secondEdge) {
                        nextMutable.add(immutable);
                        nextTranslucent.put(immutable, 0.70F);
                    }
                }
            }
        }

        cone = new ConeVolume(
                start,
                shapeDirection,
                shapeLength,
                midpoint,
                maximumRadius,
                (int) Math.floor(mc.player.getY()) + 1
        );

        Set<BlockPos> old = hidden;
        Set<BlockPos> nextHidden = Set.copyOf(nextMutable);
        translucent = Map.copyOf(nextTranslucent);
        hidden = nextHidden;

        if (!old.equals(nextHidden)) {
            HashSet<BlockPos> changed = new HashSet<>(old);
            changed.addAll(nextHidden);
            markDirty(mc, changed);
        }
    }

    private static boolean hasMeaningfulObstruction(
            Minecraft mc,
            Vec3 cameraPos,
            Vec3 playerPos
    ) {
        Vec3 direction = playerPos.subtract(cameraPos).normalize();
        Vec3 from = cameraPos;

        // Skip replaceable vegetation hits and continue the visual ray.
        for (int i = 0; i < 16; i++) {
            HitResult hit = mc.level.clip(new ClipContext(
                    from,
                    playerPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (hit.getType() != HitResult.Type.BLOCK) return false;

            BlockPos pos = BlockPos.containing(hit.getLocation()
                    .add(direction.scale(0.001D)));
            BlockState state = mc.level.getBlockState(pos);
            if (!isIgnoredVegetation(state)) return true;

            from = hit.getLocation().add(direction.scale(0.05D));
            if (from.distanceToSqr(playerPos) < 0.01D) return false;
        }
        return false;
    }

    private static boolean isIgnoredVegetation(BlockState state) {
        return state.canBeReplaced()
                && state.getCollisionShape(
                        net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                        BlockPos.ZERO
                ).isEmpty();
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
            double midpoint,
            double maximumRadius,
            int minimumY
    ) {
        private static final ConeVolume INACTIVE =
                new ConeVolume(Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D, 0.0D, Integer.MAX_VALUE);

        private boolean contains(AABB box) {
            if (length <= 0.0D || box.maxY < minimumY) return false;

            Vec3 center = box.getCenter();
            double axial = center.subtract(start).dot(direction);
            if (axial < 0.0D || axial > length) return false;

            double radius;
            if (axial <= midpoint) {
                radius = Math.tan(CONE_HALF_ANGLE_RADIANS) * axial;
            } else {
                double progress = (axial - midpoint) / (length - midpoint);
                radius = maximumRadius
                        + (PLAYER_END_RADIUS - maximumRadius) * progress;
            }

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

    private HiddenBlockManager() {}
}
