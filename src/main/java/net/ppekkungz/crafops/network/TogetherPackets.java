package net.ppekkungz.crafops.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.ppekkungz.crafops.client.network.ClientPayloadHandlers;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.*;
import net.ppekkungz.crafops.network.TogetherPayloads.*;

import java.util.*;

/** Versioned, bounded network protocol. Server state is authoritative. */
public final class TogetherPackets {
    private static final String PROTOCOL_VERSION = "2";
    /** Relayed states reach players this close to the sender (partners plus anyone watching). */
    private static final double RELAY_RANGE = 64.0;
    /** A client may report its position this far from where the server has it; further means a lie or a desync. */
    private static final double MAX_REPORTED_DRIFT = 1.5;

    private static final Map<UUID, Integer> lastRelayTick = new HashMap<>();
    private static final Map<UUID, Vec3> lastServerPosition = new HashMap<>();
    private static final ArrayDeque<DelayedRelay> delayedRelays = new ArrayDeque<>();
    private static int simulatedLagMillis;

    /**
     * Optional, so players without the mod can still join: they get a chat prompt instead of the HUD.
     * Client-bound handlers live in {@link ClientPayloadHandlers}, which is only loaded when one runs on a client.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> ClientPayloadHandlers.snapshot(payload));
        registrar.playToClient(IncomingRequest.TYPE, IncomingRequest.CODEC, (payload, context) -> ClientPayloadHandlers.incomingRequest(payload));
        registrar.playToClient(Settings.TYPE, Settings.CODEC, (payload, context) -> ClientPayloadHandlers.settings(payload));
        registrar.playToClient(RelayedState.TYPE, RelayedState.CODEC, (payload, context) -> ClientPayloadHandlers.relayedState(payload));
        registrar.playToServer(Response.TYPE, Response.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                InteractionManager.getInstance().respond(player, payload.requestId(), payload.accepted());
        });
        registrar.playToServer(OwnState.TYPE, OwnState.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) relayPartnerState(player.getServer(), player, payload.state(), true);
        });
    }

    /** Forwards a holding player's own tick state to nearby players at once, instead of via the entity tracker. */
    private static void relayPartnerState(MinecraftServer server, ServerPlayer sender, PartnerState state, boolean fromClient) {
        if (!InteractionManager.getInstance().isConnected(sender.getUUID())) return;
        if (fromClient && Objects.equals(lastRelayTick.put(sender.getUUID(), server.getTickCount()), server.getTickCount())) return;
        if (sender.position().distanceToSqr(state.x(), state.y(), state.z()) > MAX_REPORTED_DRIFT * MAX_REPORTED_DRIFT) return;
        RelayedState payload = new RelayedState(sender.getUUID(), state);
        for (ServerPlayer viewer : sender.serverLevel().players()) {
            if (viewer == sender || viewer.distanceToSqr(sender) > RELAY_RANGE * RELAY_RANGE) continue;
            if (simulatedLagMillis > 0) delayedRelays.add(new DelayedRelay(viewer.getUUID(), payload, System.currentTimeMillis() + simulatedLagMillis));
            else send(viewer, payload);
        }
    }

    /**
     * Each tick: relays a server-side state for holding players whose client did not send one (clients without
     * the mod, server-driven fake players), and sends relays held back by the debug lag simulator.
     */
    public static void tick(MinecraftServer server) {
        for (UUID id : new ArrayList<>(InteractionManager.getInstance().getConnectedPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            Vec3 previous = lastServerPosition.put(id, player.position());
            Integer reported = lastRelayTick.get(id);
            if (reported != null && server.getTickCount() - reported <= 2 || previous == null) continue;
            Vec3 moved = player.position().subtract(previous);
            byte flags = (byte) ((player.onGround() ? PartnerState.ON_GROUND : 0) | (player.isInWater() ? PartnerState.IN_WATER : 0)
                    | (player.getAbilities().flying ? PartnerState.FLYING : 0) | (player.isSprinting() ? PartnerState.SPRINTING : 0)
                    | (player.isSwimming() ? PartnerState.SWIMMING : 0));
            relayPartnerState(server, player, new PartnerState(player.getX(), player.getY(), player.getZ(),
                    (float) moved.x, (float) moved.y, (float) moved.z, player.yBodyRot, flags), false);
        }
        lastServerPosition.keySet().retainAll(InteractionManager.getInstance().getConnectedPlayers());
        long now = System.currentTimeMillis();
        while (!delayedRelays.isEmpty() && delayedRelays.peekFirst().dueAtMillis() <= now) {
            DelayedRelay relay = delayedRelays.pollFirst();
            ServerPlayer viewer = server.getPlayerList().getPlayer(relay.viewer());
            if (viewer != null) send(viewer, relay.payload());
        }
        lastRelayTick.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Debug: delays partner relays to imitate a high-ping connection. 0 turns it off. */
    public static void setSimulatedLag(int millis) { simulatedLagMillis = Math.max(0, millis); }
    public static int simulatedLag() { return simulatedLagMillis; }

    private record DelayedRelay(UUID viewer, RelayedState payload, long dueAtMillis) {}

    public static boolean canReceiveRequests(ServerPlayer player) {
        return player.connection.hasChannel(IncomingRequest.TYPE);
    }

    public static void sendIncomingRequest(ServerPlayer recipient, PendingHandHoldRequest request, String senderName, long lifetimeMillis) {
        send(recipient, new IncomingRequest(request.id(), request.sender(), senderName, (int) lifetimeMillis));
    }

    public static void sendSnapshot(ServerPlayer player) {
        List<TogetherPayloads.Link> links = new ArrayList<>();
        for (HandHoldComponent link : InteractionManager.getInstance().getAllComponents()) {
            if (links.size() >= TogetherPayloads.MAX_SYNCED_LINKS) break;
            links.add(new TogetherPayloads.Link(link.getId(), link.getLeaderUuid(), link.getFollowerUuid()));
        }
        List<UUID> flying = new ArrayList<>(InteractionManager.getInstance().getFlyingPlayers());
        if (flying.size() > TogetherPayloads.MAX_SYNCED_LINKS) flying = flying.subList(0, TogetherPayloads.MAX_SYNCED_LINKS);
        send(player, new Snapshot(links, flying));
        send(player, new Settings(TogetherConfig.get().formation()));
    }

    public static void broadcastSnapshot(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendSnapshot(player);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    private TogetherPackets() {}
}
