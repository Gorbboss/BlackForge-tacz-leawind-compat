package com.blackforge.taczleawind;

import com.blackforge.taczleawind.client.ClientEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(BlackForgeCompat.MOD_ID)
public final class BlackForgeCompat {
    public static final String MOD_ID = "blackforge_tacz_leawind_compat";

    public BlackForgeCompat() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                ClientConfig.SPEC,
                "blackforge-tacz-leawind.toml"
        );
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
    }
}
