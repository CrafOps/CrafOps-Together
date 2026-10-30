package net.ppekkungz.crafops.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.interaction.PartnerState;
import net.ppekkungz.crafops.network.TogetherPayloads;

import java.util.*;

/** Client side of the protocol. Handlers run on the client main thread (NeoForge's default). */
public final class ClientPayloadHandlers {
    public static void snapshot(TogetherPayloads.Snapshot payload) {
        List<ClientConnectionCache.Link> links = new ArrayList<>(payload.links().size());
        for (TogetherPayloads.Link link : payload.links()) links.add(new ClientConnectionCache.Link(link.id(), link.leader(), link.follower()));
        ClientConnectionCache.replace(links);
        ClientConnectionCache.setFlying(new HashSet<>(payload.flying()));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) HandHoldRequestQueue.removeConnected(player.getUUID(), links);
    }

    public static void incomingRequest(TogetherPayloads.IncomingRequest payload) {
        HandHoldRequestQueue.enqueue(payload.id(), payload.sender(), payload.senderName(), payload.lifetimeMillis());
    }

    public static void settings(TogetherPayloads.Settings payload) {
        ClientConnectionCache.setSettings(payload.settings());
    }

    public static void relayedState(TogetherPayloads.RelayedState payload) {
        ClientConnectionCache.receivePartnerState(payload.player(), payload.state());
    }

    /** While holding hands, reports this tick's own movement so partners can skip the vanilla tracker delay. */
    public static void sendOwnState(Minecraft client) {
        LocalPlayer self = client.player;
        if (self == null || client.getConnection() == null || !ClientConnectionCache.isConnected(self.getUUID())
                || !client.getConnection().hasChannel(TogetherPayloads.OwnState.TYPE)) return;
        byte flags = (byte) ((self.onGround() ? PartnerState.ON_GROUND : 0) | (self.isInWater() ? PartnerState.IN_WATER : 0)
                | (self.getAbilities().flying ? PartnerState.FLYING : 0) | (self.isSprinting() ? PartnerState.SPRINTING : 0)
                | (self.input != null && self.input.jumping ? PartnerState.JUMPING : 0) | (self.isSwimming() ? PartnerState.SWIMMING : 0));
        PacketDistributor.sendToServer(new TogetherPayloads.OwnState(new PartnerState(self.getX(), self.getY(), self.getZ(),
                (float) (self.getX() - self.xo), (float) (self.getY() - self.yo), (float) (self.getZ() - self.zo), self.yBodyRot, flags)));
    }

    public static void respond(UUID requestId, boolean accepted) {
        PacketDistributor.sendToServer(new TogetherPayloads.Response(requestId, accepted));
    }

    private ClientPayloadHandlers() {}
}
