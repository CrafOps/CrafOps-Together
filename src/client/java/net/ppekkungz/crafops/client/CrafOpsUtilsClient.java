package net.ppekkungz.crafops.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.client.movement.ClientFormationController;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;
import net.ppekkungz.crafops.client.renderer.HandConnectionRenderer;

public final class CrafOpsUtilsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        TogetherClientPackets.registerClientReceivers();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(HandConnectionRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HandHoldRequestQueue.tick(client);
            if (client.world == null) return;
            // Body-only alignment keeps the two arm models parallel while leaving each player's camera/head free.
            for (var link : ClientConnectionCache.getLinks()) {
                var leader = client.world.getPlayerByUuid(link.leader());
                var follower = client.world.getPlayerByUuid(link.follower());
                if (leader != null && follower != null) {
                    follower.prevBodyYaw = leader.prevBodyYaw;
                    follower.bodyYaw = leader.bodyYaw;
                }
            }
            ClientFormationController.tick(client);
            for (var player : client.world.getPlayers()) ArmAnimationState.tick(player.getUuid(),
                    ClientConnectionCache.hasIncoming(player.getUuid()), ClientConnectionCache.hasOutgoing(player.getUuid()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConnectionCache.clear(); ArmAnimationState.clear(); HandHoldRequestQueue.clear();
        });
    }
}
