package net.ppekkungz.crafops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CrafOpsUtils — Main server-side mod entrypoint.
 *
 * Responsibilities:
 *   1. Register server-side packet receivers (C2S)
 *   2. Hook InteractionManager into server tick loop
 *   3. Register /together commands
 *   4. Clean up all connections on server stop
 */

public class CrafOpsUtils implements ModInitializer {

    public static final String MOD_ID = "crafops_together";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CrafOps: Together] Initializing server systems...");

        TogetherPackets.registerServerReceivers();

        ServerTickEvents.END_SERVER_TICK.register(server ->
                InteractionManager.getInstance().onServerTick(server)
        );

        TogetherCommands.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[CrafOps: Together] Server stopping — clearing all connections.");
            InteractionManager.getInstance().disconnectAll(server);
        });

        LOGGER.info("[CrafOps: Together] Server systems ready.");
    }
}
