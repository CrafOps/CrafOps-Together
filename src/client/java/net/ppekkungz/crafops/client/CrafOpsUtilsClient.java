package net.ppekkungz.crafops.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientConnectionCache.clear()
        );

        LOGGER.info("[CrafOps: Together] Client systems ready.");
    }
}
