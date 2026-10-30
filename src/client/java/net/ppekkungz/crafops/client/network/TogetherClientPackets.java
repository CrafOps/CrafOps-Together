package net.ppekkungz.crafops.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.interaction.FormationSettings;
import net.ppekkungz.crafops.interaction.PartnerState;
import net.ppekkungz.crafops.network.TogetherPackets;
import java.util.*;

public final class TogetherClientPackets {
    private static final int MAX_REQUEST_LIFETIME_MILLIS = 120_000;

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_SNAPSHOT, (client, handler, buf, sender) -> {
            int count = Math.min(buf.readVarInt(), TogetherPackets.MAX_SYNCED_LINKS);
            List<ClientConnectionCache.Link> links = new ArrayList<>(count);
            for (int i = 0; i < count; i++) links.add(new ClientConnectionCache.Link(
                    TogetherPackets.readUuid(buf), TogetherPackets.readUuid(buf), TogetherPackets.readUuid(buf)));
            int flyingCount = Math.min(buf.readVarInt(), TogetherPackets.MAX_SYNCED_LINKS);
            Set<UUID> flying = new HashSet<>(flyingCount);
            for (int i = 0; i < flyingCount; i++) flying.add(TogetherPackets.readUuid(buf));
            client.execute(() -> {
                ClientConnectionCache.replace(links);
                ClientConnectionCache.setFlying(flying);
                if (client.player != null) HandHoldRequestQueue.removeConnected(client.player.getUuid(), links);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_PARTNER_STATE, (client, handler, buf, sender) -> {
            UUID player = TogetherPackets.readUuid(buf);
            PartnerState state = PartnerState.read(buf);
            client.execute(() -> ClientConnectionCache.receivePartnerState(player, state));
        });
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_SETTINGS, (client, handler, buf, sender) -> {
            FormationSettings settings = FormationSettings.read(buf);
            client.execute(() -> ClientConnectionCache.setSettings(settings));
        });
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_REQUEST, (client, handler, buf, sender) -> {
            UUID id = TogetherPackets.readUuid(buf), from = TogetherPackets.readUuid(buf);
            String name = buf.readString(64);
            long lifetime = Math.min(buf.readVarInt(), MAX_REQUEST_LIFETIME_MILLIS);
            client.execute(() -> HandHoldRequestQueue.enqueue(id, from, name, lifetime));
        });
    }

    /** While holding hands, reports this tick's own movement so partners can skip the vanilla tracker delay. */
    public static void sendOwnState(MinecraftClient client) {
        ClientPlayerEntity self = client.player;
        if (self == null || !ClientConnectionCache.isConnected(self.getUuid())
                || !ClientPlayNetworking.canSend(TogetherPackets.C2S_PARTNER_STATE)) return;
        byte flags = (byte) ((self.isOnGround() ? PartnerState.ON_GROUND : 0) | (self.isTouchingWater() ? PartnerState.IN_WATER : 0)
                | (self.getAbilities().flying ? PartnerState.FLYING : 0) | (self.isSprinting() ? PartnerState.SPRINTING : 0)
                | (self.input != null && self.input.jumping ? PartnerState.JUMPING : 0) | (self.isSwimming() ? PartnerState.SWIMMING : 0));
        PacketByteBuf buf = PacketByteBufs.create();
        new PartnerState(self.getX(), self.getY(), self.getZ(), (float) (self.getX() - self.prevX), (float) (self.getY() - self.prevY),
                (float) (self.getZ() - self.prevZ), self.bodyYaw, flags).write(buf);
        ClientPlayNetworking.send(TogetherPackets.C2S_PARTNER_STATE, buf);
    }

    public static void respond(UUID requestId, boolean accepted) {
        PacketByteBuf buf = PacketByteBufs.create();
        TogetherPackets.writeUuid(buf, requestId); buf.writeBoolean(accepted);
        ClientPlayNetworking.send(TogetherPackets.C2S_RESPONSE, buf);
    }
    private TogetherClientPackets() {}
}
