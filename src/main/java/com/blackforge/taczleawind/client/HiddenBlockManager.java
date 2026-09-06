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
    private static final float VISIBILITY_STEP = 1.0F / 20.0F;
    private static final ThreadLocal<Boolean> OVERLAY_RENDERING =
            ThreadLocal.withInitial(() -> false);

    private static volatile Set<BlockPos> hidden = Set.of();
    private static volatile Map<BlockPos, Float> translucent = Map.of();
    private static volatile Set<BoundaryFace> blackBoundaryFaces = Set.of();
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

    public static Set<BoundaryFace> blackBoundarySnapshot() {
        return blackBoundaryFaces;
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
        blackBoundaryFaces = Set.of();
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
        if (hidden.isEmpty()) {
            if (!ShaderCutawayState.snapshot().active()) {
                blackBoundaryFaces = Set.of();
                cone = ConeVolume.INACTIVE;
            }
            return;
        }
        HashSet<BlockPos> remaining = new HashSet<>();
        HashMap<BlockPos, Float> opacities = new HashMap<>();
        for (BlockPos pos : hidden) {
            float opacity = Math.min(1.0F,
                    translucent.getOrDefault(pos, 0.0F) + VISIBILITY_STEP);
            if (opacity < 0.999F) {
                remaining.add(pos);
                opacities.put(pos, opacity);
            }
        }

        publish(mc, remaining, opacities);
        if (remaining.isEmpty()) {
            cone = ConeVolume.INACTIVE;
            blackBoundaryFaces = Set.of();
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
        boolean overheadClearance = cameraPos.y >= mc.player.getY() + 1.0D;

        Vec3 cameraToPlayer = playerPos.subtract(cameraPos);
        double cameraDistance = cameraToPlayer.length();
        if (cameraDistance < 2.05D) {
            ShaderCutawayState.deactivateSmoothly();
            if (ShaderPackDetector.isShaderPackActive()) {
                clearHiddenBlocks(mc);
                if (!ShaderCutawayState.snapshot().active()) blackBoundaryFaces = Set.of();
            } else closeSmoothly(mc);
            return;
        }

        Vec3 axis = cameraToPlayer.scale(1.0D / cameraDistance);
        Vec3 right = cameraRight(axis);
        Vec3 up = right.cross(axis).normalize();

        /*
         * Test the center plus the four cardinal edges of the camera opening.
         * A wall clipping any one of these rays activates the cutaway early.
         */
        CutawayObstruction obstruction = findObstruction(mc, cameraPos, playerPos, right, up);
        if (!obstruction.any() && !overheadClearance) {
            ShaderCutawayState.deactivateSmoothly();
            if (ShaderPackDetector.isShaderPackActive()) {
                clearHiddenBlocks(mc);
                if (!ShaderCutawayState.snapshot().active()) blackBoundaryFaces = Set.of();
            } else closeSmoothly(mc);
            return;
        }

        // Start one block behind the camera and stop one block behind the
        // character, on the camera-facing side.
        Vec3 start = cameraPos.subtract(axis.scale(CAMERA_APEX_BACK_OFFSET));
        double normalEndDistance = Math.max(1.0D,
                cameraDistance - PLAYER_END_BACK_OFFSET);
        double cutawayEndDistance = obstruction.any()
                ? Math.min(normalEndDistance, obstruction.lastDistance() + 0.85D)
                : normalEndDistance;
        Vec3 end = cameraPos.add(axis.scale(cutawayEndDistance));
        Vec3 shapeAxis = end.subtract(start);
        double shapeLength = shapeAxis.length();
        Vec3 shapeDirection = shapeAxis.scale(1.0D / shapeLength);
        double taperLength = Math.min(MAX_TAPER_LENGTH, shapeLength * 0.25D);

        cone = new ConeVolume(
                start,
                shapeDirection,
                shapeLength,
                taperLength,
                (int) Math.floor(mc.player.getY()) + 1
        );
        ShaderCutawayState.activate(
                cameraBlock, start, end, right, up, taperLength,
                END_RADIUS, TUBE_RADIUS, OUTER_FADE_WIDTH,
                obstruction, overheadClearance
        );

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
        HashSet<BlockPos> cutawayVolume = new HashSet<>();
        HashMap<BlockPos, Float> targetTranslucent = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
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
                    boolean cameraClearance = overheadClearance
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
                    double sectorStrength = obstruction.strength(
                            center.subtract(nearest), right, up);
                    double innerEdge = centerEdge
                            + Math.max(0.0D, radius + blockAllowance - centerEdge)
                            * sectorStrength;
                    double fadeEdge = innerEdge + OUTER_FADE_WIDTH;

                    BlockPos immutable = pos.immutable();
                    boolean insideCenter = (obstruction.any() && distance <= innerEdge)
                            || cameraClearance;
                    boolean insideTransition = obstruction.any()
                            && distance <= fadeEdge
                            && obstruction.enables(center.subtract(nearest), right, up);
                    if (!insideCenter && !insideTransition) continue;

                    // The mathematical volume includes air so the fake
                    // black-concrete boundary cannot develop side openings.
                    cutawayVolume.add(immutable);

                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()
                            || state.getRenderShape() == RenderShape.INVISIBLE) continue;

                    if (insideCenter) {
                        // Fully invisible center.
                        targetMutable.add(immutable);
                        targetTranslucent.put(immutable, 0.0F);
                    } else {
                        // Smoothly blend from zero visibility at the cutaway
                        // edge to full visibility across three outer rings.
                        double progress = (distance - innerEdge) / OUTER_FADE_WIDTH;
                        double smooth = progress * progress * (3.0D - 2.0D * progress);
                        targetMutable.add(immutable);
                        targetTranslucent.put(immutable, (float) smooth);
                    }
                }
            }
        }

        HashSet<BoundaryFace> boundary = new HashSet<>();
        int playerFeetY = (int) Math.floor(mc.player.getY());
        // Preserve the previously working boundary where the cutaway meets
        // actual terrain.
        for (BlockPos cutawayPos : targetMutable) {
            for (Direction outward : Direction.values()) {
                BlockPos shellPos = cutawayPos.relative(outward);
                if (targetMutable.contains(shellPos)) continue;
                if (outward == Direction.DOWN && shellPos.getY() >= playerFeetY) continue;
                BlockState shellState = mc.level.getBlockState(shellPos);
                if (!shellState.isAir()
                        && shellState.getRenderShape() != RenderShape.INVISIBLE) {
                    boundary.add(new BoundaryFace(
                            shellPos.immutable(), outward.getOpposite()));
                }
            }
        }

        // The only air-generated geometry is the outer radial wall of an
        // activated wedge. It extends along the corridor toward the camera;
        // axial faces are rejected so this cannot form camera-covering caps.
        for (BlockPos cutawayPos : cutawayVolume) {
            Vec3 center = Vec3.atCenterOf(cutawayPos);
            double axial = center.subtract(start).dot(shapeDirection);
            Vec3 nearest = start.add(shapeDirection.scale(axial));
            Vec3 radial = center.subtract(nearest);
            if (radial.lengthSqr() < 1.0E-6D
                    || obstruction.strength(radial, right, up) <= 0.0F) continue;
            Vec3 radialDirection = radial.normalize();
            for (Direction outward : Direction.values()) {
                BlockPos shellPos = cutawayPos.relative(outward);
                if (cutawayVolume.contains(shellPos)) continue;
                if (!mc.level.getBlockState(shellPos).isAir()) continue;
                if (outward == Direction.DOWN && shellPos.getY() >= playerFeetY) continue;
                Vec3 outwardVector = new Vec3(
                        outward.getStepX(), outward.getStepY(), outward.getStepZ());
                if (Math.abs(outwardVector.dot(shapeDirection)) > 0.55D) continue;
                if (outwardVector.dot(radialDirection) <= 0.25D) continue;
                boundary.add(new BoundaryFace(
                        shellPos.immutable(), outward.getOpposite()));
            }
        }
        blackBoundaryFaces = Set.copyOf(boundary);

        /* Shader packs perform the transparency mask themselves. The mod only
         * supplies the air-safe black-concrete boundary in this path. */
        if (ShaderPackDetector.isShaderPackActive()) {
            clearHiddenBlocks(mc);
            return;
        }

        HashSet<BlockPos> animated = new HashSet<>();
        HashMap<BlockPos, Float> animatedOpacity = new HashMap<>();
        for (BlockPos pos : targetMutable) {
            float target = targetTranslucent.getOrDefault(pos, 0.0F);
            float previous = translucent.containsKey(pos)
                    ? translucent.get(pos) : 1.0F;
            // The center corridor and camera-clearance box open immediately.
            float opacity = target <= 0.001F ? 0.0F
                    : moveToward(previous, target, VISIBILITY_STEP);
            animated.add(pos);
            animatedOpacity.put(pos, opacity);
        }
        // Everything leaving the moving cutaway takes one second to return.
        for (BlockPos pos : translucent.keySet()) {
            if (targetMutable.contains(pos)) continue;
            float opacity = Math.min(1.0F,
                    translucent.getOrDefault(pos, 0.0F) + VISIBILITY_STEP);
            if (opacity < 0.999F) {
                animated.add(pos);
                animatedOpacity.put(pos, opacity);
            }
        }
        publish(mc, animated, animatedOpacity);
    }

    private static float moveToward(float value, float target, float amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
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
        boolean any = false;
        double lastDistance = 0.0D;
        float[] sectors = new float[8];
        for (int horizontal = -1; horizontal <= 1; horizontal++) {
            for (int vertical = -1; vertical <= 1; vertical++) {
                Vec3 offset = right.scale(horizontal * TRIGGER_RAY_OFFSET)
                        .add(up.scale(vertical * TRIGGER_RAY_OFFSET));
                double rayLast = lastObstructionDistance(
                        mc, cameraPos.add(offset), playerPos.add(offset));
                if (rayLast < 0.0D) continue;
                any = true;
                lastDistance = Math.max(lastDistance, rayLast);
                if (horizontal == 0 && vertical == 0) continue;
                int sector = sectorIndex(horizontal, vertical);
                sectors[sector] = 1.0F;
                sectors[(sector + 1) & 7] = Math.max(sectors[(sector + 1) & 7], 0.65F);
                sectors[(sector + 7) & 7] = Math.max(sectors[(sector + 7) & 7], 0.65F);
            }
        }
        return new CutawayObstruction(any, lastDistance, sectors);
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

    record CutawayObstruction(boolean any, double lastDistance, float[] sectors) {
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
