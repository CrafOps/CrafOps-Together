package net.ppekkungz.crafops.network;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.*;
import java.util.*;

/** Versioned, bounded network protocol. Server state is authoritative. */
public final class TogetherPackets {
    public static final int MAX_SYNCED_LINKS = 4096;
    public static final Identifier S2C_SNAPSHOT = id("snapshot_v2");
    public static final Identifier S2C_REQUEST = id("incoming_request_v2");
    public static final Identifier S2C_SETTINGS = id("settings_v2");
    public static final Identifier C2S_RESPONSE = id("request_response");
    public static final Identifier C2S_DISCONNECT = id("disconnect");
    public static final Identifier C2S_PARTNER_STATE = id("partner_state");
    public static final Identifier S2C_PARTNER_STATE = id("partner_state");
    /** Relayed states reach players this close to the sender (partners plus anyone watching). */
    private static final double RELAY_RANGE = 64.0;
    /** A client may report its position this far from where the server has it; further means a lie or a desync. */
    private static final double MAX_REPORTED_DRIFT = 1.5;

    private static final Map<UUID, Integer> lastRelayTick = new HashMap<>();
    private static final Map<UUID, Vec3d> lastServerPosition = new HashMap<>();
    private static final ArrayDeque<DelayedRelay> delayedRelays = new ArrayDeque<>();
    private static int simulatedLagMillis;

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_PARTNER_STATE, (server, player, handler, buf, sender) -> {
            PartnerState state = PartnerState.read(buf);
            server.execute(() -> relayPartnerState(server, player, state, true));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_RESPONSE, (server, player, handler, buf, sender) -> {
            UUID requestId = readUuid(buf); boolean accepted = buf.readBoolean();
            server.execute(() -> InteractionManager.getInstance().respond(player, requestId, accepted));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_DISCONNECT, (server, player, handler, buf, sender) ->
                server.execute(() -> InteractionManager.getInstance().disconnectPlayer(player)));
    }

    /** Forwards a holding player's own tick state to nearby players at once, instead of via the entity tracker. */
    private static void relayPartnerState(MinecraftServer server, ServerPlayerEntity sender, PartnerState state, boolean fromClient) {
        if (!InteractionManager.getInstance().isConnected(sender.getUuid())) return;
        if (fromClient && Objects.equals(lastRelayTick.put(sender.getUuid(), server.getTicks()), server.getTicks())) return;
        if (sender.getPos().squaredDistanceTo(state.x(), state.y(), state.z()) > MAX_REPORTED_DRIFT * MAX_REPORTED_DRIFT) return;
        PacketByteBuf buf = PacketByteBufs.create();
        writeUuid(buf, sender.getUuid());
        state.write(buf);
        for (ServerPlayerEntity viewer : sender.getServerWorld().getPlayers()) {
            if (viewer == sender || viewer.squaredDistanceTo(sender) > RELAY_RANGE * RELAY_RANGE) continue;
            if (simulatedLagMillis > 0) delayedRelays.add(new DelayedRelay(viewer.getUuid(), PacketByteBufs.copy(buf), System.currentTimeMillis() + simulatedLagMillis));
            else send(viewer, S2C_PARTNER_STATE, PacketByteBufs.copy(buf));
        }
    }

    /**
     * Each tick: relays a server-side state for holding players whose client did not send one (server-driven
     * fake players, clients without the mod), and sends relays held back by the debug lag simulator.
     */
    public static void tick(MinecraftServer server) {
        for (UUID id : new ArrayList<>(InteractionManager.getInstance().getConnectedPlayers())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) continue;
            Vec3d previous = lastServerPosition.put(id, player.getPos());
            Integer reported = lastRelayTick.get(id);
            if (reported != null && server.getTicks() - reported <= 2 || previous == null) continue;
            Vec3d moved = player.getPos().subtract(previous);
            byte flags = (byte) ((player.isOnGround() ? PartnerState.ON_GROUND : 0) | (player.isTouchingWater() ? PartnerState.IN_WATER : 0)
                    | (player.getAbilities().flying ? PartnerState.FLYING : 0) | (player.isSprinting() ? PartnerState.SPRINTING : 0)
                    | (player.isSwimming() ? PartnerState.SWIMMING : 0));
            relayPartnerState(server, player, new PartnerState(player.getX(), player.getY(), player.getZ(),
                    (float) moved.x, (float) moved.y, (float) moved.z, player.bodyYaw, flags), false);
        }
        lastServerPosition.keySet().retainAll(InteractionManager.getInstance().getConnectedPlayers());
        long now = System.currentTimeMillis();
        while (!delayedRelays.isEmpty() && delayedRelays.peekFirst().dueAtMillis() <= now) {
            DelayedRelay relay = delayedRelays.pollFirst();
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(relay.viewer());
            if (viewer != null) send(viewer, S2C_PARTNER_STATE, relay.buf());
        }
        lastRelayTick.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
    }

    /** Debug: delays partner relays to imitate a high-ping connection. 0 turns it off. */
    public static void setSimulatedLag(int millis) { simulatedLagMillis = Math.max(0, millis); }
    public static int simulatedLag() { return simulatedLagMillis; }

    private record DelayedRelay(UUID viewer, PacketByteBuf buf, long dueAtMillis) {}

    public static boolean canReceiveRequests(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, S2C_REQUEST);
    }

    /** Sends the remaining lifetime rather than a wall-clock deadline, so clock skew cannot break the countdown. */
    public static void sendIncomingRequest(ServerPlayerEntity recipient, PendingHandHoldRequest request, String senderName, long lifetimeMillis) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeUuid(buf, request.id()); writeUuid(buf, request.sender());
        buf.writeString(senderName, 64); buf.writeVarInt((int) lifetimeMillis);
        send(recipient, S2C_REQUEST, buf);
    }

    public static void sendSnapshot(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        Collection<HandHoldComponent> links = InteractionManager.getInstance().getAllComponents();
        buf.writeVarInt(Math.min(links.size(), MAX_SYNCED_LINKS));
        int sent = 0;
        for (HandHoldComponent link : links) {
            if (sent++ >= MAX_SYNCED_LINKS) break;
            writeUuid(buf, link.getId()); writeUuid(buf, link.getLeaderUuid()); writeUuid(buf, link.getFollowerUuid());
        }
        // Clients cannot see other players' abilities, so the server tells them who in a chain is flying.
        Collection<UUID> flying = InteractionManager.getInstance().getFlyingPlayers();
        buf.writeVarInt(Math.min(flying.size(), MAX_SYNCED_LINKS));
        int written = 0;
        for (UUID id : flying) { if (written++ >= MAX_SYNCED_LINKS) break; writeUuid(buf, id); }
        send(player, S2C_SNAPSHOT, buf);
        sendSettings(player);
    }

    public static void sendSettings(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        TogetherConfig.get().formation().write(buf);
        send(player, S2C_SETTINGS, buf);
    }

    public static void broadcastSnapshot(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendSnapshot(player);
    }

    private static void send(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        if (ServerPlayNetworking.canSend(player, channel)) ServerPlayNetworking.send(player, channel, buf);
    }
    public static void writeUuid(PacketByteBuf buf, UUID id) { buf.writeLong(id.getMostSignificantBits()); buf.writeLong(id.getLeastSignificantBits()); }
    public static UUID readUuid(PacketByteBuf buf) { return new UUID(buf.readLong(), buf.readLong()); }
    private static Identifier id(String path) { return new Identifier("crafops_together", path); }
    private TogetherPackets() {}
}
