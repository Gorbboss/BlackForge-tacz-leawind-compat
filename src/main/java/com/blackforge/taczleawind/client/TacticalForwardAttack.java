package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.ClientConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;

/** Optional bridge to BlackForge Movement's client-side Tactical setting. */
public final class TacticalForwardAttack {
    private static final String MOVEMENT_MOD_ID = "blackforge_movement";
    private static volatile boolean lookupAttempted;
    private static Field tacticalValueField;
    private static boolean useWasDown;

    public static boolean isEligibleThirdPerson() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                || !ModList.get().isLoaded(MOVEMENT_MOD_ID)) {
            return false;
        }
        return isEligible(player.getMainHandItem());
    }

    /** Tactical hip-fire continuously follows the camera crosshair. */
    public static boolean isTacticalCrosshairMode() {
        return isEligibleThirdPerson()
                && movementTacticalEnabled()
                && !isAdsRequested();
    }

    /** Passive hip-fire remains level and follows the character's facing. */
    public static boolean isPassiveForwardMode() {
        return isEligibleThirdPerson()
                && !movementTacticalEnabled()
                && !isAdsRequested();
    }

    public static boolean isAdsRequested() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && (mc.options.keyUse.isDown()
                || ScopedFirstPersonController.isAiming(mc.player));
    }

    /** Removes the stale backwards-walking body yaw on the first ADS tick. */
    public static void updateAdsTransition() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean useDown = player != null && mc.options.keyUse.isDown();
        if (useDown && !useWasDown && isEligibleThirdPerson()
                && movementTacticalEnabled()) {
            player.yBodyRot = player.getYRot();
            player.yBodyRotO = player.getYRot();
            player.yHeadRot = player.getYRot();
            player.yHeadRotO = player.getYRot();
        }
        useWasDown = useDown;
    }

    public static boolean shouldSuppressLeawindInteraction() {
        Minecraft mc = Minecraft.getInstance();
        return isPassiveForwardMode()
                && mc.options.keyAttack.isDown()
                && !mc.options.keyUse.isDown()
                && !mc.options.keyPickItem.isDown();
    }

    public static boolean isEligible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem || isTaczGun(item.getClass())) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && ClientConfig.TACTICAL_FORWARD_ITEMS.get().stream()
                .anyMatch(configured -> id.toString().equals(configured));
    }

    public static boolean movementTacticalEnabled() {
        try {
            if (!lookupAttempted) {
                lookupAttempted = true;
                Class<?> config = Class.forName("com.blackforge.movement.client.ClientConfig");
                tacticalValueField = config.getField("TACTICAL_STANCE");
            }
            if (tacticalValueField == null) return false;
            Object configValue = tacticalValueField.get(null);
            Object result = configValue.getClass().getMethod("get").invoke(configValue);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            tacticalValueField = null;
            return false;
        }
    }

    private static boolean isTaczGun(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if ("com.tacz.guns.api.item.IGun".equals(current.getName())) return true;
            for (Class<?> iface : current.getInterfaces()) {
                if ("com.tacz.guns.api.item.IGun".equals(iface.getName())) return true;
            }
        }
        return false;
    }

    private TacticalForwardAttack() {}
}
