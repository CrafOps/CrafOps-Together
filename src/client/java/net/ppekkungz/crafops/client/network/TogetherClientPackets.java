package net.ppekkungz.crafops.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;           // ← correct import
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.UUID;

/**
 * TogetherClientPackets — Client-side packet receivers.
 *
 * Lives in the client sourceSet so it can safely import client-only
 * classes (MinecraftClient, ClientPlayNetworking) without breaking
 * dedicated server compilation.
 *
 * Called from CrafOpsUtilsClient during client init.
 */
public class TogetherClientPackets {

    /**
     * Register all S2C receivers.
     * Must be called on the client only.
     */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
                TogetherPackets.S2C_CONNECT,
                TogetherClientPackets::handleS2CConnect
        );
        ClientPlayNetworking.registerGlobalReceiver(
                TogetherPackets.S2C_DISCONNECT,
                TogetherClientPackets::handleS2CDisconnect
        );
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static void handleS2CConnect(MinecraftClient client,
                                         ClientPlayNetworkHandler handler,
                                         PacketByteBuf buf,
                                         PacketSender sender) {
        UUID initiatorUuid = TogetherPackets.readUuid(buf);
        UUID targetUuid    = TogetherPackets.readUuid(buf);

        // Always mutate client state on the main thread
        client.execute(() ->
                ClientConnectionCache.addConnection(initiatorUuid, targetUuid)
        );
    }

    private static void handleS2CDisconnect(MinecraftClient client,
                                            ClientPlayNetworkHandler handler,
                                            PacketByteBuf buf,
                                            PacketSender sender) {
        UUID playerUuid = TogetherPackets.readUuid(buf);

        client.execute(() ->
                ClientConnectionCache.removeConnection(playerUuid)
        );
    }

    private TogetherClientPackets() {}
}