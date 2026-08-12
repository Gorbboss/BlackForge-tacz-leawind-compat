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

        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01D) {
            clear();
            return;
        }

        Vec3 dir = delta.scale(1.0D / length);
        double radius = ClientConfig.HIDE_CORRIDOR_RADIUS.get();

        HashSet<BlockPos> next = new HashSet<>();
        double step = 0.20D;

        for (double d = 0.0D; d <= length; d += step) {
            Vec3 p = from.add(dir.scale(d));
            int r = radius > 0.45D ? 1 : 0;

            BlockPos center = BlockPos.containing(p);
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockState state = mc.level.getBlockState(pos);
                        if (!state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty()) {
                            next.add(pos.immutable());
                        }
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

    private static void markDirty(Minecraft mc, Set<BlockPos> positions) {
        if (mc.levelRenderer == null) return;
        for (BlockPos p : positions) {
            mc.levelRenderer.setBlockDirty(p, mc.level != null ? mc.level.getBlockState(p) : null, mc.level != null ? mc.level.getBlockState(p) : null);
        }
    }

    private HiddenBlockManager() {}
}
