package net.ppekkungz.crafops.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;
import net.ppekkungz.crafops.client.renderer.HandConnectionRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrafOpsUtilsClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("crafops_together_client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[CrafOps: Together] Initializing client systems...");

        TogetherClientPackets.registerClientReceivers();

        WorldRenderEvents.AFTER_TRANSLUCENT.register(HandConnectionRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            for (var player : client.world.getPlayers()) {
                boolean connected = ClientConnectionCache.isConnected(player.getUuid());
                ArmAnimationState.tick(player.getUuid(), connected);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConnectionCache.clear();
            ArmAnimationState.clear();
        });

        LOGGER.info("[CrafOps: Together] Client systems ready.");
    }
}
