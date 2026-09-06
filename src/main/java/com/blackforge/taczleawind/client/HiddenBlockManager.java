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
    private static final double NEAR_PLAYER_END_OFFSET = 0.25D;
    // Compact main tube (about 2x2) and wall-mode midpoint (about 4x4).
    private static final double END_RADIUS = 1.0D;
    private static final double TUBE_RADIUS = 2.0D;

    /*
     * Embeddium builds chunk meshes on worker threads. Always publish a
     * complete immutable snapshot.
     */
    private static final double TRIGGER_RAY_OFFSET = 0.85D;
    private static final double MIDPOINT_WEDGE_SAMPLE_OFFSET = 1.75D;
    private static final double RELEASE_OVERLAP = 1.15D;
    private static final ThreadLocal<Boolean> OVERLAY_RENDERING =
            ThreadLocal.withInitial(() -> false);

    private static volatile Set<BlockPos> hidden = Set.of();
    private static volatile Map<BlockPos, Float> translucent = Map.of();
    private static volatile Map<BoundaryFace, Float> boundaryFaces = Map.of();
    private static volatile Map<BlockPos, Integer> retainedLayers = Map.of();
    private static volatile Map<BlockPos, Double> retainedReleaseRadii = Map.of();
    private static double retainedEndDistance = -1.0D;
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

    public static Map<BoundaryFace, Float> boundarySnapshot() {
        return boundaryFaces;
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
        ShaderCutawayState.clear();
        cone = ConeVolume.INACTIVE;
        boundaryFaces = Map.of();
        retainedLayers = Map.of();
        retainedReleaseRadii = Map.of();
        retainedEndDistance = -1.0D;
        clearHiddenBlocks(mc);
    }

    private static void clearHiddenBlocks(Minecraft mc) {
        translucent = Map.of();
        Set<BlockPos> old = hidden;
        if (old.isEmpty()) return;

        hidden = Set.of();
        markDirty(mc, old);
    }

    private static void closeSmoothly(Minecraft mc) {
        updateBoundary(Set.of());
        clearHiddenBlocks(mc);
        cone = ConeVolume.INACTIVE;
        retainedLayers = Map.of();
        retainedReleaseRadii = Map.of();
        retainedEndDistance = -1.0D;
    }

    private static void closeShaderOverlay(Minecraft mc) {
        updateBoundary(Set.of());
        translucent = Map.of();
        retainedLayers = Map.of();
        retainedReleaseRadii = Map.of();
        retainedEndDistance = -1.0D;
        if (!hidden.isEmpty()) {
            Set<BlockPos> old = hidden;
            hidden = Set.of();
            markDirty(mc, old);
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
        BlockPos cameraBlock = BlockPos.containing(cameraPos);
        Vec3 playerPos = mc.player.getEyePosition(1.0F)
                .add(0.0D, -0.30D, 0.0D);

        Vec3 cameraToPlayer = playerPos.subtract(cameraPos);
        double cameraDistance = cameraToPlayer.length();
        if (cameraDistance < 2.05D) {
            ShaderCutawayState.deactivateSmoothly();
            if (ShaderPackDetector.isShaderPackActive()) {
                closeShaderOverlay(mc);
            } else closeSmoothly(mc);
            return;
        }

        Vec3 axis = cameraToPlayer.scale(1.0D / cameraDistance);
        Vec3 right = cameraRight(axis);
        Vec3 up = right.cross(axis).normalize();

        // Normal mode listens only to center and top-center. Directional
        // wedges are populated only while the camera itself is inside terrain.
        CutawayObstruction obstruction = findObstruction(mc, cameraPos, playerPos, right, up);
        if (!obstruction.any()) {
            ShaderCutawayState.deactivateSmoothly();
            if (ShaderPackDetector.isShaderPackActive()) {
                closeShaderOverlay(mc);
            } else closeSmoothly(mc);
            return;
        }

        // Start one block behind the camera. The player-side end is selected
        // independently for the center and each of the eight screen sectors.
        Vec3 start = cameraPos.subtract(axis.scale(CAMERA_APEX_BACK_OFFSET));
        double normalEndDistance = Math.max(1.0D,
                cameraDistance - NEAR_PLAYER_END_OFFSET);
        double rawEndDistance = normalEndDistance;
        double cutawayEndDistance = rawEndDistance;
        if (retainedEndDistance > rawEndDistance
                && retainedEndDistance <= rawEndDistance * RELEASE_OVERLAP) {
            cutawayEndDistance = Math.min(normalEndDistance, retainedEndDistance);
        }
        retainedEndDistance = cutawayEndDistance;
        Vec3 end = cameraPos.add(axis.scale(cutawayEndDistance));
        Vec3 shapeAxis = end.subtract(start);
        double shapeLength = shapeAxis.length();
        Vec3 shapeDirection = shapeAxis.scale(1.0D / shapeLength);
        double taperLength = shapeLength * 0.5D;

        cone = new ConeVolume(
                start,
                shapeDirection,
                shapeLength,
                taperLength,
                (int) Math.floor(mc.player.getY()) + 1,
                right,
                up,
                obstruction
        );
        ShaderCutawayState.activate(
                cameraBlock, start, end, right, up, taperLength,
                END_RADIUS, TUBE_RADIUS, 0.0D,
                obstruction, obstruction.cameraInside()
        );

        double blockAllowance = Math.sqrt(3.0D) * 0.5D;
        double searchRadius = TUBE_RADIUS + blockAllowance;
        searchRadius *= RELEASE_OVERLAP;

        int minX = (int) Math.floor(Math.min(start.x, end.x) - searchRadius);
        int minY = (int) Math.floor(Math.min(start.y, end.y) - searchRadius);
        int minZ = (int) Math.floor(Math.min(start.z, end.z) - searchRadius);
        int maxX = (int) Math.floor(Math.max(start.x, end.x) + searchRadius);
        int maxY = (int) Math.floor(Math.max(start.y, end.y) + searchRadius);
        int maxZ = (int) Math.floor(Math.max(start.z, end.z) + searchRadius);

        HashSet<BlockPos> targetMutable = new HashSet<>();
        HashMap<BlockPos, Float> targetTranslucent = new HashMap<>();
        HashMap<BlockPos, Integer> nextLayers = new HashMap<>();
        HashMap<BlockPos, Double> nextReleaseRadii = new HashMap<>();

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

                    if (axialDistance < 0.0D || axialDistance > shapeLength) {
                        continue;
                    }

                    // Keep the cutaway one full block above the surface the
                    // player is standing on. Camera pitch cannot lower it.
                    int minimumHiddenY = (int) Math.floor(mc.player.getY()) + 1;
                    boolean cameraClearance = obstruction.cameraInside()
                            && isInsideCameraClearance(pos, cameraPos);
                    if (pos.getY() < minimumHiddenY && !cameraClearance) {
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
                    double centerEdge = END_RADIUS + blockAllowance;
                    Vec3 fromAxis = center.subtract(nearest);
                    double horizontal = fromAxis.dot(right);
                    double vertical = fromAxis.dot(up);
                    double sectorStrength = obstruction.strength(fromAxis, right, up);
                    double innerEdge = centerEdge
                            + Math.max(0.0D, radius + blockAllowance - centerEdge)
                            * sectorStrength;
                    BlockPos immutable = pos.immutable();
                    int normalLayer = distance <= innerEdge ? 0 : -1;
                    if (!obstruction.any() && !cameraClearance) normalLayer = -1;
                    Integer previousLayer = retainedLayers.get(immutable);
                    int selectedLayer = normalLayer;
                    double previousRelease = retainedReleaseRadii.getOrDefault(
                            immutable,
                            previousLayer == null ? -1.0D
                                    : releaseBoundary(innerEdge));
                    boolean retainedPrevious = false;
                    if (previousLayer != null
                            && (normalLayer < 0 || normalLayer > previousLayer)
                            && distance <= previousRelease) {
                        selectedLayer = previousLayer;
                        retainedPrevious = true;
                    }
                    if (cameraClearance) {
                        selectedLayer = 0;
                        retainedPrevious = false;
                    }
                    if (selectedLayer < 0) continue;

                    nextLayers.put(immutable, selectedLayer);
                    nextReleaseRadii.put(immutable, retainedPrevious
                            ? previousRelease
                            : releaseBoundary(innerEdge));
                    // The compact tube and any enabled wall wedge are fully
                    // invisible; there are no outer transparency rings.
                    targetMutable.add(immutable);
                    targetTranslucent.put(immutable, 0.0F);
                }
            }
        }
        retainedLayers = Map.copyOf(nextLayers);
        retainedReleaseRadii = Map.copyOf(nextReleaseRadii);

        HashSet<BoundaryFace> boundary = new HashSet<>();
        for (BlockPos cutawayPos : targetMutable) {
            for (Direction outward : Direction.values()) {
                BlockPos shellPos = cutawayPos.relative(outward);
                if (targetMutable.contains(shellPos)) continue;
                BlockState shellState = mc.level.getBlockState(shellPos);
                if (!shellState.isAir()
                        && shellState.getRenderShape() != RenderShape.INVISIBLE) {
                    boundary.add(new BoundaryFace(shellPos.immutable(), outward.getOpposite()));
                }
            }
        }
        updateBoundary(boundary);

        // Visibility now switches immediately in both directions.
        if (ShaderPackDetector.isShaderPackActive()) {
            Set<BlockPos> oldHidden = hidden;
            hidden = Set.of();
            translucent = Map.copyOf(targetTranslucent);
            if (!oldHidden.isEmpty()) markDirty(mc, oldHidden);
            return;
        }
        publish(mc, targetMutable, targetTranslucent);
    }

    private static void updateBoundary(Set<BoundaryFace> target) {
        HashMap<BoundaryFace, Float> next = new HashMap<>();
        for (BoundaryFace face : target) {
            next.put(face, 1.0F);
        }
        boundaryFaces = Map.copyOf(next);
    }

    private static double releaseBoundary(double innerEdge) {
        return innerEdge * RELEASE_OVERLAP;
    }

    private static void publish(
            Minecraft mc, Set<BlockPos> positions, Map<BlockPos, Float> opacities
    ) {
        Set<BlockPos> next = Set.copyOf(positions);
        Map<BlockPos, Float> nextFade = Map.copyOf(opacities);
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

    private static CutawayObstruction findObstruction(
            Minecraft mc,
            Vec3 cameraPos,
            Vec3 playerPos,
            Vec3 right,
            Vec3 up
    ) {
        double centerLast = lastObstructionDistance(mc, cameraPos, playerPos);
        double topLast = lastObstructionDistance(
                mc,
                cameraPos.add(up.scale(TRIGGER_RAY_OFFSET)),
                playerPos.add(up.scale(TRIGGER_RAY_OFFSET))
        );
        boolean cameraInside = isSolidAt(mc, BlockPos.containing(cameraPos));
        boolean any = centerLast >= 0.0D || topLast >= 0.0D || cameraInside;
        double lastDistance = 0.0D;
        float[] sectors = new float[8];
        if (centerLast >= 0.0D) lastDistance = Math.max(lastDistance, centerLast);
        if (topLast >= 0.0D) lastDistance = Math.max(lastDistance, topLast);

        if (cameraInside) {
            Vec3 midpoint = cameraPos.add(playerPos).scale(0.5D);
            for (int horizontal = -1; horizontal <= 1; horizontal++) {
                for (int vertical = -1; vertical <= 1; vertical++) {
                    if (horizontal == 0 && vertical == 0) continue;
                    Vec3 sample = midpoint
                            .add(right.scale(horizontal * MIDPOINT_WEDGE_SAMPLE_OFFSET))
                            .add(up.scale(vertical * MIDPOINT_WEDGE_SAMPLE_OFFSET));
                    if (!isSolidAt(mc, BlockPos.containing(sample))) continue;
                    int sector = sectorIndex(horizontal, vertical);
                    sectors[sector] = 1.0F;
                    sectors[(sector + 1) & 7] = Math.max(
                            sectors[(sector + 1) & 7], 0.65F);
                    sectors[(sector + 7) & 7] = Math.max(
                            sectors[(sector + 7) & 7], 0.65F);
                }
            }
        }
        return new CutawayObstruction(any, cameraInside, lastDistance, sectors);
    }

    private static boolean isSolidAt(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir()
                && !state.canBeReplaced()
                && state.getRenderShape() != RenderShape.INVISIBLE
                && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private static double lastObstructionDistance(
            Minecraft mc, Vec3 start, Vec3 end
    ) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.001D) return -1.0D;
        Vec3 direction = delta.scale(1.0D / length);
        BlockPos previous = null;
        double last = -1.0D;
        for (double distance = 0.0D; distance <= length; distance += 0.20D) {
            BlockPos pos = BlockPos.containing(start.add(direction.scale(distance)));
            if (pos.equals(previous)) continue;
            previous = pos;
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()
                    && !state.canBeReplaced()
                    && state.getRenderShape() != RenderShape.INVISIBLE
                    && !state.getCollisionShape(mc.level, pos).isEmpty()) {
                last = distance;
            }
        }
        return last;
    }

    private static int sectorIndex(double horizontal, double vertical) {
        double angle = Math.atan2(vertical, horizontal);
        return ((int) Math.round(angle / (Math.PI / 4.0D)) + 8) & 7;
    }

    private static Vec3 cameraRight(Vec3 axis) {
        Vec3 right = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : right.normalize();
    }

    private static boolean isInsideCameraClearance(BlockPos pos, Vec3 camera) {
        int minX = (int) Math.floor(camera.x - 0.5D);
        int minY = (int) Math.floor(camera.y - 0.5D);
        int minZ = (int) Math.floor(camera.z - 0.5D);
        return pos.getX() >= minX && pos.getX() <= minX + 1
                && pos.getY() >= minY && pos.getY() <= minY + 1
                && pos.getZ() >= minZ && pos.getZ() <= minZ + 1;
    }

    record CutawayObstruction(
            boolean any, boolean cameraInside,
            double lastDistance, float[] sectors
    ) {
        float strength(Vec3 fromAxis, Vec3 rightVector, Vec3 upVector) {
            double horizontal = fromAxis.dot(rightVector);
            double vertical = fromAxis.dot(upVector);
            if (Math.abs(horizontal) < 0.001D && Math.abs(vertical) < 0.001D) {
                return 1.0F;
            }
            return sectors[sectorIndex(horizontal, vertical)];
        }

        boolean enables(Vec3 fromAxis, Vec3 rightVector, Vec3 upVector) {
            return strength(fromAxis, rightVector, upVector) > 0.0F;
        }

        float sector(int index) {
            return sectors[index & 7];
        }
    }

    public record BoundaryFace(BlockPos pos, Direction face) {}

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
            int minimumY,
            Vec3 right,
            Vec3 up,
            CutawayObstruction obstruction
    ) {
        private static final ConeVolume INACTIVE =
                new ConeVolume(
                        Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D,
                        Integer.MAX_VALUE, Vec3.ZERO, Vec3.ZERO,
                        new CutawayObstruction(false, false, 0.0D, new float[8])
                );

        private boolean contains(AABB box) {
            if (length <= 0.0D || box.maxY < minimumY) return false;

            Vec3 center = box.getCenter();
            double axial = center.subtract(start).dot(direction);
            if (axial < 0.0D || axial > length) return false;

            double entityAllowance = 0.5D * Math.sqrt(
                    box.getXsize() * box.getXsize()
                            + box.getYsize() * box.getYsize()
                            + box.getZsize() * box.getZsize()
            );
            Vec3 nearest = start.add(direction.scale(axial));
            Vec3 fromAxis = center.subtract(nearest);
            double wedgeStrength = obstruction.strength(fromAxis, right, up);
            double radius = END_RADIUS + (radiusAt(axial, length, taperLength)
                    - END_RADIUS) * wedgeStrength;
            double accepted = radius + entityAllowance;
            return center.distanceToSqr(nearest) <= accepted * accepted;
        }
    }

    private static double radiusAt(
            double axialDistance,
            double shapeLength,
            double taperLength
    ) {
        if (shapeLength <= 0.0D) return END_RADIUS;
        double progress = Math.max(0.0D, Math.min(1.0D,
                axialDistance / shapeLength));
        double middleStrength = 1.0D - Math.abs(progress * 2.0D - 1.0D);
        return END_RADIUS + (TUBE_RADIUS - END_RADIUS) * middleStrength;
    }

    private HiddenBlockManager() {}
}
