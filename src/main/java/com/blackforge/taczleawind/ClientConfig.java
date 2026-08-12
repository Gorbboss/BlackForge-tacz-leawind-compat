package com.blackforge.taczleawind;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CAMERA_RECOIL;
    public static final ForgeConfigSpec.BooleanValue DISABLE_CAMERA_COLLISION;
    public static final ForgeConfigSpec.BooleanValue HIDE_CAMERA_OBSTRUCTIONS;
    public static final ForgeConfigSpec.BooleanValue FORWARD_ONLY_TARGETING;
    public static final ForgeConfigSpec.DoubleValue HIDE_CORRIDOR_RADIUS;
    public static final ForgeConfigSpec.DoubleValue FORWARD_HEMISPHERE_DEGREES;

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
                .comment("Hide only blocks intersecting the camera-to-player corridor.")
                .define("hideCameraObstructions", true);

        HIDE_CORRIDOR_RADIUS = b
                .comment("Approximate radius in blocks around the camera-to-player line to hide.")
                .defineInRange("hideCorridorRadius", 0.32D, 0.05D, 0.75D);
        b.pop();

        b.push("aiming");
        FORWARD_ONLY_TARGETING = b
                .comment("Reject crosshair targets behind the character.")
                .define("forwardOnlyTargeting", true);

        FORWARD_HEMISPHERE_DEGREES = b
                .comment("Half-angle of legal horizontal aiming cone. 90 = complete front hemisphere.")
                .defineInRange("forwardHemisphereDegrees", 90.0D, 30.0D, 90.0D);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}
}
