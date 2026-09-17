package net.ppekkungz.crafops.network;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.*;
import java.util.*;

/** Versioned, bounded network protocol. Server state is authoritative. */
public final class TogetherPackets {
    private static final int MAX_SYNCED_LINKS = 4096;
    public static final Identifier S2C_SNAPSHOT = id("snapshot");
    public static final Identifier S2C_REQUEST = id("incoming_request");
    public static final Identifier S2C_SETTINGS = id("settings");
    public static final Identifier C2S_RESPONSE = id("request_response");
    public static final Identifier C2S_DISCONNECT = id("disconnect");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_RESPONSE, (server, player, handler, buf, sender) -> {
            UUID requestId = readUuid(buf); boolean accepted = buf.readBoolean();
            server.execute(() -> InteractionManager.getInstance().respond(player, requestId, accepted));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_DISCONNECT, (server, player, handler, buf, sender) ->
                server.execute(() -> InteractionManager.getInstance().disconnectPlayer(player)));
    }

    public static void sendIncomingRequest(ServerPlayerEntity recipient, PendingHandHoldRequest request, String senderName) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeUuid(buf, request.id()); writeUuid(buf, request.sender());
        buf.writeString(senderName, 64); buf.writeLong(request.expiresAtMillis());
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
        send(player, S2C_SNAPSHOT, buf);
        sendSettings(player);
    }

    public static void sendSettings(ServerPlayerEntity player) {
        TogetherConfig cfg = TogetherConfig.get();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(cfg.enableVisualConnection); buf.writeDouble(cfg.visualRenderDistance);
        buf.writeDouble(cfg.formationSideOffset);
        buf.writeDouble(cfg.formationBehindOffset);
        buf.writeDouble(cfg.formationTolerance);
        buf.writeDouble(cfg.formationSpringStrength);
        buf.writeDouble(cfg.formationAcceleration);
        buf.writeDouble(cfg.formationMaxCorrectionSpeed);
        buf.writeDouble(cfg.leaderVelocityMatch);
        buf.writeDouble(cfg.stepAssistVelocity);
        buf.writeDouble(cfg.visualHandGap);
        buf.writeDouble(cfg.maxVisualPositionCorrection);
        buf.writeDouble(cfg.movingFormationBehindOffset);
        buf.writeDouble(cfg.movementDetectionSpeed);
        buf.writeDouble(cfg.movementBlendSpeed);
        buf.writeDouble(cfg.maxJumpArmLift);
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
