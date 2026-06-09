package net.ppekkungz.crafops.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.ppekkungz.crafops.interaction.InteractionManager;

import java.util.UUID;

/**
 * TogetherPackets — Server-side packet definitions.
 *
 * Client receivers are registered separately in:
 *   client/network/TogetherClientPackets.java
 *
 * This class is safe to load on dedicated servers (no client imports).
 */
public class TogetherPackets {

    // ── Packet Identifiers ────────────────────────────────────────────────────

    public static final Identifier S2C_CONNECT =
            new Identifier("crafops_together", "s2c_connect");

    public static final Identifier S2C_DISCONNECT =
            new Identifier("crafops_together", "s2c_disconnect");

    public static final Identifier C2S_REQUEST_DISCONNECT =
            new Identifier("crafops_together", "c2s_request_disconnect");

    // ── Server-side Registration ──────────────────────────────────────────────

    /**
     * Register C2S receivers.
     * Called from CrafOpsUtils (main entrypoint).
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                C2S_REQUEST_DISCONNECT,
                TogetherPackets::handleClientDisconnectRequest
        );
    }

    // ── Server → Client Senders ───────────────────────────────────────────────

    public static void sendConnectPacket(ServerPlayerEntity initiator,
                                         ServerPlayerEntity target) {
        // Send to initiator
        PacketByteBuf buf1 = PacketByteBufs.create();
        writeUuid(buf1, initiator.getUuid());
        writeUuid(buf1, target.getUuid());
        ServerPlayNetworking.send(initiator, S2C_CONNECT, buf1);

        // Send to target
        PacketByteBuf buf2 = PacketByteBufs.create();
        writeUuid(buf2, initiator.getUuid());
        writeUuid(buf2, target.getUuid());
        ServerPlayNetworking.send(target, S2C_CONNECT, buf2);

        // Broadcast to nearby observers
        broadcastConnectToNearby(initiator, target);
    }

    public static void sendDisconnectPacket(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeUuid(buf, player.getUuid());
        ServerPlayNetworking.send(player, S2C_DISCONNECT, buf);
    }

    private static void broadcastConnectToNearby(ServerPlayerEntity initiator,
                                                 ServerPlayerEntity target) {
        final double broadcastRange = 64.0;

        initiator.getServer().getPlayerManager().getPlayerList().forEach(observer -> {
            if (observer.getUuid().equals(initiator.getUuid())) return;
            if (observer.getUuid().equals(target.getUuid())) return;
            if (!observer.getWorld().equals(initiator.getWorld())) return;
            if (observer.getPos().distanceTo(initiator.getPos()) > broadcastRange) return;

            PacketByteBuf buf = PacketByteBufs.create();
            writeUuid(buf, initiator.getUuid());
            writeUuid(buf, target.getUuid());
            ServerPlayNetworking.send(observer, S2C_CONNECT, buf);
        });
    }

    // ── Server-side Receivers ─────────────────────────────────────────────────

    private static void handleClientDisconnectRequest(MinecraftServer server,
                                                      ServerPlayerEntity player,
                                                      ServerPlayNetworkHandler handler,
                                                      PacketByteBuf buf,
                                                      PacketSender sender) {
        InteractionManager.getInstance().disconnect(player, true);
    }

    // ── UUID Helpers ──────────────────────────────────────────────────────────

    public static void writeUuid(PacketByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(PacketByteBuf buf) {
        long msb = buf.readLong();
        long lsb = buf.readLong();
        return new UUID(msb, lsb);
    }

    private TogetherPackets() {}
}