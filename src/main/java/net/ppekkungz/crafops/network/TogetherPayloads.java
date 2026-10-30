package net.ppekkungz.crafops.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ppekkungz.crafops.CrafOpsTogether;
import net.ppekkungz.crafops.interaction.FormationSettings;
import net.ppekkungz.crafops.interaction.PartnerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every packet the mod sends. Lists are bounded on read so a hostile peer cannot request huge allocations. */
public final class TogetherPayloads {
    public static final int MAX_SYNCED_LINKS = 4096;
    public static final int MAX_REQUEST_LIFETIME_MILLIS = 120_000;

    public record Link(UUID id, UUID leader, UUID follower) {}

    /** S2C: the full set of links plus who in a chain is flying (clients cannot see others' abilities). */
    public record Snapshot(List<Link> links, List<UUID> flying) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = typeOf("snapshot");
        public static final StreamCodec<FriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.links.size());
            for (Link link : value.links) { buf.writeUUID(link.id()); buf.writeUUID(link.leader()); buf.writeUUID(link.follower()); }
            buf.writeVarInt(value.flying.size());
            for (UUID id : value.flying) buf.writeUUID(id);
        }, buf -> {
            int count = Math.min(buf.readVarInt(), MAX_SYNCED_LINKS);
            List<Link> links = new ArrayList<>(count);
            for (int i = 0; i < count; i++) links.add(new Link(buf.readUUID(), buf.readUUID(), buf.readUUID()));
            int flyingCount = Math.min(buf.readVarInt(), MAX_SYNCED_LINKS);
            List<UUID> flying = new ArrayList<>(flyingCount);
            for (int i = 0; i < flyingCount; i++) flying.add(buf.readUUID());
            return new Snapshot(links, flying);
        });
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    /** S2C: someone reached out a hand. Carries the remaining lifetime, not a wall-clock deadline, so clock skew cannot break the countdown. */
    public record IncomingRequest(UUID id, UUID sender, String senderName, int lifetimeMillis) implements CustomPacketPayload {
        public static final Type<IncomingRequest> TYPE = typeOf("incoming_request");
        public static final StreamCodec<FriendlyByteBuf, IncomingRequest> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUUID(value.id); buf.writeUUID(value.sender); buf.writeUtf(value.senderName, 64); buf.writeVarInt(value.lifetimeMillis);
        }, buf -> new IncomingRequest(buf.readUUID(), buf.readUUID(), buf.readUtf(64), Math.min(buf.readVarInt(), MAX_REQUEST_LIFETIME_MILLIS)));
        @Override public Type<IncomingRequest> type() { return TYPE; }
    }

    /** S2C: the formation config every client needs to drive and draw a hand-hold identically. */
    public record Settings(FormationSettings settings) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = typeOf("settings");
        public static final StreamCodec<FriendlyByteBuf, Settings> CODEC =
                StreamCodec.of((buf, value) -> value.settings.write(buf), buf -> new Settings(FormationSettings.read(buf)));
        @Override public Type<Settings> type() { return TYPE; }
    }

    /** C2S: take or leave a reaching hand. */
    public record Response(UUID requestId, boolean accepted) implements CustomPacketPayload {
        public static final Type<Response> TYPE = typeOf("request_response");
        public static final StreamCodec<FriendlyByteBuf, Response> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.requestId); buf.writeBoolean(value.accepted); },
                buf -> new Response(buf.readUUID(), buf.readBoolean()));
        @Override public Type<Response> type() { return TYPE; }
    }

    /** C2S: this player's own tick state, see {@link PartnerState}. */
    public record OwnState(PartnerState state) implements CustomPacketPayload {
        public static final Type<OwnState> TYPE = typeOf("own_state");
        public static final StreamCodec<FriendlyByteBuf, OwnState> CODEC =
                StreamCodec.of((buf, value) -> value.state.write(buf), buf -> new OwnState(PartnerState.read(buf)));
        @Override public Type<OwnState> type() { return TYPE; }
    }

    /** S2C: another holding player's tick state, relayed without waiting for the entity tracker. */
    public record RelayedState(UUID player, PartnerState state) implements CustomPacketPayload {
        public static final Type<RelayedState> TYPE = typeOf("relayed_state");
        public static final StreamCodec<FriendlyByteBuf, RelayedState> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.player); value.state.write(buf); },
                buf -> new RelayedState(buf.readUUID(), PartnerState.read(buf)));
        @Override public Type<RelayedState> type() { return TYPE; }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CrafOpsTogether.MOD_ID, path));
    }

    private TogetherPayloads() {}
}
