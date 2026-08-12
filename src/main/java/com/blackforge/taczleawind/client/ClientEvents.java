package com.blackforge.taczleawind.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ForwardAimGuard.enforce();
        HiddenBlockManager.update();
    }

    private ClientEvents() {}
}
