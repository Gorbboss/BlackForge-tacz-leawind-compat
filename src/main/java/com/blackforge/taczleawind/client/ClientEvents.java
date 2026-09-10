package com.blackforge.taczleawind.client;

import com.blackforge.taczleawind.network.TacticalAttackNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    private static boolean lastTacticalAttackState;
    private static boolean tacticalStateSent;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Entity Culling 1.20.1 respects Entity#noCulling. Exempt only the
        // local player; every other entity remains eligible for culling.
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            net.minecraft.client.Minecraft.getInstance().player.noCulling = true;
        }

        ScopedFirstPersonController.update();
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getConnection() != null) {
            boolean passiveForwardState = TacticalForwardAttack.isPassiveForwardMode();
            if (!tacticalStateSent || passiveForwardState != lastTacticalAttackState) {
                TacticalAttackNetwork.send(passiveForwardState);
                lastTacticalAttackState = passiveForwardState;
                tacticalStateSent = true;
            }
        } else {
            tacticalStateSent = false;
        }
        LeawindZoomIntegration.update();
        HiddenBlockManager.update();
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        TranslucentCutawayRenderer.render(event);
    }

    private ClientEvents() {}
}
