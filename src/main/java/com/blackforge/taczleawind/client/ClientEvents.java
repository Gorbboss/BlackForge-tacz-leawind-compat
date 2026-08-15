package com.blackforge.taczleawind.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Entity Culling 1.20.1 respects Entity#noCulling. Exempt only the
        // local player; every other entity remains eligible for culling.
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            net.minecraft.client.Minecraft.getInstance().player.noCulling = true;
        }

        ScopedFirstPersonController.update();
        ForwardAimGuard.enforce();
        HiddenBlockManager.update();
    }

    private ClientEvents() {}
}
