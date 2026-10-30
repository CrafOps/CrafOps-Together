package net.ppekkungz.crafops.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.client.movement.FollowerController;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CrafOpsUtilsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        TogetherClientPackets.registerClientReceivers();
        HandHoldRequestQueue.registerKeys();
        HudRenderCallback.EVENT.register(HandHoldRequestQueue::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HandHoldRequestQueue.tick(client);
            if (client.world == null) return;
            ClientConnectionCache.tickMotion(client.world);
            FollowerController.tickAfterMovement(client);
            TogetherClientPackets.sendOwnState(client);
            Set<UUID> present = new HashSet<>();
            for (var player : client.world.getPlayers()) {
                UUID id = player.getUuid();
                present.add(id);
                ArmAnimationState.tick(id, ClientConnectionCache.hasIncoming(id), ClientConnectionCache.hasOutgoing(id));
            }
            ArmAnimationState.retain(present);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConnectionCache.clear(); ArmAnimationState.clear(); HandHoldRequestQueue.clear();
        });
    }
}
