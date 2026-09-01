package com.blackforge.taczleawind;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CAMERA_RECOIL;
    public static final ForgeConfigSpec.BooleanValue DISABLE_CAMERA_COLLISION;
    public static final ForgeConfigSpec.BooleanValue HIDE_CAMERA_OBSTRUCTIONS;
    public static final ForgeConfigSpec.BooleanValue FORWARD_ONLY_TARGETING;
    public static final ForgeConfigSpec.DoubleValue HIDE_CORRIDOR_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MAX_THIRD_PERSON_DISTANCE;
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
                .comment("Hide blocks inside the camera-to-player obstruction tube.")
                .define("hideCameraObstructions", true);

        HIDE_CORRIDOR_RADIUS = b
                .comment("Radius in blocks around the camera-to-player line. 2.5 = five blocks wide.")
                .defineInRange("hideCorridorRadius", 2.5D, 2.5D, 5.0D);

        MAX_THIRD_PERSON_DISTANCE = b
                .comment("Maximum Leawind third-person camera distance in blocks.")
                .defineInRange("maximumThirdPersonDistance", 16.0D, 4.0D, 32.0D);
        b.pop();

        b.push("aiming");
        FORWARD_ONLY_TARGETING = b
                .comment("Ignore camera-side obstructions and reject targets behind the character.")
                .define("forwardOnlyTargeting", true);

        FORWARD_HEMISPHERE_DEGREES = b
                .comment("Half-angle of legal horizontal aiming cone. 90 = complete front hemisphere.")
                .defineInRange("forwardHemisphereDegrees", 90.0D, 30.0D, 90.0D);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}
}
