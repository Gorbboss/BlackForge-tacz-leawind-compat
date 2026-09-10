package com.blackforge.taczleawind.network;

import com.blackforge.taczleawind.BlackForgeCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TacticalAttackNetwork {
    private static final String PROTOCOL = "3";
    private static final Map<UUID, Float> ACTIVE_PLAYERS = new ConcurrentHashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(BlackForgeCompat.MOD_ID, "tactical_attack"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public static void initialize() {
        CHANNEL.messageBuilder(StateMessage.class, 0)
                .encoder(StateMessage::encode)
                .decoder(StateMessage::decode)
                .consumerMainThread(StateMessage::handle)
                .add();
    }

    public static void send(boolean active, float facingYaw) {
        CHANNEL.sendToServer(new StateMessage(active, facingYaw));
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE_PLAYERS.containsKey(player.getUUID());
    }

    public static float getFacingYaw(ServerPlayer player) {
        return ACTIVE_PLAYERS.getOrDefault(player.getUUID(), player.yBodyRot);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_PLAYERS.remove(event.getEntity().getUUID());
    }

    private record StateMessage(boolean active, float facingYaw) {
        private static void encode(StateMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.active);
            buffer.writeFloat(message.facingYaw);
        }

        private static StateMessage decode(FriendlyByteBuf buffer) {
            return new StateMessage(buffer.readBoolean(), buffer.readFloat());
        }

        private static void handle(StateMessage message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                if (message.active && Float.isFinite(message.facingYaw)) {
                    ACTIVE_PLAYERS.put(sender.getUUID(), message.facingYaw);
                }
                else ACTIVE_PLAYERS.remove(sender.getUUID());
            }
            context.setPacketHandled(true);
        }
    }

    private TacticalAttackNetwork() {}
}
