package com.blackforge.taczleawind;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CAMERA_RECOIL;
    public static final ForgeConfigSpec.BooleanValue DISABLE_CAMERA_COLLISION;
    public static final ForgeConfigSpec.BooleanValue HIDE_CAMERA_OBSTRUCTIONS;
    public static final ForgeConfigSpec.BooleanValue FORWARD_ONLY_TARGETING;
    public static final ForgeConfigSpec.DoubleValue HIDE_CORRIDOR_RADIUS;
    public static final ForgeConfigSpec.DoubleValue FORWARD_HEMISPHERE_DEGREES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TACTICAL_FORWARD_ITEMS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("camera");
        CAMERA_RECOIL = b
                .comment("Allow TaCZ recoil to rotate the third-person camera. Default false.")
                .define("cameraRecoil", false);

        DISABLE_CAMERA_COLLISION = b
                .comment("Prevent terrain from pushing the third-person camera toward the player.")
                .define("disableCameraCollision", true);

        HIDE_CAMERA_OBSTRUCTIONS = b
                .comment("Hide blocks inside the camera-to-player obstruction tube.")
                .define("hideCameraObstructions", true);

        HIDE_CORRIDOR_RADIUS = b
                .comment("Radius in blocks around the camera-to-player line. 2.5 = five blocks wide.")
                .defineInRange("hideCorridorRadius", 2.5D, 2.5D, 5.0D);

        b.pop();

        b.push("aiming");
        FORWARD_ONLY_TARGETING = b
                .comment("Ignore camera-side obstructions and reject targets behind the character.")
                .define("forwardOnlyTargeting", true);

        FORWARD_HEMISPHERE_DEGREES = b
                .comment("Half-angle of legal horizontal aiming cone. 90 = complete front hemisphere.")
                .defineInRange("forwardHemisphereDegrees", 90.0D, 30.0D, 90.0D);

        TACTICAL_FORWARD_ITEMS = b
                .comment("Extra item IDs that attack straight ahead while BlackForge Movement is in Tactical stance.",
                        "Swords, axes, and TaCZ guns are included automatically.",
                        "Example: [\"minecraft:trident\", \"othermod:combat_knife\"]")
                .defineListAllowEmpty(
                        List.of("tacticalForwardItems"),
                        List::of,
                        value -> value instanceof String id
                                && net.minecraft.resources.ResourceLocation.tryParse(id) != null
                );
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}
}
