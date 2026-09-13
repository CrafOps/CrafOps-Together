package net.ppekkungz.crafops.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.network.TogetherPackets;
import java.util.*;

public final class TogetherClientPackets {
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_SNAPSHOT, (client, handler, buf, sender) -> {
            int count = Math.min(buf.readVarInt(), 63);
            List<ClientConnectionCache.Link> links = new ArrayList<>(count);
            for (int i = 0; i < count; i++) links.add(new ClientConnectionCache.Link(read(buf), read(buf), read(buf)));
            client.execute(() -> ClientConnectionCache.replace(links));
        });
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_SETTINGS, (client, handler, buf, sender) -> {
            boolean enabled = buf.readBoolean(); double distance = buf.readDouble();
            double side = buf.readDouble(), behind = buf.readDouble(), tolerance = buf.readDouble();
            double spring = buf.readDouble(), acceleration = buf.readDouble(), maxSpeed = buf.readDouble();
            double velocityMatch = buf.readDouble(), stepAssist = buf.readDouble();
            client.execute(() -> ClientConnectionCache.setSettings(enabled, distance, side, behind, tolerance,
                    spring, acceleration, maxSpeed, velocityMatch, stepAssist));
        });
        ClientPlayNetworking.registerGlobalReceiver(TogetherPackets.S2C_REQUEST, (client, handler, buf, sender) -> {
            UUID id = read(buf), from = read(buf); String name = buf.readString(64); long expires = buf.readLong();
            client.execute(() -> HandHoldRequestQueue.enqueue(new HandHoldRequestQueue.Request(id, from, name, expires)));
        });
    }
    public static void respond(UUID requestId, boolean accepted) {
        PacketByteBuf buf = PacketByteBufs.create();
        write(buf, requestId); buf.writeBoolean(accepted);
        ClientPlayNetworking.send(TogetherPackets.C2S_RESPONSE, buf);
    }
    private static UUID read(PacketByteBuf buf) { return new UUID(buf.readLong(), buf.readLong()); }
    private static void write(PacketByteBuf buf, UUID id) { buf.writeLong(id.getMostSignificantBits()); buf.writeLong(id.getLeastSignificantBits()); }
    private TogetherClientPackets() {}
}
