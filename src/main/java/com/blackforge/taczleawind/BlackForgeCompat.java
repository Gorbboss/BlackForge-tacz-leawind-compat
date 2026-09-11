package com.blackforge.taczleawind;

import com.blackforge.taczleawind.client.ClientEvents;
import com.blackforge.taczleawind.client.EmfAimIntegration;
import com.blackforge.taczleawind.network.TacticalAttackNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BlackForgeCompat.MOD_ID)
public final class BlackForgeCompat {
    public static final String MOD_ID = "blackforge_tacz_leawind_compat";

    public BlackForgeCompat() {
        TacticalAttackNetwork.initialize();
        MinecraftForge.EVENT_BUS.register(TacticalAttackNetwork.class);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                ClientConfig.SPEC,
                "blackforge-tacz-leawind.toml"
        );
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientOnly::register);
    }

    private static final class ClientOnly {
        private static void register() {
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
            FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener(ClientOnly::clientSetup);
        }

        private static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(EmfAimIntegration::register);
        }
    }
}
